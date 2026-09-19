package com.liorshaya.policypilot.rules.validation;

import com.liorshaya.policypilot.rules.model.Literal;
import java.util.List;
import java.util.Map;

/**
 * A validated case: the typed value of every field the case supplied or a default filled (step 2 of Document 3's
 * evaluation), or the problems that make it a case error; a case with problems is never evaluated.
 */
public record CaseValidation(Map<String, Literal> values, List<CaseProblem> problems) {

    public CaseValidation {
        values = Map.copyOf(values);
        problems = List.copyOf(problems);
    }

    /** Whether the case may be evaluated. */
    public boolean isValid() {
        return problems.isEmpty();
    }
}
