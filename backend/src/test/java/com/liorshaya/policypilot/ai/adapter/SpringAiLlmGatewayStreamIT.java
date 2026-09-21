package com.liorshaya.policypilot.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.ai.ChatTool;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.entity.ModelCallEntity;
import com.liorshaya.policypilot.ai.repository.ModelCallRepository;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;

/**
 * The streaming half of the adapter (Document 2, AI Layer Design: {@code LlmGateway} streams chat with tools
 * registered; Document 4, Prompt 4). The provider is a fake {@link ChatModel} whose stream the test writes, and it
 * runs the tool callbacks the adapter registered the way Spring AI's internal tool execution does; the ledger, the
 * budget and the deadlines around the call are real.
 */
@TestPropertySource(properties = {"policypilot.ai.daily-token-budget=100000",
        "policypilot.ai.timeouts.chat-first-token-seconds=1"})
@Import(SpringAiLlmGatewayStreamIT.FakeProvider.class)
class SpringAiLlmGatewayStreamIT extends ApiIntegrationTest {

    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired
    private LlmGateway gateway;

    @Autowired
    private StreamingModel model;

    @Autowired
    private ModelCallRepository calls;

    private String version;

    @BeforeEach
    void freshPromptVersion() {
        version = "stream" + RUN.incrementAndGet();
        model.script(prompt -> Flux.empty());
    }

    // Document 4: "tokens stream as they arrive"; Document 4, Logging: every call is recorded. Expected: the three
    // pieces in order, the usage of the last chunk (120 in, 40 out), and one VALID row in the ledger
    @Test
    void tokensArriveInOrderAndTheCallIsRecorded() {
        model.script(prompt -> Flux.just(chunk("The term "), chunk("is 84 "), last("months.", "STOP")));
        List<String> tokens = new ArrayList<>();

        TokenUsage usage = gateway.stream(spec(), List.of(), tokens::add);

        assertThat(tokens).containsExactly("The term ", "is 84 ", "months.");
        assertThat(usage).isEqualTo(new TokenUsage(120, 40));
        List<ModelCallEntity> recorded = calls.findByPromptNameAndPromptVersionOrderByAtAsc("answer", version);
        assertThat(recorded).extracting(ModelCallEntity::validationResult).containsExactly("VALID");
        assertThat(recorded.getFirst().model()).isEqualTo("gpt-5.6-luna");
    }

    // Document 2: the tools are ChatTool objects the adapter registers with Spring AI. Expected: the provider sees a
    // callback named after the tool with its schema, calling it runs the tool with the arguments as sent, and the
    // model reads what the tool returned
    @Test
    void theModelCallsTheChatToolsThroughTheCallbacksTheAdapterRegistered() {
        List<String> received = new ArrayList<>();
        ChatTool tool = tool("getDecision", arguments -> {
            received.add(arguments);
            return "{\"outcome\":\"refer\"}";
        });
        model.script(prompt -> {
            ToolCallback callback = callbacks(prompt).getFirst();
            String result = callback.call("{\"applicationNumber\":17}");
            return Flux.just(last(callback.getToolDefinition().name() + " said " + result, "STOP"));
        });
        List<String> tokens = new ArrayList<>();

        gateway.stream(spec(), List.of(tool), tokens::add);

        assertThat(received).containsExactly("{\"applicationNumber\":17}");
        assertThat(tokens).containsExactly("getDecision said {\"outcome\":\"refer\"}");
        assertThat(callbacks(model.lastPrompt()).getFirst().getToolDefinition().inputSchema())
                .isEqualTo("{\"type\":\"object\"}");
    }

    // Document 4, Guardrails: 20 s to the first token (1 s here), well before the prompt's 60 s. Expected: a provider
    // that never answers is a TIMEOUT within seconds, and is recorded as UNAVAILABLE
    @Test
    void aFirstTokenThatNeverComesIsATimeout() {
        model.script(prompt -> Flux.never());
        long started = System.nanoTime();

        assertThatThrownBy(() -> gateway.stream(spec(), List.of(), token -> { }))
                .isInstanceOf(LlmUnavailableException.class)
                .extracting(e -> ((LlmUnavailableException) e).reason())
                .isEqualTo(LlmUnavailableException.Reason.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
        assertThat(calls.findByPromptNameAndPromptVersionOrderByAtAsc("answer", version))
                .extracting(ModelCallEntity::validationResult).containsExactly("UNAVAILABLE");
    }

    // Document 4, Guardrails: a provider failure is a defined failure. Expected: PROVIDER_ERROR, recorded
    @Test
    void aProviderFailureIsAProviderError() {
        model.script(prompt -> Flux.error(new IllegalStateException("500 from the provider")));

        assertThatThrownBy(() -> gateway.stream(spec(), List.of(), token -> { }))
                .isInstanceOf(LlmUnavailableException.class)
                .extracting(e -> ((LlmUnavailableException) e).reason())
                .isEqualTo(LlmUnavailableException.Reason.PROVIDER_ERROR);
    }

    // Document 4, Guardrails: an answer the provider stopped on its output cap is the call failing. Expected:
    // OUTPUT_TRUNCATED, recorded as TRUNCATED, after the tokens that did arrive
    @Test
    void anAnswerStoppedByTheCapIsTruncated() {
        model.script(prompt -> Flux.just(chunk("The term "), last("is", "LENGTH")));
        List<String> tokens = new ArrayList<>();

        assertThatThrownBy(() -> gateway.stream(spec(), List.of(), tokens::add))
                .isInstanceOf(LlmUnavailableException.class)
                .extracting(e -> ((LlmUnavailableException) e).reason())
                .isEqualTo(LlmUnavailableException.Reason.OUTPUT_TRUNCATED);
        assertThat(tokens).containsExactly("The term ", "is");
        assertThat(calls.findByPromptNameAndPromptVersionOrderByAtAsc("answer", version))
                .extracting(ModelCallEntity::validationResult).containsExactly("TRUNCATED");
    }

    private PromptSpec spec() {
        return new PromptSpec("answer", version, ModelRole.FAST, "system", "user " + version, null, null, 1200,
                Duration.ofSeconds(60), 1);
    }

    private static ChatTool tool(String name, Function<String, String> body) {
        return new ChatTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "a test tool";
            }

            @Override
            public String inputSchema() {
                return "{\"type\":\"object\"}";
            }

            @Override
            public String call(String argumentsJson) {
                return body.apply(argumentsJson);
            }
        };
    }

    private static List<ToolCallback> callbacks(Prompt prompt) {
        return ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks();
    }

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse last(String text, String finishReason) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text),
                        ChatGenerationMetadata.builder().finishReason(finishReason).build())),
                ChatResponseMetadata.builder().usage(new DefaultUsage(120, 40)).build());
    }

    /** A provider whose stream the test writes; it keeps the last prompt so a test can look at what was sent. */
    static final class StreamingModel implements ChatModel {

        private volatile Function<Prompt, Flux<ChatResponse>> script = prompt -> Flux.empty();
        private volatile Prompt lastPrompt;

        void script(Function<Prompt, Flux<ChatResponse>> script) {
            this.script = script;
        }

        Prompt lastPrompt() {
            return lastPrompt;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException("the chat streams");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            lastPrompt = prompt;
            return Flux.defer(() -> script.apply(prompt));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProvider {

        @Bean
        @Primary
        StreamingModel streamingModel() {
            return new StreamingModel();
        }
    }
}
