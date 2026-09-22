package com.liorshaya.policypilot.ruleset.service;

import java.util.List;

/**
 * A draft cannot be published yet: its review is missing, failed or stale, or a blocking finding is not acknowledged
 * (Document 2, Flow 1). The API answers 422 {@code FINDINGS_UNRESOLVED} with one problem per reason: the path is
 * {@code /review} or {@code /review/findings/{id}}, the code the review's status or the finding's kind.
 */
public class FindingsUnresolvedException extends RuntimeException {

    private final transient List<RulesetProblem> problems;

    public FindingsUnresolvedException(List<RulesetProblem> problems) {
        super("the draft's review does not allow publishing", null, false, false);
        this.problems = List.copyOf(problems);
    }

    public List<RulesetProblem> problems() {
        return problems;
    }
}
