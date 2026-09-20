package com.liorshaya.policypilot.ai.adapter;

import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.entity.ModelCallEntity;
import com.liorshaya.policypilot.ai.repository.ModelCallRepository;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row per model call (Document 4, Logging for every call): prompt name and version, model, provider, attempt
 * number, tokens, latency, how the answer validated, whether it came from the cache, and the trace id that ties
 * it to the request. This is the table the cost view and the evaluation runner read.
 *
 * <p>Rows are written in their own transaction: a call that happened is recorded even when the request that made
 * it fails afterwards.
 */
@Component
public class ModelCallLedger {

    private final ModelCallRepository calls;
    private final Clock clock;
    private final @Nullable Tracer tracer;

    public ModelCallLedger(ModelCallRepository calls, Clock clock, @Nullable Tracer tracer) {
        this.calls = calls;
        this.clock = clock;
        this.tracer = tracer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            PromptSpec spec, String model, String provider, TokenUsage usage, Duration latency, String result,
            boolean cacheHit) {
        calls.save(new ModelCallEntity(
                UUID.randomUUID(),
                clock.instant(),
                spec.promptName(),
                spec.promptVersion(),
                model,
                provider,
                spec.attempt(),
                usage.inputTokens(),
                usage.outputTokens(),
                latency.toMillis(),
                result,
                cacheHit,
                traceId()));
    }

    private @Nullable String traceId() {
        if (tracer == null || tracer.currentSpan() == null) {
            return null;
        }
        return tracer.currentSpan().context().traceId();
    }

    /** The results a call can end with, as the ledger spells them. */
    public static final class Results {
        /** The answer parsed and passed every check. */
        public static final String VALID = "VALID";
        /** The answer was not JSON the contract accepts. */
        public static final String MALFORMED = "MALFORMED";
        /** The answer parsed but failed the validator, so a repair follows or the request fails. */
        public static final String INVALID = "INVALID";
        /** The provider did not answer. */
        public static final String UNAVAILABLE = "UNAVAILABLE";
        /** The provider answered, but the answer was cut off by the output cap and held no text. */
        public static final String TRUNCATED = "TRUNCATED";

        private Results() {}
    }
}
