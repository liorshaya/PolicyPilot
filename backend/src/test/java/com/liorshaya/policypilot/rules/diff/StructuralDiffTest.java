package com.liorshaya.policypilot.rules.diff;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The structural diff of Document 3 (Change Patches, Diff and Versioning): fields by name, rules by id, the defaults
 * as a whole, a rule's condition by its leaves. The versions are the fixtures' own: ruleset.v1.json with the patches
 * change-request-1.json and fixtures/eval/changes.json expect, so every expected change is read off two documents
 * nobody wrote for this test.
 */
@Requirement({"FR-18", "FR-20"})
class StructuralDiffTest {

    private static final RuleSetMapper MAPPER = new RuleSetMapper();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNode SCRIPTED =
            Fixtures.json("policies/consumer-lending/change-request-1.json").required("expected").required("patches");

    // Expected: nothing changed, and the JSON says so in the shape the audit entry stores
    @Test
    void aVersionAgainstItselfHasAnEmptyDiff() {
        StructuralDiff diff = diff(Fixtures.lendingV1(), Fixtures.lendingV1());

        assertThat(diff.isEmpty()).isTrue();
        assertThat(diff.toJson()).isEqualTo(JSON.readTree("""
                {"fields": {"added": [], "removed": [], "modified": []},
                 "rules": {"added": [], "removed": [], "modified": []},
                 "defaults": null}"""));
    }

    // Document 3: "the side-by-side view highlights condition.value: 8000 -> 9000 rather than the whole rule".
    // Expected: R-170's label, condition value, reason and provenance changed, in the DSL's order, both rules whole
    @Test
    void theScriptedPatchesChangeR170AtItsConditionLeaf() {
        ObjectNode after = withRules(Fixtures.lendingV1(), SCRIPTED.get(0).required("rule"),
                SCRIPTED.get(1).required("rule"));

        StructuralDiff diff = diff(Fixtures.lendingV1(), after);

        assertThat(diff.rules().modified()).extracting(StructuralDiff.Modified::key).containsExactly("R-170", "R-410");
        StructuralDiff.Modified r170 = diff.rules().modified().getFirst();
        assertThat(r170.changes()).extracting(StructuralDiff.Change::path)
                .containsExactly("/label", "/condition/value", "/actions", "/provenance");
        assertThat(r170.changes().get(1)).isEqualTo(new StructuralDiff.Change("/condition/value",
                JSON.readTree("8000"), JSON.readTree("9000")));
        assertThat(r170.from()).isEqualTo(rule(Fixtures.lendingV1(), "R-170"));
        assertThat(r170.to()).isEqualTo(SCRIPTED.get(0).required("rule"));
        assertThat(diff.rules().added()).isEmpty();
        assertThat(diff.rules().removed()).isEmpty();
        assertThat(diff.isEmpty()).isFalse();
    }

    // R-410's band moves from [8000, 9000] to [9000, 10000]. Expected: the two array leaves and the provenance, and
    // not the field or the operator, which did not change
    @Test
    void anArrayValueChangesByItsElements() {
        ObjectNode after = withRules(Fixtures.lendingV1(), SCRIPTED.get(1).required("rule"));

        StructuralDiff.Modified r410 = diff(Fixtures.lendingV1(), after).rules().modified().getFirst();

        assertThat(r410.changes()).containsExactly(
                new StructuralDiff.Change("/condition/value/0", JSON.readTree("8000"), JSON.readTree("9000")),
                new StructuralDiff.Change("/condition/value/1", JSON.readTree("9000"), JSON.readTree("10000")),
                new StructuralDiff.Change("/provenance", rule(Fixtures.lendingV1(), "R-410").required("provenance"),
                        SCRIPTED.get(1).required("rule").required("provenance")));
    }

    // CR-2 raises the longest term: R-130 is {"not": {"field": "term_months", "op": "between", "value": [12, 84]}}.
    // Expected: one condition change, at /condition/not/value/1, from 84 to 96
    @Test
    void aLeafInsideANotIsFoundByItsPointer() {
        JsonNode r130 = labeledPatch("CR-2").required("rule");

        StructuralDiff.Modified modified = diff(Fixtures.lendingV1(), withRules(Fixtures.lendingV1(), r130))
                .rules().modified().getFirst();

        assertThat(modified.changes()).filteredOn(change -> change.path().startsWith("/condition"))
                .containsExactly(new StructuralDiff.Change("/condition/not/value/1", JSON.readTree("84"),
                        JSON.readTree("96")));
    }

    // Document 3: a condition whose shape changed is one change where the two shapes part. Expected: the operator as
    // a leaf, and the value, an array become a number, whole at its own pointer
    @Test
    void whereTwoConditionsPartInShapeTheSubtreeIsOneChange() {
        ObjectNode r130 = (ObjectNode) rule(Fixtures.lendingV1(), "R-130").deepCopy();
        r130.set("condition", JSON.readTree("{\"not\": {\"field\": \"term_months\", \"op\": \"lt\", \"value\": 12}}"));

        StructuralDiff.Modified modified = diff(Fixtures.lendingV1(), withRules(Fixtures.lendingV1(), r130))
                .rules().modified().getFirst();

        assertThat(modified.changes()).containsExactly(
                new StructuralDiff.Change("/condition/not/op", JSON.readTree("\"between\""), JSON.readTree("\"lt\"")),
                new StructuralDiff.Change("/condition/not/value", JSON.readTree("[12, 84]"), JSON.readTree("12")));
    }

