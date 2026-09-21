package com.liorshaya.policypilot.ai.service.chat;

/**
 * The denylist scan stopped an answer that carried a secret (Document 5, RT-02; Document 2, {@code ANSWER_WITHHELD}):
 * the stream ends with an error and nothing of the answer is stored.
 */
public class AnswerWithheldException extends RuntimeException {

    public AnswerWithheldException() {
        super("the answer was withheld by the denylist scan");
    }
}
