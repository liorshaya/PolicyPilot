package com.liorshaya.policypilot.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Requirement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * The two numbers an interviewer can check with a stopwatch (NFR-6; Document 6, Performance and Determinism Tests:
 * the 200-case batch under 1 second, a single decision under 50 ms, both with persistence). The measured median is
 * printed for the job summary, and the test fails above three times its target, as Document 6 says.
 */
@Isolated
@Requirement("NFR-6")
class DecisionPerformanceIT extends ApiIntegrationTest {

    private static final Duration BATCH_TARGET = Duration.ofSeconds(1);
    private static final Duration SINGLE_TARGET = Duration.ofMillis(50);
    /** Document 6: "the timing tests fail only above three times their target". */
    private static final int TOLERANCE = 3;

    private Decisions decisions;

    @BeforeEach
    void logIn() {
        decisions = new Decisions(api(), api().login());
    }

    // Document 6, Batch endpoint: the 200-case fixture set with persistence, median of three
    @Test
    void theTwoHundredCaseBatchTakesUnderOneSecondMedianOfThree() {
        List<Duration> runs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            runs.add(timed(() -> assertThat(decisions.decide("{\"fixtureSet\":\"cases-200\"}").statusCode())
                    .isEqualTo(200)));
        }

        Duration median = median(runs);
        System.out.println("performance: 200-case batch median " + median.toMillis() + " ms");
        assertThat(median).isLessThan(BATCH_TARGET.multipliedBy(TOLERANCE));
    }

    // Document 6, Single decision: one case through the API with persistence, median of 20 after warm-up
    @Test
    void aSingleDecisionTakesUnder50MillisecondsMedianOf20AfterWarmUp() {
        String body = "{\"case\":" + Decisions.input(Decisions.DEMO_CASE) + "}";
        for (int i = 0; i < 5; i++) {
            decisions.decide(body);
        }

        List<Duration> runs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            runs.add(timed(() -> assertThat(decisions.decide(body).statusCode()).isEqualTo(200)));
        }

        Duration median = median(runs);
        System.out.println("performance: single decision median " + median.toMillis() + " ms");
        assertThat(median).isLessThan(SINGLE_TARGET.multipliedBy(TOLERANCE));
    }

    private static Duration timed(Runnable call) {
        long started = System.nanoTime();
        call.run();
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static Duration median(List<Duration> runs) {
        return runs.stream().sorted().toList().get(runs.size() / 2);
    }
}
