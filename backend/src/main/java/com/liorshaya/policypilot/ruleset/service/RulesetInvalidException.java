package com.liorshaya.policypilot.ruleset.service;

import java.util.List;

/** A rule set the validator refused; the API answers 422 {@code RULESET_INVALID} with these pointers. */
public class RulesetInvalidException extends RuntimeException {

    private final transient List<RulesetProblem> problems;

    public RulesetInvalidException(List<RulesetProblem> problems) {
        super("RULESET_INVALID", null, false, false);
        this.problems = List.copyOf(problems);
    }

    public List<RulesetProblem> problems() {
        return problems;
    }
}
