package com.liorshaya.policypilot.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.ai.Completion;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.entity.ModelCallEntity;
import com.liorshaya.policypilot.ai.repository.ModelCallRepository;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * The provider adapter with a fake {@link ChatModel} in the provider's place (Document 6: a model call is one of
 * the few things a test may fake). Everything around the call is real: the response cache, the daily budget, the
 * ledger and the circuit breaker, against the database.
 */
@TestPropertySource(properties = "policypilot.ai.daily-token-budget=1000")
@Import(SpringAiLlmGatewayIT.FakeProvider.class)
@Isolated
class SpringAiLlmGatewayIT extends ApiIntegrationTest {

    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired
    private LlmGateway gateway;

    @Autowired
    private FakeChatModel model;

    @Autowired
    private ModelCallRepository calls;

    private String version;

    @BeforeEach
    void freshDayAndPrompt() {
        // a day of its own, far from the days AiBookkeepingIT uses: the ledger is one shared table
        clock.set(START.plus(Duration.ofDays(100 + RUN.incrementAndGet())));
        version = "v" + RUN.get();
        model.reset();
    }

    private PromptSpec spec() {
        return new PromptSpec("author", version, ModelRole.STRONG, "system " + version, "user " + version,
                null, 0.0, 8000, Duration.ofSeconds(60), 1);
    }

    private List<ModelCallEntity> recorded() {
        return calls.findByPromptNameAndPromptVersionOrderByAtAsc("author", version);
    }

    @Test
    void answersFromTheProviderAndRecordsWhatItCost() {
        model.willAnswer("{\"id\":\"consumer-lending\"}");

        Completion<String> completion = gateway.complete(spec(), String.class);

        assertThat(completion.value()).isEqualTo("{\"id\":\"consumer-lending\"}");
        assertThat(completion.cacheHit()).isFalse();
        assertThat(completion.usage().inputTokens()).isEqualTo(120);
        assertThat(completion.usage().outputTokens()).isEqualTo(40);
        ModelCallEntity call = recorded().getFirst();
        assertThat(call.validationResult()).isEqualTo("VALID");
        assertThat(call.cacheHit()).isFalse();
        assertThat(call.model()).isEqualTo("gpt-5.6-terra");
        // the provider is named after the model in use; here that is the fake one
        assertThat(call.provider()).isEqualTo("fake");
    }

    @Test
    void anAnswerTheProviderCutOffIsADefinedFailure() {
        // the strong model's reasoning and its text share one budget, so a hard policy can spend the whole cap
        // on reasoning and return nothing (Document 4, Guardrails: answer cut off by the output cap)
        model.willAnswer("");

        assertThatThrownBy(() -> gateway.complete(spec(), String.class))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("no text");
        assertThat(recorded().getFirst().validationResult()).isEqualTo("TRUNCATED");
    }

    @Test
    void anAnswerWithNoTextIsNeverCached() {
        // caching it would make the failure permanent for that policy: every later run would replay the nothing
        model.willAnswer("");
        assertThatThrownBy(() -> gateway.complete(spec(), String.class))
                .isInstanceOf(LlmUnavailableException.class);

        model.willAnswer("{\"id\":\"consumer-lending\"}");
        Completion<String> second = gateway.complete(spec(), String.class);

        assertThat(second.value()).isEqualTo("{\"id\":\"consumer-lending\"}");
        assertThat(second.cacheHit()).isFalse();
    }

    @Test
    void theSameCallASecondTimeIsServedFromTheCache() {
        model.willAnswer("{\"id\":\"consumer-lending\"}");
        gateway.complete(spec(), String.class);

        Completion<String> again = gateway.complete(spec(), String.class);

        assertThat(again.cacheHit()).isTrue();
        assertThat(again.value()).isEqualTo("{\"id\":\"consumer-lending\"}");
        assertThat(again.usage().total()).isZero();
        // the provider was asked once; the second row is the cache hit
        assertThat(model.calls()).isEqualTo(1);
        assertThat(recorded()).hasSize(2);
        assertThat(recorded().get(1).cacheHit()).isTrue();
    }

    // Document 2, Flow 1: a review run again reads the draft again, and a refused answer is never served twice.
    // Expected: after forget, the same call reaches the provider a second time and answers what it says now
    @Test
    void aForgottenAnswerIsAskedOfTheProviderAgain() {
        model.willAnswer("{\"findings\": \"first\"}");
        model.willAnswer("{\"findings\": []}");
        gateway.complete(spec(), String.class);

        gateway.forget(spec());
        Completion<String> again = gateway.complete(spec(), String.class);

        assertThat(again.cacheHit()).isFalse();
        assertThat(again.value()).isEqualTo("{\"findings\": []}");
        assertThat(model.calls()).isEqualTo(2);
        // forgetting what was never cached is not an error
        gateway.forget(spec());
    }

