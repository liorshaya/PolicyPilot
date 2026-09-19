package com.liorshaya.policypilot.decision.service;

import java.util.List;

/**
 * A case that fails case validation (Document 3, step 1: a case error is not a decision, and nothing is stored). The
 * API answers 422 {@code CASE_INVALID} with one detail per problem: the field's pointer and the problem's code, never
 * the value (Document 5, Error responses).
 */
public class CaseInvalidException extends RuntimeException {

    private final transient List<CaseProblemView> problems;

    public CaseInvalidException(List<CaseProblemView> problems) {
        super("CASE_INVALID", null, false, false);
        this.problems = List.copyOf(problems);
    }

    public List<CaseProblemView> problems() {
        return problems;
    }

    /** One problem: the JSON pointer of the field in the request, and the code of Document 3's case validation. */
    public record CaseProblemView(String path, String code) {}
}
