package com.liorshaya.policypilot.eval;

import com.liorshaya.policypilot.ai.service.Proposal;
import com.liorshaya.policypilot.engine.CompiledRuleSet;
import com.liorshaya.policypilot.engine.Decision;
import com.liorshaya.policypilot.engine.Evaluation;
import com.liorshaya.policypilot.engine.RuleEngine;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.patch.Patches;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Change correctness (Document 4, Evaluation Set and Metrics): a labeled request is correct when its proposal
 * validates, its patches replace and remove exactly the rules the expected set replaces and removes and add as many
 * rules as it adds, and the patched rule set decides every case of the request's regression case file with the
 * outcome the expected patches give that case; the impossible request is correct when the answer has no patches.
 */
final class ChangeScoring {

    private static final RuleSetMapper MAPPER = new RuleSetMapper();
    private static final RuleEngine ENGINE = new RuleEngine();

    private ChangeScoring() {}

    /** Whether a proposal is correct, and if not, the first thing that made it wrong, for the report's mismatches. */
    record Verdict(boolean correct, String reason) {

        static final Verdict CORRECT = new Verdict(true, "");

        static Verdict wrong(String reason) {
            return new Verdict(false, reason);
        }
    }

    /**
     * @param labeled one request of fixtures/eval/changes.json
     * @param proposal what the change use case answered, after its repairs
     */
    static Verdict score(JsonNode labeled, Proposal proposal) {
        JsonNode expected = labeled.required("expected").required("patches");
        JsonNode patches = proposal.answer().path("patches");
        if (expected.isEmpty()) {
            return patches.isEmpty() ? Verdict.CORRECT
                    : Verdict.wrong(patches.size() + " patches for a request the rule set cannot express");
        }
        if (!proposal.valid()) {
            return Verdict.wrong(proposal.refused() ? "refused by the proposal validator"
                    : "not valid after " + proposal.repairs() + " repairs");
        }
        for (String op : List.of("replace", "remove")) {
            if (!targets(patches, op).equals(targets(expected, op))) {
                return Verdict.wrong(op + " " + targets(patches, op) + " where " + targets(expected, op)
                        + " is expected");
            }
        }
        if (targets(patches, "add").size() != targets(expected, "add").size()) {
            return Verdict.wrong("adds " + targets(patches, "add").size() + " rules where "
                    + targets(expected, "add").size() + " is expected");
        }
        ObjectNode base = (ObjectNode) Fixtures.json(labeled.required("ruleset").asString());
        CompiledRuleSet proposed = compiled(Objects.requireNonNull(proposal.validation().patched()));
        CompiledRuleSet reference = compiled(Patches.apply(base, expected).document());
        JsonNode cases = Fixtures.json(labeled.required("regression").required("cases").asString()).required("cases");
        for (JsonNode labeledCase : cases) {
            String ours = outcome(proposed, labeledCase.required("input"));
            String theirs = outcome(reference, labeledCase.required("input"));
            if (!ours.equals(theirs)) {
                return Verdict.wrong("case " + labeledCase.required("id").asString() + " is " + ours + " where the "
                        + "expected patches make it " + theirs);
            }
        }
        return Verdict.CORRECT;
    }

    /** The rule ids the patches of one operation name, sorted. */
    private static Set<String> targets(JsonNode patches, String op) {
        Set<String> ids = new TreeSet<>();
        for (JsonNode patch : patches) {
            if (op.equals(patch.path("op").asString(""))) {
                ids.add(patch.path("ruleId").asString(""));
            }
        }
        return ids;
    }

    private static CompiledRuleSet compiled(ObjectNode document) {
        return CompiledRuleSet.compile(MAPPER.toRuleSet(document));
    }

    private static String outcome(CompiledRuleSet ruleSet, JsonNode input) {
        Evaluation evaluation = ENGINE.evaluate(ruleSet, (ObjectNode) input.deepCopy());
        return evaluation instanceof Decision decision && decision.outcome() != null ? decision.outcome().json()
                : "error";
    }
}
