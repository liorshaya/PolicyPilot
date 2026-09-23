package com.liorshaya.policypilot.change.service;

import com.liorshaya.policypilot.ai.service.Candidates;

/**
 * What a change request reports while it is worked on (Document 2, API Surface: {@code analyzing}, {@code proposing}
 * with the candidate rules and fields, {@code validating}); the caller streams each one as it happens.
 */
public interface ChangeProgress {

    /** The request is embedded and the version's rules searched for the candidates. */
    void analyzing();

    /** The model is asked, and shown these candidates and no other rule. */
    void proposing(Candidates candidates);

    /** The model answered; the answer is validated, and repaired when it has errors. */
    void validating();
}
