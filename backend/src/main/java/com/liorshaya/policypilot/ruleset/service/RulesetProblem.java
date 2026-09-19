package com.liorshaya.policypilot.ruleset.service;

/**
 * One reason a rule set was refused: the JSON pointer of the node and the code of the finding (Document 3, Error
 * reporting shape; Document 5, Error responses: a message never repeats the request).
 */
public record RulesetProblem(String path, String code) {

    /** The document's id is the rule set's lineage identity and may not change (Document 2, PUT rules). */
    public static final String ID_CHANGED = "RULESET_ID_CHANGED";
}
