package com.liorshaya.policypilot.web.response;

import com.liorshaya.policypilot.decision.service.Regression;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A proposal's regression report (Document 3, Regression report): how many of the sandbox's decisions on the base
 * version the copy decided again, each flipped outcome, and the count of each transition, {@code approve → reject}.
 */
public record RegressionResponse(int decisions, List<Flip> flips, Map<String, Integer> transitions) {

    public static RegressionResponse of(Regression regression) {
        return new RegressionResponse(regression.decisions(), regression.flips().stream()
                .map(flip -> new Flip(flip.decisionId(), flip.caseNo(), flip.before(), flip.after(),
                        flip.decidingRuleBefore(), flip.decidingRuleAfter()))
                .toList(), regression.transitions());
    }

    /** One flipped outcome; the case number is null for a case decided on its own. */
    public record Flip(UUID decisionId, @Nullable Integer caseNo, String before, String after,
            @Nullable String decidingRuleBefore, @Nullable String decidingRuleAfter) {}
}
