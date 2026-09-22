package com.liorshaya.policypilot.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.ruleset.service.FindingKind;
import com.liorshaya.policypilot.ruleset.service.ReviewFinding;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.SeededDrafts;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The review prompt's recorded answers, replayed through the whole pipeline (Document 6, Recorded level; Work Plan
 * day 10: "a review recording per kind, including injection"). The recordings are the first live run of review/v1,
 * made by LiveReviewRecordingIT; every expected value is a seeded finding of the labeled set, found the way
 * fixtures/README.md defines it (the same kind and an overlapping anchor).
 */
class RecordedReviewTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path RECORDINGS = Path.of("..", "fixtures", "eval", "recordings", "openai");
    /** The canonical author/v1 recording of the lending policy: the draft step 1 is tested with. */
    private static final String DEMO_DRAFT =
            "eval/recordings/openai/author/v1/45e240b93e8f90a34f4c59cdbbd6217584198acf143358e43e66df2ce7cf2d3a.json";

    private final ReviewService reviews = new ReviewService(RecordedGateway.replaying(RECORDINGS),
            new PromptRegistry(PromptRegistry.PROMPTS, Map.of()));

    private List<ReviewFinding> reviewOf(String slug) {
        return reviews.review(SeededDrafts.policy(slug), slug, SeededDrafts.language(slug), SeededDrafts.draft(slug))
                .review().findings();
    }

    // Work Plan day 10, Done when: "step 1 shows the ambiguity and the conflict". Expected: SF-1 (the undefined
    // "stable income", paragraph 4) and SF-2 (age 70 against retirees to 75, paragraphs 1 and 8) of
    // fixtures/eval/policies/consumer-lending/seeded.findings.json
    @Test
    void theDemoDraftsReviewShowsTheAmbiguityAndTheConflictOfStepOne() {
        JsonNode draft = JSON.readTree(Fixtures.json(DEMO_DRAFT).required("response").asString());
        PolicyVersionRef lending = SeededDrafts.policy("consumer-lending");

        List<ReviewFinding> findings = reviews.review(lending, "מדיניות אשראי צרכני - הלוואות אישיות", "he", draft)
                .review().findings();

        List<JsonNode> seeded = SeededDrafts.seeded("consumer-lending");
        assertThat(SeededDrafts.found(seeded.get(0), findings)).as("SF-1 ambiguity").isTrue();
        assertThat(SeededDrafts.found(seeded.get(1), findings)).as("SF-2 conflict").isTrue();
    }

    // Work Plan day 10: a review recording per kind. Expected: synthetic-enums seeds all six kinds (SF-1 to SF-6),
    // and each is found
    @Test
    void theRecordingOfSyntheticEnumsFindsEverySeededKind() {
        List<ReviewFinding> findings = reviewOf("synthetic-enums");

        assertThat(SeededDrafts.seeded("synthetic-enums"))
                .allSatisfy(seeded -> assertThat(SeededDrafts.found(seeded, findings))
                        .as(seeded.required("id").asString()).isTrue());
        assertThat(findings).extracting(ReviewFinding::kind).contains(FindingKind.values());
    }

    // Document 5, RT-05's premise: a passage that addresses the system is reported. Expected: the injection seeded in
    // both policies that plant one, synthetic-enums SF-3 (paragraph 8) and synthetic-derived-chain SF-1 (paragraph 6)
    @Test
    void anInstructionToTheSystemInsideAPolicyIsReportedAsInjection() {
        assertThat(reviewOf("synthetic-enums")).anySatisfy(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.INJECTION);
            assertThat(finding.paragraphIndexes()).contains(8);
        });
        assertThat(reviewOf("synthetic-derived-chain")).anySatisfy(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.INJECTION);
            assertThat(finding.paragraphIndexes()).contains(6);
        });
    }
}
