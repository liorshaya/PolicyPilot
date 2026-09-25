package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.Requirement;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * The words that name a decision's outcome (Document 4, Prompt 4: Words that name an outcome). A label's word that is
 * one of an outcome's forms is said by any form of that outcome; any other word is said as written. The sentences take
 * the shapes of the live answers of 2026-09-24 that the cache refused ("אושרה" or "אישור", the worklog's gate G3 proof)
 * and of the answer the live run recorded ("מאושרת"); the forms, the prefix letters and the negations are Document 4's.
 */
@Requirement({"FR-13", "NFR-5"})
class OutcomeWordsTest {

    private final OutcomeWords words = OutcomeWords.load();

    // Document 4: Q-02's label says "מאושר", its live answers said "אושרה" or "אישור", the recorded one "מאושרת".
    // Expected: each of the three says the label's word
    @Test
    void everyFormOfTheApprovalSaysTheLabelsWord() {
        assertThat(words.says("כן. בסימולציה עם ערב, בקשה 17 אושרה לפי R-900.", "מאושר")).isTrue();
        assertThat(words.says("התוצאה בסימולציה עם ערב היא אישור, לפי כלל R-900.", "מאושר")).isTrue();
        assertThat(words.says("כן. בסימולציה עם ערב, בקשה 17 הייתה מאושרת לפי R-900.", "מאושר")).isTrue();
    }

    // Document 4: "in Hebrew also after up to three of the prefix letters". Expected: "וכשאושרה" (and when it was
    // approved, three prefix letters) says it; a fourth prefix letter, or a letter that is not one, does not
    @Test
    void aFormSaysItsOutcomeAfterUpToThreePrefixLetters() {
        assertThat(words.says("וכשאושרה הבקשה, נשלח מכתב.", "מאושר")).isTrue();
        assertThat(words.says("ושכשאושרה הבקשה, נשלח מכתב.", "מאושר")).isFalse();
        assertThat(words.says("הבקשה תמאושרת.", "מאושר")).isFalse();
    }

    // Document 4: a form "counts only when none of the three words before it is a negation". Expected: "לא" one, two
    // or three words before, with a prefix letter or without, cancels it; four words before, it does not
    @Test
    void aNegationAmongTheThreeWordsBeforeAFormSaysTheOppositeOutcome() {
        assertThat(words.says("הבקשה לא אושרה.", "מאושר")).isFalse();
        assertThat(words.says("הבקשה לא הייתה מאושרת.", "מאושר")).isFalse();
        assertThat(words.says("לא הייתה הבקשה מאושרת.", "מאושר")).isFalse();
        assertThat(words.says("הבקשה נבדקה ולא אושרה.", "מאושר")).isFalse();
        assertThat(words.says("לא נדרש ערב והבקשה מאושרת.", "מאושר")).isTrue();
    }

    // A form of another outcome is not the label's outcome. Expected: rejected and referred do not say "מאושר"
    @Test
    void anotherOutcomesFormDoesNotSayTheLabelsWord() {
        assertThat(words.says("גם עם ערב, בקשה 17 נדחתה לפי R-220.", "מאושר")).isFalse();
        assertThat(words.says("בקשה 17 הופנתה לבדיקה ידנית לפי R-330.", "מאושר")).isFalse();
    }

    // Document 4: English forms "in any letter case", and "any word ending in n't" a negation. Expected:
    // "REJECTED" says the labeled set's "reject" (Q-05); "wouldn't be rejected" and "cannot be rejected" do not
    @Test
    void anEnglishFormSaysItsOutcomeInAnyCaseUnlessANegationComesBefore() {
        assertThat(words.says("An applicant with two adverse events is REJECTED by R-220.", "reject")).isTrue();
        assertThat(words.says("The application wouldn't be rejected.", "reject")).isFalse();
        assertThat(words.says("The application cannot be rejected.", "reject")).isFalse();
    }

    // Document 4: "Any other word of a label is matched as written, as before". Expected: Q-01's "ערב" is said though
    // the answer denies there is one, and Q-03's "84" is said by 84 but not by 48
    @Test
    void aWordThatNamesNoOutcomeIsSaidAsWritten() {
        assertThat(words.says("לבקשה 17 אין ערב, ולכן הופנתה לבדיקה.", "ערב")).isTrue();
        assertThat(words.says("תקופת ההחזר המקסימלית היא 84 חודשים.", "84")).isTrue();
        assertThat(words.says("תקופת ההחזר המקסימלית היא 48 חודשים.", "84")).isFalse();
    }

    // Document 4's table: each form belongs to one outcome. Expected: every form of the file says its own outcome's
    // first form and no other outcome's, so none is listed twice or under the wrong outcome
    @Test
    void everyFormSaysItsOwnOutcomeAndNoOther() {
        Map<String, List<String>> outcomes = formsByOutcome();
        List<String> misfiled = new ArrayList<>();
        outcomes.forEach((outcome, forms) -> {
            for (String form : forms) {
                outcomes.forEach((other, otherForms) -> {
                    if (words.says(form, otherForms.getFirst()) != other.equals(outcome)) {
                        misfiled.add(form + " as " + other);
                    }
                });
            }
        });

        assertThat(outcomes).containsOnlyKeys("approve", "reject", "refer");
        assertThat(misfiled).isEmpty();
    }

    /** Each outcome's forms in the file, Hebrew and English together. */
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> formsByOutcome() {
        try (InputStream in = new ClassPathResource("prompts/answer/outcome-words.yml").getInputStream()) {
            Map<String, Map<String, List<String>>> outcomes =
                    (Map<String, Map<String, List<String>>>) new Yaml().<Map<String, Object>>load(in).get("outcomes");
            Map<String, List<String>> forms = new LinkedHashMap<>();
            outcomes.forEach((outcome, languages) -> forms.put(outcome,
                    languages.values().stream().flatMap(List::stream).toList()));
            return forms;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
