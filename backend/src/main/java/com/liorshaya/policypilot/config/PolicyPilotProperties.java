package com.liorshaya.policypilot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application properties owned by PolicyPilot (Document 2, Configuration and Model Providers).
 *
 * <p>Every value has a default in {@code application.yml} except the three secrets, which come from environment
 * variables only, and {@code ai.daily-token-budget}, for which the documents fix no number yet (the token budget
 * guard of day 7 must refuse to start in the cloud profile while it is missing). The application refuses to start
 * without an access code of 8 lowercase letters and a cookie secret of at least 32 bytes (Document 5, Data
 * Protection, Secrets). The admin code is optional: without one, {@code POST /admin/reset} refuses every request.
 */
@Validated
@ConfigurationProperties(prefix = "policypilot")
public record PolicyPilotProperties(
        @NotNull(message = "POLICYPILOT_ACCESS_CODE is required")
        @Pattern(regexp = "[a-z]{8}", message = "POLICYPILOT_ACCESS_CODE must be 8 lowercase letters")
        String accessCode,
        @NotNull(message = "POLICYPILOT_COOKIE_SECRET is required") String cookieSecret,
        String adminCode,
        @NotNull @Valid RateLimit rateLimit,
        @NotNull @Valid Web web,
        @NotNull @Valid Ai ai,
        @NotNull @Valid Embedding embedding,
        @NotNull @Valid Rag rag,
        @NotNull @Valid Demo demo) {

    /** HMAC-SHA256 with a 32-byte secret (Document 5, Cookie). */
    public static final int MIN_COOKIE_SECRET_BYTES = 32;

    @AssertTrue(message = "POLICYPILOT_COOKIE_SECRET must be at least 32 bytes")
    public boolean isCookieSecretLongEnough() {
        return cookieSecret == null || cookieSecret.getBytes(StandardCharsets.UTF_8).length >= MIN_COOKIE_SECRET_BYTES;
    }

    /** Bucket4j limits (Document 5, Availability and Abuse Resistance). */
    public record RateLimit(@Positive int perMinute, @Positive int perSandboxPerHour, @Positive int concurrentStreams) {}

    /** The web app's origins: the CORS allowlist and the Origin check (Document 5, CSRF). */
    public record Web(@NotEmpty List<@NotBlank String> allowedOrigins) {}

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

        /**
         * Seconds. The chat stream's deadline lives here, and a prompt's own timeout in its {@code prompt.yml}
         * (Document 2, Application properties). {@code promptSeconds} replaces a prompt's own timeout by name: a
         * provider profile whose model writes slower sets it (Document 4, Model Configuration per Prompt: the
         * {@code ollama} profile, decided on day 15); the default sets none.
         */
        public record Timeouts(@Positive int chatFirstTokenSeconds,
                @Nullable Map<String, @Positive Integer> promptSeconds) {

            /** None when the profile sets none: an empty map binds as no properties at all. */
            public Timeouts {
                promptSeconds = promptSeconds == null ? Map.of() : Map.copyOf(promptSeconds);
            }
        }
    }

    /** 1536 for OpenAI text-embedding-3-small, 1024 for bge-m3; checked against the vector column at startup. */
    public record Embedding(@Positive int dimension) {}

    /** Hybrid retrieval: top-k chunks and the minimum fused score below which the answer is "not covered". */
    public record Rag(@Positive int topK, @DecimalMin("0.0") @DecimalMax("1.0") double minScore) {}

    /** Nightly reset schedule and the fixture set the demo loads. */
    public record Demo(@NotBlank String resetCron, @NotBlank String fixtureSet) {}
}