    // Expected: a comparison become a combinator is one change of the whole condition, and a list that grew is one
    // change of the whole list, since neither has leaves in common to compare
    @Test
    void aConditionOrAListOfAnotherShapeIsReplacedWhole() {
        ObjectNode combined = (ObjectNode) rule(Fixtures.lendingV1(), "R-170").deepCopy();
        combined.set("condition", JSON.readTree("""
                {"all": [{"field": "monthly_income", "op": "lt", "value": 8000},
                         {"field": "age", "op": "lt", "value": 21}]}"""));
        ObjectNode listed = (ObjectNode) rule(Fixtures.lendingV1(), "R-170").deepCopy();
        listed.set("condition", JSON.readTree("{\"field\": \"monthly_income\", \"op\": \"in\", \"value\": [8000]}"));
        ObjectNode longer = (ObjectNode) listed.deepCopy();
        longer.set("condition", JSON.readTree(
                "{\"field\": \"monthly_income\", \"op\": \"in\", \"value\": [8000, 9000]}"));

        StructuralDiff.Modified shape = diff(Fixtures.lendingV1(), withRules(Fixtures.lendingV1(), combined))
                .rules().modified().getFirst();
        StructuralDiff.Modified size = diff(withRules(Fixtures.lendingV1(), listed),
                withRules(Fixtures.lendingV1(), longer)).rules().modified().getFirst();

        assertThat(shape.changes()).containsExactly(new StructuralDiff.Change("/condition",
                rule(Fixtures.lendingV1(), "R-170").required("condition"), combined.required("condition")));
        assertThat(size.changes()).containsExactly(new StructuralDiff.Change("/condition/value",
                JSON.readTree("[8000]"), JSON.readTree("[8000, 9000]")));
    }

    // CR-3 adds R-180 to the English lending policy. Expected: the new rule whole, and nothing else changed
    @Test
    void anAddedRuleIsCarriedWhole() {
        ObjectNode base = expectedRuleSet("consumer-lending-en");
        ObjectNode after = base.deepCopy();
        JsonNode r180 = labeledPatch("CR-3").required("rule");
        ((ArrayNode) after.required("rules")).add(r180);

        StructuralDiff diff = diff(base, after);

        assertThat(diff.rules().added()).containsExactly(r180);
        assertThat(diff.rules().removed()).isEmpty();
        assertThat(diff.rules().modified()).isEmpty();
        assertThat(diff.isEmpty()).isFalse();
    }

    // CR-5 removes R-140 from the merit scholarship. Expected: the removed rule whole, as the base had it
    @Test
    void aRemovedRuleIsCarriedWhole() {
        ObjectNode base = expectedRuleSet("scholarship-merit");
        ObjectNode after = base.deepCopy();
        ((ArrayNode) after.required("rules")).removeIf(rule -> rule.required("id").asString().equals("R-140"));

        StructuralDiff diff = diff(base, after);

        assertThat(diff.rules().removed()).containsExactly(rule(base, "R-140"));
        assertThat(diff.rules().added()).isEmpty();
        assertThat(diff.isEmpty()).isFalse();
    }

    // Rules are compared by id, not by place. Expected: the same rules in reverse order are no change at all
    @Test
    void aRuleThatOnlyMovedIsNotChanged() {
        ObjectNode reversed = Fixtures.lendingV1();
        List<JsonNode> rules = new ArrayList<>(reversed.required("rules").valueStream().toList());
        Collections.reverse(rules);
        ((ArrayNode) reversed.required("rules")).removeAll().addAll(rules);

        assertThat(diff(Fixtures.lendingV1(), reversed).isEmpty()).isTrue();
    }

    // Fields are compared by name, their attributes whole. Expected: a new optional field added, has_guarantor
    // removed, and term_months's minimum changed from the fixture's to 6; the rules are untouched
    @Test
    void fieldsAreComparedByName() {
        ObjectNode after = Fixtures.lendingV1();
        ArrayNode fields = (ArrayNode) after.required("fields");
        JsonNode guarantor = field(after, "has_guarantor");
        fields.removeIf(field -> field.required("name").asString().equals("has_guarantor"));
        ((ObjectNode) field(after, "term_months")).put("minimum", 6);
        JsonNode bonus = JSON.readTree("{\"name\": \"bonus_income\", \"type\": \"number\", \"required\": false}");
        fields.add(bonus);

        StructuralDiff diff = diff(Fixtures.lendingV1(), after);

        assertThat(diff.fields().added()).containsExactly(bonus);
        assertThat(diff.fields().removed()).containsExactly(guarantor);
        assertThat(diff.fields().modified()).singleElement().satisfies(term -> {
            assertThat(term.key()).isEqualTo("term_months");
            assertThat(term.changes()).containsExactly(new StructuralDiff.Change("/minimum",
                    field(Fixtures.lendingV1(), "term_months").required("minimum"), JSON.readTree("6")));
        });
        assertThat(diff.rules().modified()).isEmpty();
        assertThat(diff.isEmpty()).isFalse();
    }

