package com.liorshaya.policypilot.rules.model;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Where a rule comes from: the policy text, a person, or a pending change (Document 3, Provenance). */
public sealed interface Provenance {

    /** A citation of a policy paragraph; {@code confidence} is shown, never used for logic. */
    record Quoted(int paragraph, String quote, @Nullable BigDecimal confidence) implements Provenance {}

    /** A person's rule, created by the system on approval or through the UI. */
    record Analyst(String note, String actor, @Nullable String changeRequestId) implements Provenance {}

    /** A model change the policy text no longer supports, valid only inside a change proposal. */
    record Pending(String changeRequestId, String rationale) implements Provenance {}
}
