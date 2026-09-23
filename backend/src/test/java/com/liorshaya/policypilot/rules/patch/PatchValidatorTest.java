package com.liorshaya.policypilot.rules.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.liorshaya.policypilot.rules.validation.Finding;
import com.liorshaya.policypilot.rules.validation.ValidationCode;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The three steps of Document 3's Patch validation and where each finding is reported: the steps run in order and stop
 * at the first that fails, and a finding on a patched rule or an added field is at its patch. The findings expected
 * of the patched copies are the reference implementation's (reference_check.py, validate after apply_patches).
 */
@Requirement({"FR-3", "FR-17"})
class PatchValidatorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final PatchValidator VALIDATOR = new PatchValidator();

    // An answer with a schema error and a refusal: the schema runs first, and the refusal waits for the repaired one
    @Test
    void aSchemaErrorStopsBeforeTheProposalValidator() {
        ObjectNode answer = ChangeRequests.rt04Answer();
        ((ObjectNode) answer.required("patches").get(0).required("rule")).put("id", "RULE1");

        PatchValidation validation = validate(answer, ChangeRequests.rt04());

        assertThat(validation.findings()).extracting(Finding::code).containsExactly(ValidationCode.DSL_SCHEMA);
        assertThat(validation.problems()).isEmpty();
        assertThat(validation.patched()).isNull();
    }

    // RT-04's answer with a patch the application would refuse too: the refusals alone are reported, nothing applied
    @Test
    void aRefusalStopsBeforeTheApplication() {
        ObjectNode answer = ChangeRequests.rt04Answer();
        ((ArrayNode) answer.required("patches")).addObject().put("op", "remove").put("ruleId", "R-999")
                .put("rationale", "כלל שאינו קיים");

        PatchValidation validation = validate(answer, ChangeRequests.rt04());

        assertThat(validation.problems()).allMatch(problem -> problem.code().refuses()).hasSize(12);
        assertThat(validation.refused()).isTrue();
        assertThat(validation.valid()).isFalse();
        assertThat(validation.patched()).isNull();
    }

    // A replace whose rule carries another id: the application refuses it, and the copy is never validated
    @Test
    void anApplicationProblemStopsBeforeTheCopyIsValidated() {
        ObjectNode answer = ChangeRequests.scriptedPatches();
        ((ObjectNode) answer.required("patches").get(0).required("rule")).put("id", "R-171");

        PatchValidation validation = validate(answer, ChangeRequests.scripted());

        assertThat(validation.problems()).extracting(PatchProblem::code).containsExactly(PatchCode.PATCH_ID_CHANGED);
        assertThat(validation.findings()).isEmpty();
        assertThat(validation.refused()).isFalse();
        assertThat(validation.valid()).isFalse();
    }

    // R-020 replaced at priority 250, after R-200 reads what it derives. Reference: DERIVED_ORDER on R-200 and
    // PRIORITY_BAND_UNUSUAL on R-020. R-020 is patch 0's rule, so its finding moves to the patch; R-200 is untouched
    // and keeps its place in the copy
    @Test
    void aFindingOnAPatchedRuleIsAtItsPatchAndOneOnAnUntouchedRuleStaysInTheCopy() {
        ObjectNode r020 = ruleOf("R-020");
        ObjectNode replace = JSON.createObjectNode().put("op", "replace").put("ruleId", "R-020")
                .put("rationale", "העברה");
        replace.set("rule", r020.put("priority", 250));

        PatchValidation validation = validate(withPatches(replace), ChangeRequests.scripted());

        assertThat(validation.findings()).extracting(Finding::code, Finding::path)
                .containsExactlyInAnyOrder(tuple(ValidationCode.DERIVED_ORDER, "/rules/12"),
                        tuple(ValidationCode.PRIORITY_BAND_UNUSUAL, "/patches/0/rule/priority"));
    }

    // R-200 replaced at priority 15, before R-020 derives what it reads. Reference: DERIVED_ORDER and
    // PRIORITY_BAND_UNUSUAL, both on R-200, patch 0's rule: the first is on the rule itself, the second on its priority
    @Test
    void aFindingOnAWholePatchedRuleIsAtThePatchsRule() {
        ObjectNode r200 = ruleOf("R-200").put("priority", 15);
        ObjectNode replace = JSON.createObjectNode().put("op", "replace").put("ruleId", "R-200")
                .put("rationale", "העברה");
        replace.set("rule", r200);

        assertThat(validate(withPatches(replace), ChangeRequests.scripted()).findings())
                .extracting(Finding::code, Finding::path)
                .containsExactlyInAnyOrder(tuple(ValidationCode.DERIVED_ORDER, "/patches/0/rule"),
                        tuple(ValidationCode.PRIORITY_BAND_UNUSUAL, "/patches/0/rule/priority"));
    }

    // R-020 removed, as a request that names it asks. Reference: FIELD_UNUSED for existing_monthly_debt and
    // DERIVED_NEVER_SET for debt_to_income; neither field is a patch's, so both keep their place in the copy
    @Test
    void aFindingOnAnUntouchedFieldStaysInTheCopy() {
        ObjectNode remove = JSON.createObjectNode().put("op", "remove").put("ruleId", "R-020")
                .put("rationale", "החישוב בוטל");

        assertThat(validate(withPatches(remove), "Remove R-020").findings()).extracting(Finding::code, Finding::path)
                .containsExactlyInAnyOrder(tuple(ValidationCode.FIELD_UNUSED, "/fields/6"),
                        tuple(ValidationCode.DERIVED_NEVER_SET, "/fields/10"));
    }

    // R-900 removed, as a request that names it asks. Reference: NO_TERMINAL_APPROVE, on the rules as a whole
    @Test
    void aFindingOnTheWholeRuleListKeepsItsPath() {
        ObjectNode remove = JSON.createObjectNode().put("op", "remove").put("ruleId", "R-900")
                .put("rationale", "אין אישור אוטומטי");
        Set<String> candidates = new TreeSet<>(ChangeRequests.expectedCandidates("CR-1"));
        candidates.add("R-900");

        assertThat(VALIDATOR.validate(withPatches(remove), Fixtures.lendingV1(), Fixtures.lendingParagraphs(),
                new PatchValidator.Scope("Remove R-900", candidates, Set.of())).findings())
                .extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.NO_TERMINAL_APPROVE, "/rules"));
    }

    // An added case field no rule reads. Reference: FIELD_UNUSED, a warning, so the proposal is still valid
    @Test
    void aFindingOnAnAddedFieldIsAtItsPatchAndAWarningLeavesTheProposalValid() {
        ObjectNode addField = JSON.createObjectNode().put("op", "add_field").put("rationale", "שדה חדש");
        addField.putObject("field").put("name", "bonus_income").put("type", "number");

        PatchValidation validation = validate(withPatches(addField), ChangeRequests.scripted());

        assertThat(validation.findings()).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.FIELD_UNUSED, "/patches/0/field"));
        assertThat(validation.valid()).isTrue();
    }

    // A remove the request asked for by id: the rule leaves the copy, and its id is what the new version retires
    @Test
    void aValidRemoveRetiresItsId() {
        ObjectNode remove = JSON.createObjectNode().put("op", "remove").put("ruleId", "R-410")
                .put("rationale", "רצועת הסימון בוטלה");

        PatchValidation validation = validate(withPatches(remove), "Remove R-410");

        assertThat(validation.valid()).isTrue();
        assertThat(validation.retiredIds()).containsExactly("R-410");
        assertThat(validation.modelRuleIds()).isEmpty();
    }

    private static PatchValidation validate(ObjectNode answer, String request) {
        return VALIDATOR.validate(answer, Fixtures.lendingV1(), Fixtures.lendingParagraphs(),
                new PatchValidator.Scope(request, ChangeRequests.expectedCandidates("CR-1"), Set.of()));
    }

    private static ObjectNode ruleOf(String id) {
        for (var rule : Fixtures.lendingV1().required("rules")) {
            if (rule.required("id").asString().equals(id)) {
                return (ObjectNode) rule.deepCopy();
            }
        }
        throw new IllegalArgumentException(id);
    }

    private static ObjectNode withPatches(ObjectNode... patches) {
        ObjectNode answer = ChangeRequests.scriptedPatches();
        ArrayNode list = ((ArrayNode) answer.required("patches")).removeAll();
        for (ObjectNode patch : patches) {
            list.add(patch);
        }
        return answer;
    }
}
