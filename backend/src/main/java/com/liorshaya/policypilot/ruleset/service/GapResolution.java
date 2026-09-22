package com.liorshaya.policypilot.ruleset.service;

import java.util.Locale;

/**
 * How an analyst resolved a {@code gap} finding (Document 3, Publishing gate): a rule now covers the passage, a
 * manual-check flag surfaces it on every decision, or a note explains why an existing rule already covers it.
 */
public enum GapResolution {
    RULE_ADDED,
    FLAG_ADDED,
    INTERPRETATION;

    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GapResolution of(String json) {
        return valueOf(json.toUpperCase(Locale.ROOT));
    }
}
