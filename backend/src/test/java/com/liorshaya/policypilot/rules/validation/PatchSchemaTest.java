package com.liorshaya.policypilot.rules.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The schema step of Patch validation (Document 3; Work Plan day 12: "patch validation through $ref"). The rule, the
 * field and the defaults of a patch are the rule set's own definitions, so each test breaks one of them the way the
 * conformance suite's schema-level rejections break a rule set, and expects the same refusal inside the patch.
 */
@Requirement({"FR-3", "FR-17"})
class PatchSchemaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PatchSchemaValidator validator = new PatchSchemaValidator();

    // The expected answer to the scripted request, change-request-1.json, is a Patches object the schema accepts
    @Test
    void theScriptedProposalValidates() {
        assertThat(validator.validate(ChangeRequests.scriptedPatches())).isEmpty();
    }

    // Document 3, schema-level rejections: "a rule id RULE1". Expected: DSL_SCHEMA at the patched rule's id, which only
    // the rule definition reached by $ref can report
    @Test
    void aPatchedRuleIsCheckedByTheRuleSchemaThroughItsRef() {
        ObjectNode patches = ChangeRequests.scriptedPatches();
        rule(patches, 0).put("id", "RULE1");

        assertThat(validator.validate(patches)).extracting(Finding::code, Finding::path, Finding::ruleIds)
                .containsExactly(tuple(ValidationCode.DSL_SCHEMA, "/patches/0/rule/id", List.of("R-170")));
    }

    // Document 3, schema-level rejections: "pending provenance without a rationale". Expected: DSL_SCHEMA at the
    // patched rule's provenance
    @Test
    void aPendingProvenanceWithoutItsRationaleFailsThroughTheRef() {
        ObjectNode patches = ChangeRequests.scriptedPatches();
        ((ObjectNode) rule(patches, 1).required("provenance")).remove("rationale");

        assertThat(validator.validate(patches)).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.DSL_SCHEMA, "/patches/1/rule/provenance"));
    }

    // Document 3, schema-level rejections: "a field named Age". Expected: DSL_SCHEMA at the added field's name
    @Test
    void anAddedFieldIsCheckedByTheFieldSchemaThroughItsRef() {
        ObjectNode patches = withOnePatch(addField("Age"));

        assertThat(validator.validate(patches)).extracting(Finding::code, Finding::path, Finding::fieldNames)
                .containsExactly(tuple(ValidationCode.DSL_SCHEMA, "/patches/0/field/name", List.of("Age")));
    }

    // An error outside every patch: the Patches object without its summary. Expected: DSL_SCHEMA at the root, which
    // no rule or field anchors
    @Test
    void anErrorOutsideEveryPatchAnchorsNothing() {
        ObjectNode patches = ChangeRequests.scriptedPatches();
        patches.remove("summary");

        assertThat(validator.validate(patches)).extracting(Finding::path, Finding::ruleIds, Finding::fieldNames)
                .containsExactly(tuple("", List.of(), List.of()));
    }

    // Document 3, op table: replace carries its rule. Expected: DSL_SCHEMA at the patch that lacks it
    @Test
    void aReplaceWithoutItsRuleFailsTheSchema() {
        ObjectNode patches = ChangeRequests.scriptedPatches();
        ((ObjectNode) patches.required("patches").get(0)).remove("rule");

        assertThat(validator.validate(patches)).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.DSL_SCHEMA, "/patches/0"));
    }

    // Document 3, op table: remove carries only the rule id. Expected: DSL_SCHEMA at the rule it should not carry
    @Test
    void aRemoveThatCarriesARuleFailsTheSchema() {
        ObjectNode remove = (ObjectNode) ChangeRequests.scriptedPatches().required("patches").get(0).deepCopy();
        remove.put("op", "remove");

        assertThat(validator.validate(withOnePatch(remove))).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.DSL_SCHEMA, "/patches/0/rule"));
    }

    // Document 3, op table: five ops. Expected: the schema accepts one well-formed patch of each; refusing
    // set_defaults is the proposal validator's work, not the schema's
    @Test
    void everyOpOfDocument3HasAnAcceptedShape() {
        ObjectNode patches = ChangeRequests.scriptedPatches();
        ArrayNode list = (ArrayNode) patches.required("patches");
        ObjectNode added = rule(patches, 0).deepCopy().put("id", "R-180").put("priority", 180);
        list.addObject().put("op", "add").put("ruleId", "R-180").put("rationale", "כלל חדש").set("rule", added);
        list.addObject().put("op", "remove").put("ruleId", "R-140").put("rationale", "הכלל בוטל");
        list.add(addField("bonus_income"));
        ObjectNode defaults = list.addObject().put("op", "set_defaults").put("rationale", "ברירת מחדל");
        defaults.set("defaults", Fixtures.lendingV1().required("defaults"));

        assertThat(validator.validate(patches)).isEmpty();
    }

    private static ObjectNode rule(JsonNode patches, int index) {
        return (ObjectNode) patches.required("patches").get(index).required("rule");
    }

    private static ObjectNode addField(String name) {
        ObjectNode patch = JSON.createObjectNode().put("op", "add_field").put("rationale", "שדה חדש");
        patch.putObject("field").put("name", name).put("type", "number");
        return patch;
    }

    private static ObjectNode withOnePatch(ObjectNode patch) {
        ObjectNode patches = ChangeRequests.scriptedPatches();
        ((ArrayNode) patches.required("patches")).removeAll().add(patch);
        return patches;
    }
}
