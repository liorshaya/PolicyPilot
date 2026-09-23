package com.liorshaya.policypilot.rules.patch;

/**
 * The codes of Document 3, Patch validation, in its table's order. Every one is an error; the first three are the
 * proposal validator's refusals, which end a proposal without a repair (Document 5, RT-04), the rest are Document 3's
 * constraints on applying a patch, which go back to the model.
 */
public enum PatchCode {
    PATCH_REMOVES_UNMENTIONED(true),
    PATCH_OUTSIDE_CANDIDATES(true),
    PATCH_SETS_DEFAULTS(true),
    PATCH_TARGET_UNKNOWN(false),
    PATCH_TARGET_REPEATED(false),
    PATCH_ID_CHANGED(false),
    PATCH_ID_NOT_NEW(false),
    PATCH_FIELD_REQUIRED(false);

    private final boolean refusal;

    PatchCode(boolean refusal) {
        this.refusal = refusal;
    }

    /** Whether the proposal validator reports the code: the proposal is refused rather than repaired. */
    public boolean refuses() {
        return refusal;
    }
}