    // Expected: the defaults whole on both sides, and in the JSON as from and to
    @Test
    void theDefaultsAreComparedAsAWhole() {
        ObjectNode after = Fixtures.lendingV1();
        ((ObjectNode) after.required("defaults")).put("reason", "כל בקשה אחרת נדחית");

        StructuralDiff diff = diff(Fixtures.lendingV1(), after);

        assertThat(diff.defaults()).isEqualTo(new StructuralDiff.Replaced(
                Fixtures.lendingV1().required("defaults"), after.required("defaults")));
        ObjectNode sides = JSON.createObjectNode();
        sides.set("from", Fixtures.lendingV1().required("defaults"));
        sides.set("to", after.required("defaults"));
        assertThat(diff.toJson().required("defaults")).isEqualTo(sides);
        assertThat(diff.isEmpty()).isFalse();
    }

    // An attribute one side does not have: R-170 without its tags. Expected: a change to null, and null in the JSON
    @Test
    void anAttributeOnOneSideOnlyIsNullOnTheOther() {
        ObjectNode untagged = (ObjectNode) rule(Fixtures.lendingV1(), "R-170").deepCopy();
        untagged.remove("tags");

        StructuralDiff diff = diff(Fixtures.lendingV1(), withRules(Fixtures.lendingV1(), untagged));
        StructuralDiff back = diff(withRules(Fixtures.lendingV1(), untagged), Fixtures.lendingV1());

        assertThat(diff.rules().modified().getFirst().changes()).containsExactly(
                new StructuralDiff.Change("/tags", JSON.readTree("[\"eligibility\"]"), null));
        assertThat(back.rules().modified().getFirst().changes()).containsExactly(
                new StructuralDiff.Change("/tags", null, JSON.readTree("[\"eligibility\"]")));
        assertThat(diff.toJson().required("rules").required("modified").get(0).required("changes").get(0)
                .required("to").isNull()).isTrue();
    }

    // Expected: the JSON the audit entry stores, R-170's condition leaf at its pointer with the two values
    @Test
    void theJsonCarriesEachChangeAtItsPointer() {
        ObjectNode after = withRules(Fixtures.lendingV1(), SCRIPTED.get(0).required("rule"));

        JsonNode rules = diff(Fixtures.lendingV1(), after).toJson().required("rules");

        JsonNode r170 = rules.required("modified").get(0);
        assertThat(r170.required("id").asString()).isEqualTo("R-170");
        assertThat(r170.required("from")).isEqualTo(rule(Fixtures.lendingV1(), "R-170"));
        assertThat(r170.required("to")).isEqualTo(SCRIPTED.get(0).required("rule"));
        assertThat(r170.required("changes").get(1))
                .isEqualTo(JSON.readTree("{\"path\": \"/condition/value\", \"from\": 8000, \"to\": 9000}"));
    }

    private static StructuralDiff diff(ObjectNode before, ObjectNode after) {
        return StructuralDiff.between(MAPPER.toRuleSet(before), MAPPER.toRuleSet(after));
    }

    /** The document with each given rule in the place of the rule with its id. */
    private static ObjectNode withRules(ObjectNode document, JsonNode... replacements) {
        ObjectNode copy = document.deepCopy();
        ArrayNode rules = (ArrayNode) copy.required("rules");
        for (JsonNode replacement : replacements) {
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).required("id").equals(replacement.required("id"))) {
                    rules.set(i, replacement);
                }
            }
        }
        return copy;
    }

    private static JsonNode rule(JsonNode document, String id) {
        return document.required("rules").valueStream().filter(rule -> rule.required("id").asString().equals(id))
                .findFirst().orElseThrow();
    }

    private static JsonNode field(JsonNode document, String name) {
        return document.required("fields").valueStream()
                .filter(field -> field.required("name").asString().equals(name)).findFirst().orElseThrow();
    }

    /** A labeled policy's expected rule set, fixtures/eval/policies/<slug>/expected.ruleset.json. */
    private static ObjectNode expectedRuleSet(String slug) {
        return (ObjectNode) Fixtures.json("eval/policies/" + slug + "/expected.ruleset.json").deepCopy();
    }

    /** The first expected patch of a labeled request of fixtures/eval/changes.json. */
    private static JsonNode labeledPatch(String id) {
        return Fixtures.json("eval/changes.json").required("changes").valueStream()
                .filter(change -> change.required("id").asString().equals(id)).findFirst().orElseThrow()
                .required("expected").required("patches").get(0);
    }
}
