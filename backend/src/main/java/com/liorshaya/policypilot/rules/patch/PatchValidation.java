package com.liorshaya.policypilot.rules.patch;

import com.liorshaya.policypilot.rules.validation.Finding;
import com.liorshaya.policypilot.rules.validation.Severity;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * What Patch validation found (Document 3): the findings of the schema or of the patched copy, each on a patched rule
 * reported at its patch; the problems of the proposal validator or of the application; and, once the patches applied,
 * the copy, the rules the model wrote in it and the ids it retired.
 *
 * @param patched the version with the patches applied, or null when a step before the application failed
 * @param modelRuleIds the rules the patches added or replaced: the model's rules in the CHANGE_PROPOSAL context
 * @param retiredIds the rule ids the patches removed, which the new version records as retired
 */
public record PatchValidation(List<Finding> findings, List<PatchProblem> problems, @Nullable ObjectNode patched,
        Set<String> modelRuleIds, List<String> retiredIds) {

    public PatchValidation {
        findings = List.copyOf(findings);
        problems = List.copyOf(problems);
        modelRuleIds = Set.copyOf(modelRuleIds);
        retiredIds = List.copyOf(retiredIds);
    }

    /** Whether the proposal validator refused the proposal: it is neither repaired nor stored (Document 5, RT-04). */
    public boolean refused() {
        return problems.stream().anyMatch(problem -> problem.code().refuses());
    }

    /**
     * Whether the copy may be shown as a proposal: every patch applied, which is when there is a copy at all, and the
     * copy has no error.
     */
    public boolean valid() {
        return patched != null && findings.stream().noneMatch(finding -> finding.severity() == Severity.ERROR);
    }
}
