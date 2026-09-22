package com.liorshaya.policypilot.eval;

import com.liorshaya.policypilot.rag.service.NotCoveredSentences;
import com.liorshaya.policypilot.rules.model.Language;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Document 4: "Refusal accuracy --- not-covered questions answered with the fixed sentence, and covered questions
 * not refused". What counts is the answer the person got, not how it was reached: the Threshold stopping a
 * question before any model call and the model quoting the sentence itself are the same outcome, and a covered
 * question that gets the sentence anyway is a miss however it got there.
 *
 * <p>This is the distinction {@code NotCoveredSentences} exists for: the file is quoted by the API and by the
 * answer prompt alike, "so the API can recognize a refusal the model writes as the one it would have written".
 */
final class RefusalScoring {

    private static final NotCoveredSentences SENTENCES = NotCoveredSentences.load();

    private RefusalScoring() {}

    /**
     * @param stopped the Threshold stopped the question, so no answer was ever written
     * @param answer what the model streamed, or {@code null} when it was never asked
     */
    static boolean refused(boolean stopped, @Nullable String answer) {
        if (stopped) {
            return true;
        }
        if (answer == null) {
            return false;
        }
        String written = answer.strip();
        return Arrays.stream(Language.values()).anyMatch(language -> written.startsWith(SENTENCES.of(language)));
    }
}
