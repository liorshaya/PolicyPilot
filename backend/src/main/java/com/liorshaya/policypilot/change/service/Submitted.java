package com.liorshaya.policypilot.change.service;

import com.liorshaya.policypilot.ai.service.Proposal;

/**
 * What a submitted change request came to (Document 2, API Surface): a stored proposal, or one that is not stored
 * because it failed Patch validation after its repairs or the proposal validator refused it (Document 3).
 */
public sealed interface Submitted {

    /** The proposal validated and is stored as a PROPOSED change request. */
    record Stored(ChangeRequestView request) implements Submitted {}

    /** Nothing is stored; the proposal says what the model answered last and what validation found in it. */
    record NotStored(Proposal proposal) implements Submitted {}
}
