package com.liorshaya.policypilot.engine;

import com.liorshaya.policypilot.rules.validation.CaseProblem;
import java.util.List;

/** CASE_INVALID (Document 3, step 1): the case is not a case, nothing was evaluated and nothing is stored. */
public record CaseError(List<CaseProblem> problems) implements Evaluation {

    public CaseError {
        problems = List.copyOf(problems);
    }
}
