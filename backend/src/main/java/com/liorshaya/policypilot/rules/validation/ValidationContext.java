package com.liorshaya.policypilot.rules.validation;

/** Who produced the document, which decides the provenance it may carry (Document 3, Validation contexts). */
public enum ValidationContext {
    /** A model draft: every rule is the model's, so neither {@code analyst} nor {@code pending} is allowed. */
    AUTHORING,
    /** A change proposal: only the patched rules are the model's; they may carry {@code quoted} or {@code pending}. */
    CHANGE_PROPOSAL,
    /** A version submitted for publishing: no {@code pending} may remain. */
    PUBLISH,
    /** A person's edit in the UI, which may create {@code analyst} provenance. */
    ANALYST_EDIT
}
