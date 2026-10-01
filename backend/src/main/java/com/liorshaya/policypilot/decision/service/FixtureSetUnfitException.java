package com.liorshaya.policypilot.decision.service;

import java.util.List;

/**
 * A fixture set whose cases leave some of the version's case inputs unsupplied (Document 2, decide, 2026-10-01): the
 * API answers 400 {@code REQUEST_INVALID} at {@code /fixtureSet}, naming those inputs, and nothing is evaluated.
 */
public class FixtureSetUnfitException extends RuntimeException {

    private final transient List<String> missingInputs;

    public FixtureSetUnfitException(List<String> missingInputs) {
        super("FIXTURE_SET_UNFIT", null, false, false);
        this.missingInputs = List.copyOf(missingInputs);
    }

    /** The version's case inputs no case of the set supplies, in the order the version declares them. */
    public List<String> missingInputs() {
        return missingInputs;
    }
}
