package com.liorshaya.policypilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Document 4: "Citation accuracy --- answers whose markers are all valid and include the expected source". The
 * questions and their expected markers are the labeled set's (fixtures/eval/questions.json) and the version is
 * the lending fixture, so nothing below is an expectation this code invented.
 *
 * <p>A live pass can go entirely clean, as the first one did, and then a metric that counted everything would
 * score the same as one that worked. These are the answers that must not be counted.
 */
class CitationScoringTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RuleSet LENDING = new RuleSetMapper().toRuleSet(Fixtures.lendingV1());
    private static final int PARAGRAPHS = 9;

    // Q-03 expects the marker [[p:2]] (fixtures/eval/questions.json; its expectedChunks are a different thing,
    // what retrieval must return). Expected: an answer citing it, and the rule beside it, counts
    @Test
    void anAnswerCitingWhatTheQuestionExpectsIsCounted() {
        CitationScoring.Scored scored = score("Q-03", "תקופת ההחזר המקסימלית היא 84 חודשים.[[p:2]][[r:R-130]]");

        assertThat(scored.counted()).isTrue();
    }

    // Document 4: "include the expected source". Q-01 expects [[d:17]] and [[p:7]]. Expected: an answer that
    // fetched the decision and cited it, but never cited the paragraph, is not counted, and the reason says which
    @Test
    void anAnswerMissingTheExpectedSourceIsNotCounted() {
        JsonNode steps = JSON.createArrayNode().add(JSON.createObjectNode()
                .put("tool", "getDecision").put("arguments", "{\"applicationNumber\":17}"));

        CitationScoring.Scored scored = CitationScoring.score(labeled("Q-01"),
                "בקשה 17 הופנתה לבדיקה.[[d:17]]", steps, LENDING, PARAGRAPHS);

        assertThat(scored.counted()).isFalse();
        assertThat(scored.note()).contains("[[p:7]]");
    }

    // Document 4: "all valid". Expected: R-999 is not a rule of the lending version, so the answer is not counted
    // however right the rest of it is
    @Test
    void anAnswerCitingARuleTheVersionDoesNotHaveIsNotCounted() {
        CitationScoring.Scored scored =
                score("Q-03", "תקופת ההחזר המקסימלית היא 84 חודשים.[[p:2]][[r:R-130]][[r:R-999]]");

        assertThat(scored.counted()).isFalse();
        assertThat(scored.note()).contains("r:R-999").contains("cannot supply");
    }

    // The policy has nine paragraphs. Expected: a tenth is not a source, so the answer is not counted
    @Test
    void anAnswerCitingAParagraphThePolicyDoesNotHaveIsNotCounted() {
        CitationScoring.Scored scored = score("Q-03", "84 חודשים.[[p:2]][[r:R-130]][[p:10]]");

        assertThat(scored.counted()).isFalse();
        assertThat(scored.note()).contains("p:10");
    }

    // RT-03: an answer may not cite a decision it never fetched. Expected: with no tool call in the recording,
    // [[d:17]] is not a source this turn could supply
    @Test
    void anAnswerCitingADecisionNoToolReturnedIsNotCounted() {
        CitationScoring.Scored scored = score("Q-01", "בקשה 17 הופנתה לבדיקה.[[d:17]][[p:7]]");

        assertThat(scored.counted()).isFalse();
        assertThat(scored.note()).contains("d:17");
    }

    // Expected: the same answer counts once getDecision has actually returned application 17
    @Test
    void aDecisionAToolReturnedIsASourceTheAnswerMayCite() {
        JsonNode steps = JSON.createArrayNode().add(JSON.createObjectNode()
                .put("tool", "getDecision").put("arguments", "{\"applicationNumber\":17}"));

        CitationScoring.Scored scored = CitationScoring.score(labeled("Q-01"),
                "בקשה 17 הופנתה לבדיקה.[[d:17]][[p:7]]", steps, LENDING, PARAGRAPHS);

        assertThat(scored.counted()).isTrue();
    }

    // The labeled set writes [[sim:*]] for "any simulation marker". Expected: Q-02's simulation, whatever its
    // overrides spell, satisfies it, and the other expected marker is still required
    @Test
    void anySimulationMarkerSatisfiesTheLabelsWildcard() {
        JsonNode steps = JSON.createArrayNode().add(JSON.createObjectNode().put("tool", "simulate")
                .put("arguments", "{\"applicationNumber\":17,\"overrides\":{\"has_guarantor\":true}}"));

        CitationScoring.Scored counted = CitationScoring.score(labeled("Q-02"),
                "כן.[[sim:d17:has_guarantor=true]][[r:R-900]]", steps, LENDING, PARAGRAPHS);
        CitationScoring.Scored without = CitationScoring.score(labeled("Q-02"),
                "כן.[[r:R-900]]", steps, LENDING, PARAGRAPHS);

        assertThat(counted.counted()).isTrue();
        assertThat(without.counted()).isFalse();
        assertThat(without.note()).contains("[[sim:*]]");
    }

    // Expected: an answer that cited nothing at all is not counted, and says so rather than passing quietly
    @Test
    void anAnswerThatCitedNothingIsNotCounted() {
        CitationScoring.Scored scored = score("Q-03", "תקופת ההחזר המקסימלית היא 84 חודשים.");

        assertThat(scored.counted()).isFalse();
    }

    private static CitationScoring.Scored score(String questionId, String answer) {
        return CitationScoring.score(labeled(questionId), answer, JSON.createArrayNode(), LENDING, PARAGRAPHS);
    }

    private static JsonNode labeled(String questionId) {
        return Fixtures.json("eval/questions.json").required("questions").valueStream()
                .filter(question -> questionId.equals(question.path("id").asString("")))
                .findFirst().orElseThrow();
    }
}
