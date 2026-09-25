package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The scripted questions whose answers the cache keeps (Document 4, Prompt 4: Serving the scripted questions from the
 * cache): the demo step 3 questions of the labeled set that call the model, each with the label a kept answer meets.
 * The expected values are the labeled set's, read from {@code fixtures/eval/questions.json}.
 */
@Requirement({"FR-13", "NFR-6"})
class ScriptedAnswersTest {

    private final ScriptedAnswers scripted = ScriptedAnswers.load();

    // Document 4: "copied from Q-01 to Q-03 of the evaluation set". Expected: the labeled set's demo step 3 questions
    // that are not refusals, each with its question, its markers without the brackets, and its words
    @Test
    void theScriptedQuestionsAreTheLabeledSetsDemoQuestionsWithTheirLabels() {
        List<ScriptedAnswers.Label> expected = new ArrayList<>();
        for (JsonNode question : Fixtures.json("eval/questions.json").required("questions")) {
            if (question.path("notes").asString("").startsWith("demo step 3")
                    && !question.required("refusal").asBoolean()) {
                expected.add(new ScriptedAnswers.Label(question.required("id").asString(),
                        question.required("question").asString(),
                        question.required("expectedMarkers").valueStream()
                                .map(marker -> marker.asString().substring(2, marker.asString().length() - 2))
                                .toList(),
                        question.required("answerContains").valueStream().map(JsonNode::asString).toList()));
            }
        }

        assertThat(expected).extracting(ScriptedAnswers.Label::id).containsExactly("Q-01", "Q-02", "Q-03");
        assertThat(scripted.labels()).containsExactlyElementsOf(expected);
    }

    // A question is scripted by its exact text. Expected: Q-01's text finds Q-01; the rate question, which never calls
    // the model, and Q-01 with a word added find nothing
    @Test
    void onlyTheExactTextOfAScriptedQuestionIsScripted() {
        String referral = Fixtures.json("eval/questions.json").required("questions").get(0).required("question")
                .asString();

        assertThat(scripted.labelOf(referral)).map(ScriptedAnswers.Label::id).contains("Q-01");
        assertThat(scripted.labelOf("מהי הריבית המקסימלית שהבנק רשאי לגבות?")).isEmpty();
        assertThat(scripted.labelOf(referral + " בבקשה")).isEmpty();
    }

    // Q-02's label: [[sim:*]] and [[r:R-900]] cited, "מאושר" said. Expected: met by the answer the live run recorded;
    // missed without R-900, without the word, or with a decision where the simulation should be
    @Test
    void aLabelIsMetOnlyWithEveryMarkerCitedAndEveryWordSaid() {
        ScriptedAnswers.Label guarantor = scripted.labels().get(1);
        String approved = "כן. בסימולציה של בקשה 17 עם ערב, הבקשה הייתה מאושרת לפי כלל R-900.";
        List<String> cited = List.of("sim:d17:has_guarantor=true", "r:R-900", "p:9");

        assertThat(scripted.accepts(guarantor, approved, cited)).isTrue();
        assertThat(scripted.accepts(guarantor, approved, List.of("sim:d17:has_guarantor=true", "p:9"))).isFalse();
        assertThat(scripted.accepts(guarantor, "כן, לפי כלל R-900.", cited)).isFalse();
        assertThat(scripted.accepts(guarantor, approved, List.of("d:17", "r:R-900"))).isFalse();
    }

    // Document 4, Words that name an outcome: Q-02's live answers of 2026-09-24 cited the simulation, R-900 and
    // paragraph 9 but said "אושרה", and none was kept. Expected: such an answer meets the label; the same answer
    // denying the approval does not
    @Test
    void anAnswerThatSaysAnotherFormOfItsOutcomeMeetsTheLabel() {
        ScriptedAnswers.Label guarantor = scripted.labels().get(1);
        List<String> cited = List.of("sim:d17:has_guarantor=true", "r:R-900", "p:9");

        assertThat(scripted.accepts(guarantor, "כן. בסימולציה עם ערב, בקשה 17 אושרה לפי R-900.", cited)).isTrue();
        assertThat(scripted.accepts(guarantor, "לא. גם בסימולציה עם ערב, בקשה 17 לא אושרה.", cited)).isFalse();
    }
}
