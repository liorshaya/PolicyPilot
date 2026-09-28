package com.liorshaya.policypilot.web.response;

import com.liorshaya.policypilot.decision.service.Regression;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A proposal's regression report (Document 3, Regression report): how many of the sandbox's decisions on the base
 * version the copy decided again and their outcomes before, each flipped outcome, the count of each transition,
 * {@code approve → reject}, and the decisions whose flags moved.
 */
public record RegressionResponse(int decisions, Map<String, Integer> before, List<Flip> flips,
        Map<String, Integer> transitions, FlagsMoved flagsMoved) {

    public static RegressionResponse of(Regression regression) {
        return new RegressionResponse(regression.decisions(), regression.before(), regression.flips().stream()
                .map(flip -> new Flip(flip.decisionId(), flip.caseNo(), flip.before(), flip.after(),
                        flip.decidingRuleBefore(), flip.decidingRuleAfter()))
                .toList(), regression.transitions(),
                new FlagsMoved(regression.flagsMoved().decisions(), regression.flagsMoved().byRule()));
    }

    /** How many decisions gained or lost a flag, and how many of them did so by each rule that raises one. */
    public record FlagsMoved(int decisions, Map<String, Integer> byRule) {}

    /** One flipped outcome; the case number is null for a case decided on its own. */
    public record Flip(UUID decisionId, @Nullable Integer caseNo, String before, String after,
            @Nullable String decidingRuleBefore, @Nullable String decidingRuleAfter) {}
}
