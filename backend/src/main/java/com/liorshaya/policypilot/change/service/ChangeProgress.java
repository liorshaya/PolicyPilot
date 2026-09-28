package com.liorshaya.policypilot.change.service;

import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.service.Candidates;

/**
 * What a change request reports while it is worked on (Document 2, API Surface: {@code analyzing}, {@code proposing}
 * with the candidate rules and fields, {@code validating}, {@code regression}, and what the model's answers cost in
 * each); the caller streams each one as it happens.
 */
public interface ChangeProgress {

    /** The request is embedded and the version's rules searched for the candidates. */
    void analyzing();

    /** The model is asked, and shown these candidates and no other rule. */
    void proposing(Candidates candidates);

    /** The model answered; the answer is validated, and repaired when it has errors. */
    void validating();

    /** The proposal validated; the sandbox's decisions on the base version are decided again by the copy. */
    void regression();

    /** An answer of the model arrived in the stage under way: what it cost, nothing for one the cache served. */
    void spent(TokenUsage usage);
}
