package com.liorshaya.policypilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * The value types the AI layer passes around (Document 2, AI Layer Design): what a call asked for, what it cost
 * and what came back. They carry the little arithmetic the ledger and the budget guard depend on.
 */
class AiValuesTest {

    private static PromptSpec spec(int attempt) {
        return new PromptSpec("author", "v1", ModelRole.STRONG, "system", "user", "schema.json", 0.0, 8000,
                Duration.ofSeconds(60), attempt);
    }

    @Test
    void aUsageIsTheTokensOfBothDirections() {
        assertThat(new TokenUsage(12_000, 6_000).total()).isEqualTo(18_000);
        assertThat(TokenUsage.NONE.total()).isZero();
    }

    @Test
    void aUsageCannotBeNegative() {
        assertThatThrownBy(() -> new TokenUsage(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenUsage(0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAnswerFromTheProviderCarriesItsCostAndAnAnswerFromTheCacheCarriesNone() {
        Completion<String> fresh = Completion.fromProvider("{}", new TokenUsage(10, 5));
        Completion<String> cached = Completion.fromCache("{}");

        assertThat(fresh.usage().total()).isEqualTo(15);
        assertThat(fresh.cacheHit()).isFalse();
        assertThat(cached.usage()).isEqualTo(TokenUsage.NONE);
        assertThat(cached.cacheHit()).isTrue();
        assertThat(cached.value()).isEqualTo("{}");
    }

    @Test
    void anAttemptIsNumberedFromOne() {
        assertThat(spec(1).attempt()).isEqualTo(1);
        assertThatThrownBy(() -> spec(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRepairIsTheSameCallWithTheNextNumberAndTheRepairText() {
        PromptSpec repaired = spec(1).repairedWith("fix the quote of R-100");

        assertThat(repaired.attempt()).isEqualTo(2);
        assertThat(repaired.user()).isEqualTo("fix the quote of R-100");
        // Document 4: the repair reuses the system prompt, the schema and the budget of the prompt it repairs
        assertThat(repaired.system()).isEqualTo("system");
        assertThat(repaired.outputSchema()).isEqualTo("schema.json");
        assertThat(repaired.promptName()).isEqualTo("author");
        assertThat(repaired.promptVersion()).isEqualTo("v1");
        assertThat(repaired.role()).isEqualTo(ModelRole.STRONG);
        assertThat(repaired.temperature()).isEqualTo(0.0);
        assertThat(repaired.maxOutputTokens()).isEqualTo(8000);
        assertThat(repaired.timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(repaired.repairedWith("again").attempt()).isEqualTo(3);
    }

    @Test
    void aProviderFailureSaysWhatStoppedIt() {
        LlmUnavailableException timeout =
                new LlmUnavailableException(LlmUnavailableException.Reason.TIMEOUT, "took too long");
        LlmUnavailableException refused = new LlmUnavailableException(
                LlmUnavailableException.Reason.RATE_LIMITED, "429", new IllegalStateException("from the provider"));

        assertThat(timeout.reason()).isEqualTo(LlmUnavailableException.Reason.TIMEOUT);
        assertThat(timeout.getMessage()).isEqualTo("took too long");
        assertThat(refused.reason()).isEqualTo(LlmUnavailableException.Reason.RATE_LIMITED);
        assertThat(refused.getCause()).hasMessage("from the provider");
    }

    @Test
    void aMalformedAnswerKeepsWhatTheProviderActuallySent() {
        LlmMalformedOutputException malformed =
                new LlmMalformedOutputException("not JSON", "I cannot help with that", new IllegalStateException());

        assertThat(malformed.raw()).isEqualTo("I cannot help with that");
        assertThat(malformed.getMessage()).isEqualTo("not JSON");
    }
}
