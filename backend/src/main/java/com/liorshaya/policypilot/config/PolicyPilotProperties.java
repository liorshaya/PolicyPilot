package com.liorshaya.policypilot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application properties owned by PolicyPilot (Document 2, Configuration and Model Providers).
 *
 * <p>Every value has a default in {@code application.yml} except the three secrets, which come from environment
 * variables only, and {@code ai.daily-token-budget}, for which the documents fix no number yet (the token budget
 * guard of day 7 must refuse to start in the cloud profile while it is missing).
 */
@Validated
@ConfigurationProperties(prefix = "policypilot")
public record PolicyPilotProperties(
        String accessCode,
        String cookieSecret,
        String adminCode,
        @NotNull @Valid RateLimit rateLimit,
        @NotNull @Valid Ai ai,
        @NotNull @Valid Embedding embedding,
        @NotNull @Valid Rag rag,
        @NotNull @Valid Demo demo) {

    /** Bucket4j limits (Document 5, Availability and Abuse Resistance). */
    public record RateLimit(@Positive int perMinute, @Positive int perSandboxPerHour, @Positive int concurrentStreams) {}

    /** Model roles, prompt versions, timeouts and the repair and budget limits (Document 4). */
    public record Ai(
            @NotNull @Valid Models models,
            @NotNull Map<String, String> promptVersions,
            @NotNull @Valid Timeouts timeouts,
            @Min(0) int maxRepairAttempts,
            Long dailyTokenBudget,
            boolean logPayloads) {

        /** The strong model authors, reviews and changes; the fast model explains and answers. */
        public record Models(@NotBlank String strong, @NotBlank String fast) {}

        /** Seconds; the provider timeouts of Document 2, AI Layer Design. */
        public record Timeouts(@Positive int authorSeconds, @Positive int chatFirstTokenSeconds) {}
    }

    /** 1536 for OpenAI text-embedding-3-small, 1024 for bge-m3; checked against the vector column at startup. */
    public record Embedding(@Positive int dimension) {}

    /** Hybrid retrieval: top-k chunks and the minimum fused score below which the answer is "not covered". */
    public record Rag(@Positive int topK, @DecimalMin("0.0") @DecimalMax("1.0") double minScore) {}

    /** Nightly reset schedule and the fixture set the demo loads. */
    public record Demo(@NotBlank String resetCron, @NotBlank String fixtureSet) {}
}
