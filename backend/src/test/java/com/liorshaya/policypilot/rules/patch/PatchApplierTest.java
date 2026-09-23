package com.liorshaya.policypilot.rules.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Applying patches to a copy of the lending version (Document 3, Change Patches: the op table and its constraints),
 * the way the reference implementation's {@code apply_patches} applies them: replace in place, add at the end,
 * remove by id.
 */
@Requirement({"FR-17", "FR-18"})
class PatchApplierTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // change-request-1.json: R-170 and R-410 replaced by the fixture's rules; every other rule, the fields and the
    // defaults exactly as ruleset.v1.json has them
    @Test
    void theScriptedPatchesReplaceR170AndR410AndNothingElse() {
        ObjectNode base = Fixtures.lendingV1();
        JsonNode expected = ChangeRequests.scriptedPatches().required("patches");

        PatchApplier.Applied applied = PatchApplier.apply(ChangeRequests.scriptedPatches(), base, Set.of());

        assertThat(applied.problems()).isEmpty();
        ArrayNode rules = (ArrayNode) applied.document().required("rules");
        assertThat(rules).hasSize(20);
        for (int i = 0; i < rules.size(); i++) {
            String id = rules.get(i).required("id").asString();
            JsonNode wanted = switch (id) {
                case "R-170" -> expected.get(0).required("rule");
                case "R-410" -> expected.get(1).required("rule");
                default -> base.required("rules").get(i);
            };
            assertThat(rules.get(i)).as(id).isEqualTo(wanted);
        }
        assertThat(applied.document().required("fields")).isEqualTo(base.required("fields"));
        assertThat(applied.document().required("defaults")).isEqualTo(base.required("defaults"));
        assertThat(applied.ruleAt()).containsExactly(entry("R-170", 0), entry("R-410", 1));
        assertThat(applied.retired()).isEmpty();
    }

    // The base is the stored version: applying patches to it never changes it
    @Test
    void theBaseIsNotChanged() {
        ObjectNode base = Fixtures.lendingV1();

        PatchApplier.apply(ChangeRequests.rt04Answer(), base, Set.of());

        assertThat(base).isEqualTo(Fixtures.lendingV1());
    }

    // Document 3: "The id is retired and recorded in the version's retiredIds list"
    @Test
    void aRemovedRuleIsGoneAndItsIdRetired() {
        PatchApplier.Applied applied = apply(remove("R-140"));

        assertThat(ids(applied)).hasSize(19).doesNotContain("R-140");
        assertThat(applied.retired()).containsExactly("R-140");
    }

    // The reference implementation appends an added rule; the engine orders rules by priority, not by place
    @Test
    void anAddedRuleIsAppended() {
        PatchApplier.Applied applied = apply(add("R-180", "R-180"));

        assertThat(ids(applied)).hasSize(21).last().isEqualTo("R-180");
        assertThat(applied.ruleAt()).containsExactly(entry("R-180", 0));
    }

    // R-010, the installment formula, is the version's first rule: a patch finds it there like anywhere else
    @Test
    void theFirstRuleOfTheVersionCanBeReplacedAndRemoved() {
        assertThat(apply(replace("R-010", "R-010")).problems()).isEmpty();
        assertThat(apply(remove("R-010")).retired()).containsExactly("R-010");
    }

    // Document 3, op table: replace and remove name a rule of the version
    @Test
    void aReplaceOrRemoveOfAnUnknownRuleIsAnUnknownTarget() {
        assertThat(apply(replace("R-175", "R-175"), remove("R-999")).problems())
                .extracting(PatchProblem::code, PatchProblem::path, PatchProblem::ruleIds)
                .containsExactly(tuple(PatchCode.PATCH_TARGET_UNKNOWN, "/patches/0/ruleId", List.of("R-175")),
                        tuple(PatchCode.PATCH_TARGET_UNKNOWN, "/patches/1/ruleId", List.of("R-999")));
    }

    // Document 3, Patch validation: two patches on one rule, whether it was replaced, removed or added first
    @Test
    void aSecondPatchOnOneRuleIsRepeated() {
        assertThat(apply(replace("R-170", "R-170"), remove("R-170"), add("R-180", "R-180"), replace("R-180", "R-180"),
                remove("R-140"), replace("R-140", "R-140")).problems())
                .extracting(PatchProblem::code, PatchProblem::path)
                .containsExactly(tuple(PatchCode.PATCH_TARGET_REPEATED, "/patches/1/ruleId"),
                        tuple(PatchCode.PATCH_TARGET_REPEATED, "/patches/3/ruleId"),
                        tuple(PatchCode.PATCH_TARGET_REPEATED, "/patches/5/ruleId"));
    }

    // Document 3, op table: "full Rule with the same id"; an added rule is the rule its ruleId names
    @Test
    void theRuleOfAReplaceOrAddCarriesItsRuleId() {
        assertThat(apply(replace("R-170", "R-171"), add("R-180", "R-181")).problems())
                .extracting(PatchProblem::code, PatchProblem::path, PatchProblem::ruleIds)
                .containsExactly(tuple(PatchCode.PATCH_ID_CHANGED, "/patches/0/rule/id", List.of("R-170")),
                        tuple(PatchCode.PATCH_ID_CHANGED, "/patches/1/rule/id", List.of("R-180")));
    }

    // Document 3, op table: "id must be new for the whole lineage of the rule set (retired ids are never reused)"
    @Test
    void anAddedRuleNeedsAnIdNewToTheLineage() {
        PatchApplier.Applied applied = PatchApplier.apply(answer(add("R-170", "R-170"), add("R-175", "R-175"),
                remove("R-140"), add("R-140", "R-140"), add("R-010", "R-010")), Fixtures.lendingV1(),
                Set.of("R-175"));

        assertThat(applied.problems()).extracting(PatchProblem::code, PatchProblem::path)
                .containsExactly(tuple(PatchCode.PATCH_ID_NOT_NEW, "/patches/0/rule/id"),
                        tuple(PatchCode.PATCH_ID_NOT_NEW, "/patches/1/rule/id"),
                        tuple(PatchCode.PATCH_ID_NOT_NEW, "/patches/3/rule/id"),
                        tuple(PatchCode.PATCH_ID_NOT_NEW, "/patches/4/rule/id"));
    }

    // Document 3, op table: add_field "only for derived fields or optional case fields"
    @Test
    void anAddedCaseFieldMustBeOptionalOrDerived() {
        ObjectNode required = addField("bonus_income");
        ((ObjectNode) required.required("field")).put("required", true);
        ObjectNode derived = addField("bonus_ratio");
        ((ObjectNode) derived.required("field")).put("derived", true);

        PatchApplier.Applied applied = apply(required, addField("side_income"), derived);

        assertThat(applied.problems()).extracting(PatchProblem::code, PatchProblem::path, PatchProblem::fieldNames)
                .containsExactly(tuple(PatchCode.PATCH_FIELD_REQUIRED, "/patches/0/field/required",
                        List.of("bonus_income")));
        assertThat(applied.fieldAt()).containsExactly(entry("side_income", 1), entry("bonus_ratio", 2));
        assertThat(applied.document().required("fields")).hasSize(13);
    }

    // Document 3, op table: set_defaults carries the new defaults (a proposal never gets this far with one)
    @Test
    void setDefaultsReplacesTheDefaults() {
        ObjectNode patch = JSON.createObjectNode().put("op", "set_defaults").put("rationale", "ברירת מחדל");
        patch.putObject("defaults").put("outcome", "reject").put("reason", "בקשה שלא הוכרעה נדחית");

        assertThat(apply(patch).document().required("defaults").required("outcome").asString()).isEqualTo("reject");
    }

    private static PatchApplier.Applied apply(ObjectNode... patches) {
        return PatchApplier.apply(answer(patches), Fixtures.lendingV1(), Set.of());
    }

    private static List<String> ids(PatchApplier.Applied applied) {
        return applied.document().required("rules").valueStream().map(rule -> rule.required("id").asString())
                .toList();
    }

    private static ObjectNode answer(ObjectNode... patches) {
        ObjectNode answer = ChangeRequests.scriptedPatches();
        ArrayNode list = ((ArrayNode) answer.required("patches")).removeAll();
        for (ObjectNode patch : patches) {
            list.add(patch);
        }
        return answer;
    }

    /** A patch whose rule is R-170's, under the id the rule carries. */
    private static ObjectNode patchOf(String op, String ruleId, String ruleIdOfTheRule) {
        ObjectNode rule = (ObjectNode) ChangeRequests.scriptedPatches().required("patches").get(0).required("rule")
                .deepCopy();
        rule.put("id", ruleIdOfTheRule);
        ObjectNode patch = JSON.createObjectNode().put("op", op).put("ruleId", ruleId).put("rationale", "שינוי");
        patch.set("rule", rule);
        return patch;
    }

    private static ObjectNode replace(String ruleId, String ruleIdOfTheRule) {
        return patchOf("replace", ruleId, ruleIdOfTheRule);
    }

    private static ObjectNode add(String ruleId, String ruleIdOfTheRule) {
        return patchOf("add", ruleId, ruleIdOfTheRule);
    }

    private static ObjectNode remove(String ruleId) {
        return JSON.createObjectNode().put("op", "remove").put("ruleId", ruleId).put("rationale", "הכלל בוטל");
    }

    private static ObjectNode addField(String name) {
        ObjectNode patch = JSON.createObjectNode().put("op", "add_field").put("rationale", "שדה חדש");
        patch.putObject("field").put("name", name).put("type", "number");
        return patch;
    }
}
