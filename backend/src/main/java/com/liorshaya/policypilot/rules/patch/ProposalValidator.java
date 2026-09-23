package com.liorshaya.policypilot.rules.patch;

import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/**
 * The proposal validator (Document 3, Patch validation; Document 5, RT-04): the patches a proposal may not make at
 * all. It reads the request as the analyst wrote it and the candidates the model was shown, never the model's
 * rationale, so nothing the request smuggles in or the model argues can talk a refusal away. A patch on a rule the
 * version does not have is left to the application step, which reports it.
 */
final class ProposalValidator {

    private ProposalValidator() {}

    /** The refusals of a Patches object that passed the schema, in patch order; empty when there is none. */
    static List<PatchProblem> refusals(JsonNode patches, RuleSet base, Mentions request, Set<String> candidates) {
        Map<String, Rule> rules = base.rules().stream().collect(Collectors.toMap(Rule::id, Function.identity()));
        List<PatchProblem> refusals = new ArrayList<>();
        JsonNode list = patches.path("patches");
        for (int i = 0; i < list.size(); i++) {
            JsonNode patch = list.get(i);
            String at = "/patches/" + i;
            PatchOp op = PatchOp.of(patch.path("op").asString());
            Rule target = rules.get(patch.path("ruleId").asString(""));
            boolean onARuleOfTheVersion = target != null && (op == PatchOp.REMOVE || op == PatchOp.REPLACE);
            if (op == PatchOp.SET_DEFAULTS) {
                refusals.add(new PatchProblem(PatchCode.PATCH_SETS_DEFAULTS, at,
                        "a proposal never changes the defaults; only an analyst edits them", List.of(), List.of()));
            } else if (onARuleOfTheVersion && op == PatchOp.REMOVE && !request.names(target)) {
                refusals.add(new PatchProblem(PatchCode.PATCH_REMOVES_UNMENTIONED, at, target.id()
                        + " is removed, but the request names it neither by its id nor by a value its condition tests",
                        List.of(target.id()), List.of()));
            } else if (onARuleOfTheVersion && !candidates.contains(target.id())) {
                refusals.add(new PatchProblem(PatchCode.PATCH_OUTSIDE_CANDIDATES, at + "/ruleId",
                        target.id() + " was not among the candidate rules the model was shown",
                        List.of(target.id()), List.of()));
            }
        }
        return refusals;
    }
}
