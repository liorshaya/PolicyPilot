package com.liorshaya.policypilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Document 4's two authoring-validity metrics, "Schema-valid first try" and "Valid after repairs", scored by the runner
 * from the recordings of a live pass. The expected values are the pass's own verdicts, which it printed as each answer
 * arrived (LiveAuthorPassIT, 2026-09-24): VALID for every one of the 18 labeled policies.
 */
class AuthorScoringTest {

    // author/v2 on OpenAI, one hinted run per labeled policy, every one valid on its first answer. Expected: 18 of 18
    // schema-valid first try, and 18 of 18 valid after repairs as an exact count, not a floor, which passes the 1.00
    @Test
    void authorV2IsValidOnTheFirstAnswerForEveryLabeledPolicy() {
        EvalReport report = new EvalReport(LocalDate.EPOCH, Map.of("author", "v2"), "openai");

        RecordedScoring.Authoring authoring = new RecordedScoring("openai").scoreAuthoring(report, "v2");

        assertThat(authoring.policies()).isEqualTo(18);
        assertThat(report.markdown())
                .contains("| Schema-valid first try | 0.90 | 1.00 (18 of 18) | not run | PASS |")
                .contains("| Valid after repairs | 1.00 | 1.00 (18 of 18) | not run | PASS |");
    }
}
