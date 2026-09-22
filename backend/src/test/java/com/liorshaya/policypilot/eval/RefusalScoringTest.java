package com.liorshaya.policypilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rag.service.NotCoveredSentences;
import com.liorshaya.policypilot.rules.model.Language;
import org.junit.jupiter.api.Test;

/**
 * Document 4 counts a refusal by the answer, not by the path it took. The sentences are read from
 * {@code prompts/answer/not-covered.yml}, the file the API and the answer prompt both quote, so nothing below is
 * a sentence this test wrote.
 */
class RefusalScoringTest {

    private static final NotCoveredSentences SENTENCES = NotCoveredSentences.load();

    // Document 4, Threshold: a question the corpus does not cover is stopped before the model. Expected: refused
    @Test
    void aQuestionTheThresholdStoppedWasRefused() {
        assertThat(RefusalScoring.refused(true, null)).isTrue();
    }

    // The half that "stopped by the Threshold" misses: the model itself quoting the sentence. Expected: refused,
    // in both languages, which is how Q-10 and Q-30 are answered
    @Test
    void aQuestionTheModelAnsweredWithTheFixedSentenceWasRefused() {
        assertThat(RefusalScoring.refused(false, SENTENCES.of(Language.HE))).isTrue();
        assertThat(RefusalScoring.refused(false, SENTENCES.of(Language.EN))).isTrue();
    }

    // Document 4: the sentence may be followed by one more saying what the documents do cover. Expected: still
    // a refusal, which is how the model actually answers
    @Test
    void theSentenceWithItsSecondSentenceAfterItIsStillARefusal() {
        String answer = SENTENCES.of(Language.HE) + " המסמכים עוסקים בתנאי גיל ובסכום ההלוואה. [[r:R-100]]";

        assertThat(RefusalScoring.refused(false, answer)).isTrue();
    }

    // Expected: an answer that answers is not a refusal, however short it is
    @Test
    void anAnswerThatAnswersIsNotARefusal() {
        assertThat(RefusalScoring.refused(false, "תקופת ההחזר המקסימלית היא 84 חודשים.[[p:2]]")).isFalse();
        assertThat(RefusalScoring.refused(false, "A reservist with 45 reserve days gets 10%.[[r:R-030]]")).isFalse();
    }

    // A question that reached the model and was never recorded is not a refusal: nothing is known about it, and
    // guessing either way would put a number in the report that no answer stands behind
    @Test
    void aQuestionWithNoAnswerAtAllIsNotCountedAsRefused() {
        assertThat(RefusalScoring.refused(false, null)).isFalse();
    }
}