    @Test
    void aRateLimitIsRetriedAndThenSucceeds() {
        model.willFail("429 Too Many Requests");
        model.willAnswer("{\"id\":\"consumer-lending\"}");

        Completion<String> completion = gateway.complete(spec(), String.class);

        assertThat(completion.value()).isEqualTo("{\"id\":\"consumer-lending\"}");
        assertThat(model.calls()).isEqualTo(2);
        assertThat(recorded()).hasSize(1);
    }

    @Test
    void aProviderThatKeepsFailingIsADefinedFailure() {
        model.willFail("429 Too Many Requests");
        model.willFail("429 Too Many Requests");
        model.willFail("429 Too Many Requests");

        assertThatThrownBy(() -> gateway.complete(spec(), String.class))
                .isInstanceOf(LlmUnavailableException.class)
                .extracting(thrown -> ((LlmUnavailableException) thrown).reason())
                .isEqualTo(LlmUnavailableException.Reason.RATE_LIMITED);
        assertThat(model.calls()).isEqualTo(3);
        assertThat(recorded().getFirst().validationResult()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void aFailureThatWillNotPassIsNotRetried() {
        model.willFail("400 the request was malformed");

        assertThatThrownBy(() -> gateway.complete(spec(), String.class))
                .isInstanceOf(LlmUnavailableException.class)
                .extracting(thrown -> ((LlmUnavailableException) thrown).reason())
                .isEqualTo(LlmUnavailableException.Reason.PROVIDER_ERROR);
        assertThat(model.calls()).isEqualTo(1);
    }

    @Test
    void aSpentBudgetRefusesBeforeTheProviderIsCalled() {
        model.willAnswer("{}");
        // 1,000 tokens is the budget here; one answer of 160 tokens is far from it, so the ledger is filled first
        for (int i = 0; i < 7; i++) {
            PromptSpec spec = new PromptSpec("author", version, ModelRole.STRONG, "system " + i, "user " + i,
                    null, 0.0, 8000, Duration.ofSeconds(60), 1);
            model.willAnswer("{}");
            gateway.complete(spec, String.class);
        }

        assertThatThrownBy(() -> gateway.complete(spec(), String.class))
                .isInstanceOf(LlmUnavailableException.class)
                .extracting(thrown -> ((LlmUnavailableException) thrown).reason())
                .isEqualTo(LlmUnavailableException.Reason.BUDGET_EXHAUSTED);
    }

    @Test
    void theSchemaSentToTheProviderIsTheStrictVariantOfTheCanonicalOne() {
        String variant = SpringAiLlmGateway.variantOf("schemas/ruleset-1.0.schema.json");

        assertThat(variant).contains("\"additionalProperties\":false");
        assertThat(variant).doesNotContain("\"maxLength\"").doesNotContain("\"pattern\"");
    }

    @Test
    void aRetryableFailureIsToldApartFromOneThatIsNot() {
        assertThat(SpringAiLlmGateway.worthRetrying(new IllegalStateException("429 Too Many Requests"))).isTrue();
        assertThat(SpringAiLlmGateway.worthRetrying(new IllegalStateException("503 Service Unavailable"))).isTrue();
        assertThat(SpringAiLlmGateway.worthRetrying(new IllegalStateException("401 Unauthorized"))).isFalse();
        assertThat(SpringAiLlmGateway.reasonOf(new IllegalStateException("read timeout")))
                .isEqualTo(LlmUnavailableException.Reason.TIMEOUT);
        assertThat(SpringAiLlmGateway.reasonOf(null))
                .isEqualTo(LlmUnavailableException.Reason.PROVIDER_ERROR);
    }

    @Test
    void theCircuitOpensAfterFiveFailuresInARow() {
        SpringAiLlmGateway.CircuitBreaker breaker = new SpringAiLlmGateway.CircuitBreaker(clock);

        for (int i = 0; i < 4; i++) {
            breaker.failed();
        }
        breaker.requireClosed();
        breaker.failed();

        assertThatThrownBy(breaker::requireClosed)
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("failed 5 times in a row");
        // Document 4: it fails fast for 30 seconds, then lets one call through again
        clock.advance(Duration.ofSeconds(31));
        breaker.requireClosed();
        breaker.succeeded();
        breaker.failed();
        breaker.requireClosed();
    }

    /** A provider that answers what a test told it to, without a network (Document 6, AI Layer Testing). */
    static final class FakeChatModel implements ChatModel {

        private final Deque<Object> answers = new ArrayDeque<>();
        private int calls;

        void willAnswer(String text) {
            answers.add(text);
        }

        void willFail(String message) {
            answers.add(new IllegalStateException(message));
        }

        void reset() {
            answers.clear();
            calls = 0;
        }

        int calls() {
            return calls;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            calls++;
            Object answer = answers.isEmpty() ? "{}" : answers.removeFirst();
            if (answer instanceof RuntimeException failure) {
                throw failure;
            }
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage((String) answer))),
                    ChatResponseMetadata.builder().usage(new DefaultUsage(120, 40)).build());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProvider {

        @Bean
        @Primary
        FakeChatModel fakeChatModel() {
            return new FakeChatModel();
        }
    }
}
