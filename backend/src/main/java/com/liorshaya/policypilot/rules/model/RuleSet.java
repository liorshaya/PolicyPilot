package com.liorshaya.policypilot.rules.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A rule set document (Document 3, Document Structure). */
public record RuleSet(
        String dslVersion,
        String id,
        String name,
        Language language,
        @Nullable String description,
        List<Field> fields,
        Defaults defaults,
        List<Rule> rules) {

    /** The only DSL version this code understands. */
    public static final String DSL_VERSION = "1.0";

    public RuleSet {
        fields = List.copyOf(fields);
        rules = List.copyOf(rules);
    }
}
