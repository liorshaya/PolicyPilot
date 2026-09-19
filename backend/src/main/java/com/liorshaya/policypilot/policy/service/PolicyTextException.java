package com.liorshaya.policypilot.policy.service;

import java.util.List;

/** Policy text over its limits; each violation names a JSON pointer and a problem, never the text itself. */
public class PolicyTextException extends RuntimeException {

    /** One broken limit: {@code /text} or {@code /paragraphs/<index>}, and what is wrong. */
    public record Violation(String path, String problem) {}

    private final transient List<Violation> violations;

    public PolicyTextException(List<Violation> violations) {
        super("policy text over its limits", null, false, false);
        this.violations = List.copyOf(violations);
    }

    public List<Violation> violations() {
        return violations;
    }
}
