package com.liorshaya.policypilot.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ruleset.service.FindingKind;
import com.liorshaya.policypilot.ruleset.service.ReviewFinding;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The evaluation drafts are built the way the reference builds them (fixtures/reference/reference_check.py,
 * apply_mutation). Expected values: the mutations in each policy's seeded.findings.json.
 */
class SeededDraftsTest {

    private static JsonNode rule(JsonNode draft, String id) {
        for (JsonNode rule : draft.path("rules")) {
            if (id.equals(rule.path("id").asString(""))) {
                return rule;
            }
        }
        return null;
    }

    // consumer-lending: SF-3 sets R-170's threshold to 8500, SF-4 removes R-160, SF-5 duplicates R-100 as R-101
    @Test
    void theLendingDraftCarriesEveryPlantedMutation() {
        JsonNode draft = SeededDrafts.draft("consumer-lending");

        assertThat(rule(draft, "R-170").path("condition").path("value").asInt()).isEqualTo(8500);
        assertThat(rule(draft, "R-160")).isNull();
        assertThat(rule(draft, "R-101")).isNotNull();
        assertThat(rule(draft, "R-101").path("condition")).isEqualTo(rule(draft, "R-100").path("condition"));
    }

    // synthetic-enums SF-4: ["condition", "all", 1, "value"] set to 45, a path through an array
    @Test
    void aMutationPathStepsThroughArraysByIndex() {
        JsonNode draft = SeededDrafts.draft("synthetic-enums");

        assertThat(rule(draft, "R-130").path("condition").path("all").get(1).path("value").asInt()).isEqualTo(45);
    }

    // every labeled policy builds, so the live run and the day 11 runner can use each one
    @Test
    void everyLabeledPolicyHasADraft() {
        assertThat(Fixtures.evaluationPolicies()).hasSize(18)
                .allSatisfy(slug -> assertThat(SeededDrafts.draft(slug).path("rules").size()).isPositive());
    }

    // fixtures/README.md: found = the same kind and an overlapping anchor (a paragraph or a rule id)
    @Test
    void aDefectIsFoundByTheSameKindAndAnOverlappingAnchor() {
        JsonNode conflict = SeededDrafts.seeded("consumer-lending").get(1);

        assertThat(SeededDrafts.found(conflict, List.of(finding(FindingKind.CONFLICT, List.of(), List.of(8)))))
                .isTrue();
        assertThat(SeededDrafts.found(conflict, List.of(finding(FindingKind.CONFLICT, List.of("R-110"), List.of()))))
                .isTrue();
        assertThat(SeededDrafts.found(conflict, List.of(finding(FindingKind.AMBIGUITY, List.of(), List.of(8)))))
                .isFalse();
        assertThat(SeededDrafts.found(conflict, List.of(finding(FindingKind.CONFLICT, List.of("R-120"), List.of(2)))))
                .isFalse();
    }

    private static ReviewFinding finding(FindingKind kind, List<String> rules, List<Integer> paragraphs) {
        return new ReviewFinding("F-1", kind, rules, paragraphs, "m", "s", 0.5, null);
    }
}
