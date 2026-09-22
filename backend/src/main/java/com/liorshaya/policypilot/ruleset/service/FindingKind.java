package com.liorshaya.policypilot.ruleset.service;

import java.util.Locale;

/**
 * The six kinds of review finding and what each asks of the analyst (Document 4, Prompt 2: Review; Document 2,
 * Flow 1): an error must be overridden with a note, a gap needs a recorded resolution, an injection must have been
 * seen (Document 5, RT-05); an ambiguity or a duplicate may be acknowledged or left open.
 */
public enum FindingKind {
    AMBIGUITY,
    CONFLICT,
    UNSUPPORTED,
    GAP,
    DUPLICATE,
    INJECTION;

    /** The name the Findings contract and the API use. */
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FindingKind of(String json) {
        return valueOf(json.toUpperCase(Locale.ROOT));
    }

    /** Conflict and unsupported are errors; the rest are warnings (Document 4, the kind table). */
    public boolean isError() {
        return this == CONFLICT || this == UNSUPPORTED;
    }

    /** The severity the Findings contract requires of this kind. */
    public String severity() {
        return isError() ? "error" : "warning";
    }

    /** A finding of this kind blocks publishing until it is acknowledged (Document 2, Flow 1). */
    public boolean blocksPublishing() {
        return isError() || this == GAP || this == INJECTION;
    }
}
