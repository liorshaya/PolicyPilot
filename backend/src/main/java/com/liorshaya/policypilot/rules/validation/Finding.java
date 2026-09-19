package com.liorshaya.policypilot.rules.validation;

import java.util.List;

/**
 * One validation finding in the Document 3 reporting shape. {@code path} is a JSON pointer into the document, as
 * specific as the check can make it (the decision table highlights the cell it names); the empty string is the
 * whole document.
 */
public record Finding(
        ValidationCode code, String path, String message, List<String> ruleIds, List<String> fieldNames) {

    public Finding {
        ruleIds = List.copyOf(ruleIds);
        fieldNames = List.copyOf(fieldNames);
    }

    /** The severity of the code. */
    public Severity severity() {
        return code.severity();
    }
}
