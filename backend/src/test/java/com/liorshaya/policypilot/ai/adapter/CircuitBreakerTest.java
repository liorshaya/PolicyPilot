package com.liorshaya.policypilot.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The gateway's circuit breaker (Document 4, Guardrails: open for 30 s after 5 consecutive failures). */
class CircuitBreakerTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-30T09:00:00Z"));
    /** The failure counts the breaker reported each time it opened, as the gateway logs them. */
    private final List<Integer> opened = new ArrayList<>();
    private final SpringAiLlmGateway.CircuitBreaker breaker =
            new SpringAiLlmGateway.CircuitBreaker(clock, opened::add);

    @Test
    void theCircuitOpensAfterFiveFailuresInARow() {
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

    @Test
    void aFailedCallAfterTheOpenPeriodOpensTheCircuitForAnother30Seconds() {
        for (int i = 0; i < 5; i++) {
            breaker.failed();
        }
        clock.advance(Duration.ofSeconds(31));
        breaker.requireClosed();

        // the call let through fails too: six failures in a row are still five in a row
        breaker.failed();

        assertThatThrownBy(breaker::requireClosed)
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("failed 6 times in a row");
        clock.advance(Duration.ofSeconds(29));
        assertThatThrownBy(breaker::requireClosed).isInstanceOf(LlmUnavailableException.class);
        clock.advance(Duration.ofSeconds(2));
        assertThatCode(breaker::requireClosed).doesNotThrowAnyException();
    }

    // Document 5, Security logging: "Circuit breaker opened | Provider, failure count". Expected: reported on the
    // fifth failure in a row, not before, and again when the call let through after 30 s fails
    @Test
    void eachOpeningIsReportedWithItsFailureCount() {
        for (int i = 0; i < 4; i++) {
            breaker.failed();
        }
        assertThat(opened).isEmpty();

        breaker.failed();
        clock.advance(Duration.ofSeconds(31));
        breaker.failed();

        assertThat(opened).containsExactly(5, 6);
    }
}
