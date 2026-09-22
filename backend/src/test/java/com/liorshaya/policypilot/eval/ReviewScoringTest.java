package com.liorshaya.policypilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.Fixtures;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

/**
 * Document 4: reviewer recall counts "seeded defects found with the right kind and an overlapping anchor". The
 * seeded defects are the lending policy's own {@code seeded.findings.json} --- SF-1, the undefined "stable
 * income" of paragraph 4, and SF-2, the conflict of paragraphs 1 and 8 --- and every expectation below is read
 * from that file, never from a review answer this code produced.
 */
class ReviewScoringTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNode SEEDED =
            Fixtures.json("eval/policies/consumer-lending/seeded.findings.json");

    // Document 4: "the right kind and an overlapping anchor". Expected: a finding of the seeded kind naming one
    // of its paragraphs catches it, and every seeded defect answered gives full recall
    @Test
    void aFindingOfTheRightKindOnOneOfTheSeededParagraphsCatchesIt() {
        ReviewScoring.Result result = ReviewScoring.score(SEEDED, findingsForEverySeededDefect());

        assertThat(result.recall().value()).isEqualTo(1.0);
        assertThat(result.recall().denominator()).isEqualTo(SEEDED.required("findings").size());
        assertThat(result.caught()).allMatch(ReviewScoring.Caught::found);
    }

    // Document 4: "the right kind". Expected: the same anchors reported under another kind catch nothing, and the
    // reason names the kind that was wanted
    @Test
    void aFindingOfTheWrongKindOnTheRightParagraphCatchesNothing() {
        JsonNode defect = SEEDED.required("findings").get(0);
        JsonNode wrongKind = JSON.createArrayNode().add(JSON.createObjectNode()
                .put("kind", "duplicate")
                .set("paragraphIndexes", defect.required("paragraphIndexes").deepCopy()));

        ReviewScoring.Result result = ReviewScoring.score(SEEDED, wrongKind);

        assertThat(result.found()).isZero();
        assertThat(result.caught().getFirst().note())
                .contains("no finding of kind " + defect.required("kind").asString());
    }

    // Document 4: "an overlapping anchor", which a conflict may be described from either end of. Expected: SF-2
    // names paragraphs 1 and 8; a finding naming only paragraph 8 overlaps and catches it
    @Test
    void onlyOneOfTheSeededParagraphsIsEnoughToOverlap() {
        JsonNode conflict = seededWithTwoParagraphs();
        JsonNode oneEnd = JSON.createArrayNode().add(JSON.createObjectNode()
                .put("kind", conflict.required("kind").asString())
                .set("paragraphIndexes", JSON.createArrayNode()
                        .add(conflict.required("paragraphIndexes").get(1).asInt())));

        ReviewScoring.Result result = ReviewScoring.score(onlyDefect(conflict), oneEnd);

        assertThat(result.found()).isEqualTo(1);
    }

    // Document 4: an overlapping anchor, which may be a rule rather than a paragraph. Expected: a finding naming
    // one of the seeded rule ids and no paragraph at all still catches it
    @Test
    void aRuleIdIsAnAnchorTheSameWayAParagraphIs() {
        JsonNode defect = SEEDED.required("findings").get(0);
        JsonNode byRule = JSON.createArrayNode().add(JSON.createObjectNode()
                .put("kind", defect.required("kind").asString())
                .set("ruleIds", defect.required("ruleIds").deepCopy()));

        ReviewScoring.Result result = ReviewScoring.score(onlyDefect(defect), byRule);

        assertThat(result.found()).isEqualTo(1);
    }

    // Document 4: precision is "findings that are seeded ... / all findings". Expected: one seeded and two others
    // give a lower bound of one in three, and recall is unaffected by the extra findings
    @Test
    void precisionCountsTheFindingsThatAnswerASeededDefectOverAllOfThem() {
        JsonNode defect = SEEDED.required("findings").get(0);
        JsonNode findings = JSON.createArrayNode()
                .add(JSON.createObjectNode().put("kind", defect.required("kind").asString())
                        .set("paragraphIndexes", defect.required("paragraphIndexes").deepCopy()))
                .add(JSON.createObjectNode().put("kind", "gap")
                        .set("paragraphIndexes", JSON.createArrayNode().add(99)))
                .add(JSON.createObjectNode().put("kind", "ambiguity")
                        .set("paragraphIndexes", JSON.createArrayNode().add(98)));

        ReviewScoring.Result result = ReviewScoring.score(onlyDefect(defect), findings);

        assertThat(result.precisionLowerBound().numerator()).isEqualTo(1);
        assertThat(result.precisionLowerBound().denominator()).isEqualTo(3);
        assertThat(result.recall().value()).isEqualTo(1.0);
    }

    // Two seeded defects must not be answered by one finding, or a reviewer that reported a single vague finding
    // would score full recall. Expected: one finding catches one of them, and the other is still missing
    @Test
    void oneFindingAnswersAtMostOneSeededDefect() {
        JsonNode defect = SEEDED.required("findings").get(0);
        var twice = JSON.createObjectNode();
        twice.putArray("findings").add(defect.deepCopy()).add(defect.deepCopy());
        JsonNode single = JSON.createArrayNode().add(JSON.createObjectNode()
                .put("kind", defect.required("kind").asString())
                .set("paragraphIndexes", defect.required("paragraphIndexes").deepCopy()));

        ReviewScoring.Result result = ReviewScoring.score(twice, single);

        assertThat(result.found()).isEqualTo(1);
        assertThat(result.recall().value()).isEqualTo(0.5);
    }

    /** One review answer that reports every seeded defect exactly as the fixture describes it. */
    private static JsonNode findingsForEverySeededDefect() {
        var findings = JSON.createArrayNode();
        SEEDED.required("findings").forEach(defect -> findings.add(JSON.createObjectNode()
                .put("kind", defect.required("kind").asString())
                .set("paragraphIndexes", defect.required("paragraphIndexes").deepCopy())));
        return findings;
    }

    private static JsonNode seededWithTwoParagraphs() {
        return SEEDED.required("findings").valueStream()
                .filter(defect -> defect.required("paragraphIndexes").size() > 1)
                .findFirst().orElseThrow();
    }

    private static JsonNode onlyDefect(JsonNode defect) {
        var one = JSON.createObjectNode();
        one.putArray("findings").add(defect.deepCopy());
        return one;
    }
}
