package com.liorshaya.policypilot.ai.adapter;

import com.liorshaya.policypilot.ai.ChatTool;
import com.liorshaya.policypilot.ai.Completion;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.cache.ProposalCache;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The only class that talks to a provider (Document 2, AI Layer Design; NFR-4). One call goes: the response
 * cache, then the daily token budget, then the provider with this prompt's own timeout, and then the ledger —
 * whatever the answer was. A provider that does not answer becomes a named failure, never a made-up rule.
 *
 * <p>The schema a prompt names is sent in the provider's strict structured-output mode, derived from the
 * canonical schema by {@link ProviderSchemaVariant}; the answer is still validated locally, so the provider's
 * mode is a convenience, not the guarantee.
 */
@Component
public class SpringAiLlmGateway implements LlmGateway {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Document 4, Guardrails: 3 attempts with 1 s, 2 s and 4 s between them. */
    private static final int ATTEMPTS = 3;
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);

    private final ChatModel chat;
    private final PolicyPilotProperties properties;
    private final ProposalCache cache;
    private final TokenBudgetGuard budget;
    private final ModelCallLedger ledger;
    private final CircuitBreaker breaker;
    private final Clock clock;
    private final MeterRegistry meters;

    public SpringAiLlmGateway(ChatModel chat, PolicyPilotProperties properties, ProposalCache cache,
            TokenBudgetGuard budget, ModelCallLedger ledger, Clock clock, MeterRegistry meters) {
        this.chat = chat;
        this.properties = properties;
        this.cache = cache;
        this.budget = budget;
        this.ledger = ledger;
        this.clock = clock;
        this.meters = meters;
        this.breaker = new CircuitBreaker(clock);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Completion<T> complete(PromptSpec spec, Class<T> type) {
        if (type != String.class) {
            throw new IllegalArgumentException("the gateway answers text; the use case parses and validates it");
        }
        String model = modelFor(spec.role());
        String key = ProposalCache.keyOf(spec, model);
        Optional<String> cached = cache.find(key);
        if (cached.isPresent()) {
            ledger.record(spec, model, provider(), TokenUsage.NONE, Duration.ZERO,
                    ModelCallLedger.Results.VALID, true);
            meters.counter("policypilot.ai.cache.hit", "prompt", spec.promptName()).increment();
            return (Completion<T>) Completion.fromCache(cached.get());
        }

        budget.requireBudget();
        breaker.requireClosed();
        long started = clock.millis();
        ChatResponse response;
        try {
            response = call(spec, model);
        } catch (LlmUnavailableException e) {
            Duration latency = Duration.ofMillis(clock.millis() - started);
            ledger.record(spec, model, provider(), TokenUsage.NONE, latency,
                    ModelCallLedger.Results.UNAVAILABLE, false);
            breaker.failed();
            meters.counter("policypilot.ai.provider.failure", "reason", e.reason().name()).increment();
            throw e;
        }

        Duration latency = Duration.ofMillis(clock.millis() - started);
        TokenUsage usage = usageOf(response);
        // the tokens were spent whether or not they became an answer, so the budget is charged either way
        budget.record(usage);
        breaker.succeeded();
        String answer = response.getResult().getOutput().getText();
        if (answer == null || answer.isBlank()) {
            // The model's reasoning and its text share one budget, so a call can end with the budget spent and
            // nothing written. The provider answered; the answer was cut off. It must not reach the repair
            // loop, because repairing nothing yields a small, wrong document that looks like a good one
            // (Document 4, Guardrails), and it is not cached, or the failure would outlive the call.
            ledger.record(spec, model, provider(), usage, latency, ModelCallLedger.Results.TRUNCATED, false);
            meters.counter("policypilot.ai.provider.failure",
                    "reason", LlmUnavailableException.Reason.OUTPUT_TRUNCATED.name()).increment();
            throw new LlmUnavailableException(LlmUnavailableException.Reason.OUTPUT_TRUNCATED,
                    "the provider returned no text for " + spec.promptName() + "/" + spec.promptVersion()
                            + "; the answer did not fit in " + spec.maxOutputTokens() + " tokens");
        }
        ledger.record(spec, model, provider(), usage, latency, ModelCallLedger.Results.VALID, false);
        cache.put(key, spec.promptName(), answer);
        return (Completion<T>) Completion.fromProvider(answer, usage);
    }

    /**
     * One streamed answer (Document 4, Prompt 4): the budget and the breaker first, as for every call, then the
     * provider's stream with the tools registered as callbacks, the first piece within the chat's first-token deadline
     * and the whole within the prompt's timeout, and the ledger whatever happened. A stream is not retried: tokens
     * already shown cannot be taken back.
     */
    @Override
    public TokenUsage stream(PromptSpec spec, List<ChatTool> tools, Consumer<String> tokens) {
        String model = modelFor(spec.role());
        budget.requireBudget();
        breaker.requireClosed();
        long started = clock.millis();
        Streamed streamed = new Streamed(tokens);
        Duration firstToken = Duration.ofSeconds(properties.ai().timeouts().chatFirstTokenSeconds());
        try {
            chat.stream(new Prompt(List.of(new SystemMessage(spec.system()), new UserMessage(spec.user())),
                            streamingOptionsFor(spec, model, tools)))
                    .timeout(Mono.delay(firstToken), ignored -> Mono.never())
                    .doOnNext(streamed::take)
                    .blockLast(spec.timeout());
        } catch (RuntimeException e) {
            if (streamed.stoppedByCaller != null) {
                // the caller stopped reading (a denylist hit, a closed connection); the provider did nothing wrong
                ledger.record(spec, model, provider(), streamed.usage, Duration.ofMillis(clock.millis() - started),
                        ModelCallLedger.Results.VALID, false);
                throw streamed.stoppedByCaller;
            }
            LlmUnavailableException failure = streamFailure(e);
            ledger.record(spec, model, provider(), streamed.usage, Duration.ofMillis(clock.millis() - started),
                    ModelCallLedger.Results.UNAVAILABLE, false);
            breaker.failed();
            meters.counter("policypilot.ai.provider.failure", "reason", failure.reason().name()).increment();
            throw failure;
        }
        Duration latency = Duration.ofMillis(clock.millis() - started);
        budget.record(streamed.usage);
        breaker.succeeded();
        if ("LENGTH".equalsIgnoreCase(streamed.finishReason)) {
            ledger.record(spec, model, provider(), streamed.usage, latency, ModelCallLedger.Results.TRUNCATED, false);
            meters.counter("policypilot.ai.provider.failure",
                    "reason", LlmUnavailableException.Reason.OUTPUT_TRUNCATED.name()).increment();
            throw new LlmUnavailableException(LlmUnavailableException.Reason.OUTPUT_TRUNCATED,
                    "the answer did not fit in " + spec.maxOutputTokens() + " tokens");
        }
        ledger.record(spec, model, provider(), streamed.usage, latency, ModelCallLedger.Results.VALID, false);
        return streamed.usage;
    }

    private static LlmUnavailableException streamFailure(RuntimeException e) {
        Throwable cause = Exceptions.unwrap(e);
        boolean late = cause instanceof TimeoutException
                || String.valueOf(e.getMessage()).startsWith("Timeout on blocking read");
        if (late) {
            return new LlmUnavailableException(LlmUnavailableException.Reason.TIMEOUT, "the answer missed its deadline",
                    e);
        }
        return new LlmUnavailableException(reasonOf(e), "the provider did not answer", e);
    }

    /** The streaming options: the chat's model and cap, the prompt's temperature if it names one, and the tools. */
    private ChatOptions streamingOptionsFor(PromptSpec spec, String model, List<ChatTool> tools) {
        List<ToolCallback> callbacks = tools.stream().map(SpringAiLlmGateway::callbackOf).toList();
        if (chat instanceof OpenAiChatModel) {
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                    .timeout(spec.timeout())
                    .maxRetries(0)
                    .model(model)
                    .maxCompletionTokens(spec.maxOutputTokens())
                    // the usage arrives with the last chunk only when the request asks for it
                    .streamUsage(true)
                    .toolCallbacks(callbacks);
            if (spec.temperature() != null) {
                options.temperature(spec.temperature());
            }
            return options.build();
        }
        ToolCallingChatOptions.Builder<?> options = ToolCallingChatOptions.builder()
                .model(model)
                .maxTokens(spec.maxOutputTokens())
                .toolCallbacks(callbacks);
        if (spec.temperature() != null) {
            options.temperature(spec.temperature());
        }
        return options.build();
    }

    /** A {@link ChatTool} as Spring AI calls it: the same name, description and schema, the same call. */
    static ToolCallback callbackOf(ChatTool tool) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return tool.call(toolInput);
            }
        };
    }

    /** What a stream has delivered so far: its usage, why it stopped, and whether the caller stopped it. */
    private static final class Streamed {

        private final Consumer<String> tokens;
        private TokenUsage usage = TokenUsage.NONE;
        private @Nullable String finishReason;
        private @Nullable RuntimeException stoppedByCaller;

        Streamed(Consumer<String> tokens) {
            this.tokens = tokens;
        }

        void take(ChatResponse response) {
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null
                    && response.getMetadata().getUsage().getPromptTokens() != null
                    && response.getMetadata().getUsage().getPromptTokens() > 0) {
                usage = usageOf(response);
            }
            if (response.getResult() == null) {
                return;
            }
            var metadata = response.getResult().getMetadata();
            if (metadata != null && metadata.getFinishReason() != null) {
                finishReason = metadata.getFinishReason();
            }
            String text = response.getResult().getOutput().getText();
            if (text == null || text.isEmpty()) {
                return;
            }
            try {
                tokens.accept(text);
            } catch (RuntimeException e) {
                stoppedByCaller = e;
                throw e;
            }
        }
    }

    /** The call itself, retried with backoff on a failure that may pass (Document 4, Guardrails). */
    private ChatResponse call(PromptSpec spec, String model) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return chat.call(new Prompt(
                        List.of(new SystemMessage(spec.system()), new UserMessage(spec.user())),
                        optionsFor(spec, model)));
            } catch (RuntimeException e) {
                last = e;
                if (attempt == ATTEMPTS || !worthRetrying(e)) {
                    break;
                }
                sleep(FIRST_BACKOFF.multipliedBy(1L << (attempt - 1)));
            }
        }
        throw new LlmUnavailableException(reasonOf(last), "the provider did not answer", last);
    }

    private ChatOptions optionsFor(PromptSpec spec, String model) {
        if (chat instanceof OpenAiChatModel && spec.outputSchema() != null) {
            // the current lineup takes max_completion_tokens, not the older max_tokens, and a prompt that asks
            // for the model's own temperature sends none at all
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                    // the prompt's own timeout, and no retries of the client's own: the gateway retries
                    .timeout(spec.timeout())
                    .maxRetries(0)
                    .model(model)
                    .maxCompletionTokens(spec.maxOutputTokens())
                    .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                            .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                            .jsonSchema(variantOf(spec.outputSchema()))
                            // strict mode makes every declared property mandatory, and the DSL forbids some of
                            // them in context (a derived field has no default), so the model would have to write
                            // a value the validator then refuses; the schema still guides it and the canonical
                            // validator with the repair loop is what actually holds (Document 4, Output discipline)
                            .strict(false)
                            .build());
            if (spec.temperature() != null) {
                options.temperature(spec.temperature());
            }
            return options.build();
        }
        ChatOptions.Builder<?> options = ChatOptions.builder()
                .model(model)
                .maxTokens(spec.maxOutputTokens());
        if (spec.temperature() != null) {
            options.temperature(spec.temperature());
        }
        return options.build();
    }

    /** The provider's variant of the canonical schema the prompt names. */
    static String variantOf(String location) {
        try (InputStream stream = SpringAiLlmGateway.class.getClassLoader().getResourceAsStream(location)) {
            if (stream == null) {
                throw new IllegalStateException("no schema at " + location);
            }
            ObjectNode canonical = (ObjectNode) JSON.readTree(stream.readAllBytes());
            return ProviderSchemaVariant.of(canonical).toString();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + location, e);
        }
    }

    private String modelFor(ModelRole role) {
        return role == ModelRole.STRONG ? properties.ai().models().strong() : properties.ai().models().fast();
    }

    /** The provider's own name, taken from the model implementation: OpenAiChatModel is openai, and so on. */
    private String provider() {
        String name = chat.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.endsWith("chatmodel") ? name.substring(0, name.length() - "chatmodel".length()) : name;
    }

    private static TokenUsage usageOf(ChatResponse response) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return TokenUsage.NONE;
        }
        var usage = response.getMetadata().getUsage();
        return new TokenUsage(
                usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
    }

    /** A rate limit or a server error may pass on the next attempt; anything else will not. */
    static boolean worthRetrying(RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        return message.contains("429") || message.contains("500") || message.contains("502")
                || message.contains("503") || message.contains("504") || message.contains("timeout")
                || message.contains("Timeout");
    }

    static LlmUnavailableException.Reason reasonOf(@Nullable RuntimeException e) {
        String message = e == null ? "" : String.valueOf(e.getMessage());
        if (message.contains("429")) {
            return LlmUnavailableException.Reason.RATE_LIMITED;
        }
        if (message.contains("timeout") || message.contains("Timeout")) {
            return LlmUnavailableException.Reason.TIMEOUT;
        }
        return LlmUnavailableException.Reason.PROVIDER_ERROR;
    }

    private static void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException(LlmUnavailableException.Reason.PROVIDER_ERROR, "interrupted", e);
        }
    }

    /**
     * Fails fast for 30 seconds after 5 consecutive failures (Document 4, Guardrails), so a provider outage
     * becomes an immediate honest error instead of a queue of timeouts.
     */
    static final class CircuitBreaker {

        private static final int FAILURES_TO_OPEN = 5;
        private static final Duration OPEN_FOR = Duration.ofSeconds(30);

        private final Clock clock;
        private int consecutiveFailures;
        private long openedAt;

        CircuitBreaker(Clock clock) {
            this.clock = clock;
        }

        synchronized void requireClosed() {
            if (consecutiveFailures >= FAILURES_TO_OPEN && clock.millis() - openedAt < OPEN_FOR.toMillis()) {
                throw new LlmUnavailableException(LlmUnavailableException.Reason.PROVIDER_ERROR,
                        "the provider failed " + consecutiveFailures + " times in a row; not calling it for now");
            }
        }

        synchronized void succeeded() {
            consecutiveFailures = 0;
        }

        synchronized void failed() {
            consecutiveFailures++;
            if (consecutiveFailures == FAILURES_TO_OPEN) {
                openedAt = clock.millis();
            }
        }
    }
}
