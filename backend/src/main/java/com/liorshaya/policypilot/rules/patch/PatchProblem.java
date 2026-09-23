package com.liorshaya.policypilot.rules.patch;

import java.util.List;

/**
 * One problem of a Patches object, in the reporting shape of Document 3 (Static Validation): {@code path} is a JSON
 * pointer into the Patches object, as specific as the check can make it, and the rule ids or field names are those of
 * the patch it concerns. Every patch code is an error.
 */
public record PatchProblem(PatchCode code, String path, String message, List<String> ruleIds,
        List<String> fieldNames) {

    public PatchProblem {
        ruleIds = List.copyOf(ruleIds);
        fieldNames = List.copyOf(fieldNames);
    }
}
