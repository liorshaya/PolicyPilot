package com.liorshaya.policypilot.rules.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.liorshaya.policypilot.rules.validation.Finding;
import com.liorshaya.policypilot.rules.validation.RuleSetValidator;
import com.liorshaya.policypilot.rules.validation.ValidationCode;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The CHANGE_PROPOSAL context on a patched set (Work Plan day 12: "pending accepted, analyst-from-model rejected,
 * untouched analyst rule allowed"). The difference from the context's own tests is where the model's rules come from:
 * here they are the rules the patches wrote, not ids a test types. Every expected finding is the reference
 * implementation's for the same patched document (reference_check.py, validate after apply_patches), re-anchored at
 * its patch as Document 3's Patch validation says.
 */
@Requirement({"FR-4", "FR-17", "FR-19"})
class ProposalProvenanceTest {

    private static final PatchValidator VALIDATOR = new PatchValidator();

    // The scripted patches carry pending provenance on R-170 and R-410. Reference: no finding
    @Test
    void pendingOnTheScriptedPatchesIsAccepted() {
        PatchValidation validation = validate(ChangeRequests.scriptedPatches());

        assertThat(validation.findings()).isEmpty();
        assertThat(validation.valid()).isTrue();
        assertThat(validation.modelRuleIds()).containsExactlyInAnyOrder("R-170", "R-410");
    }

    // R-410 is analyst in ruleset.v1.json; a patch that keeps that provenance claims a person wrote the model's rule.
    // Reference: PROVENANCE_ANALYST_FROM_MODEL at /rules/17/provenance, which is patch 1's rule
    @Test
    void aPatchedRuleKeepingItsAnalystProvenanceIsRefusedAtItsPatch() {
        ObjectNode answer = ChangeRequests.scriptedPatches();
        ((ObjectNode) answer.required("patches").get(1).required("rule"))
                .set("provenance", ruleOf(Fixtures.lendingV1(), "R-410").required("provenance"));

        PatchValidation validation = validate(answer);

        assertThat(validation.findings()).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.PROVENANCE_ANALYST_FROM_MODEL, "/patches/1/rule/provenance"));
        assertThat(validation.valid()).isFalse();
        assertThat(validation.refused()).isFalse();
    }

    // R-310 keeps the analyst provenance of ruleset.v1.json in the patched copy, and nothing is said about it
    @Test
    void theUntouchedAnalystRuleR310IsAllowed() {
        PatchValidation validation = validate(ChangeRequests.scriptedPatches());

        assertThat(ruleOf(validation.patched(), "R-310").required("provenance").required("kind").asString())
                .isEqualTo("analyst");
        assertThat(validation.findings()).noneMatch(finding -> finding.ruleIds().contains("R-310"));
    }

    // Approval is what turns pending into analyst: the same copy submitted for publishing is refused. Reference:
    // PROVENANCE_PENDING_AT_PUBLISH at /rules/11/provenance (R-170) and /rules/17/provenance (R-410)
    @Test
    void theSameCopyCannotBePublishedWhilePendingRemains() {
        ObjectNode patched = validate(ChangeRequests.scriptedPatches()).patched();

        assertThat(new RuleSetValidator().validate(patched, ValidationContext.PUBLISH, Fixtures.lendingParagraphs(),
                Set.of()).findings()).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.PROVENANCE_PENDING_AT_PUBLISH, "/rules/11/provenance"),
                        tuple(ValidationCode.PROVENANCE_PENDING_AT_PUBLISH, "/rules/17/provenance"));
    }

    private static PatchValidation validate(ObjectNode answer) {
        return VALIDATOR.validate(answer, Fixtures.lendingV1(), Fixtures.lendingParagraphs(),
                new PatchValidator.Scope(ChangeRequests.scripted(), ChangeRequests.expectedCandidates("CR-1"),
                        Set.of()));
    }

    private static JsonNode ruleOf(JsonNode document, String id) {
        for (JsonNode rule : document.required("rules")) {
            if (rule.required("id").asString().equals(id)) {
                return rule;
            }
        }
        throw new IllegalArgumentException(id);
    }
}
