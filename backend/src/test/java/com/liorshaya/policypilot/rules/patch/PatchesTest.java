package com.liorshaya.policypilot.rules.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A Patches object as it is stored (Document 3, Patch validation: "the system writes the change request's id into every
 * pending provenance; the value the model wrote is never kept") and as an approval publishes it (Document 3,
 * Provenance: every pending becomes analyst, the approver as actor, the request and the rationale as note).
 */
@Requirement("FR-19")
class PatchesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ID = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90";
    /** A sandbox id, the approver Document 5 records. */
    private static final String ACTOR = "0f4c1c9e-0000-4000-8000-000000000001";

    // The scripted answer carries cr-0001, the fixture's id, in both pending provenances; the stored request's id
    // replaces it in both, and nothing else changes
    @Test
    void everyPendingProvenanceCarriesTheStoredRequestsId() {
        ObjectNode answer = ChangeRequests.scriptedPatches();

        ObjectNode stored = Patches.withChangeRequestId(answer, "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90");

        for (JsonNode patch : stored.required("patches")) {
            assertThat(patch.required("rule").required("provenance").required("changeRequestId").asString())
                    .isEqualTo("3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90");
        }
        ((ObjectNode) stored.required("patches").get(0).required("rule").required("provenance"))
                .put("changeRequestId", "cr-0001");
        ((ObjectNode) stored.required("patches").get(1).required("rule").required("provenance"))
                .put("changeRequestId", "cr-0001");
        assertThat(stored).isEqualTo(answer);
    }

    // A remove has no rule, and a quoted provenance names no change request: both stay as the model wrote them
    @Test
    void aRemoveAndAQuotedProvenanceAreLeftAsTheyAre() {
        ObjectNode answer = ChangeRequests.scriptedPatches();
        ObjectNode quoted = ((ObjectNode) answer.required("patches").get(1).required("rule")).putObject("provenance");
        quoted.put("kind", "quoted").put("paragraph", 4).put("quote", "ההכנסה החודשית נטו");
        ((ArrayNode) answer.required("patches")).addObject().put("op", "remove").put("ruleId", "R-140")
                .put("rationale", "הכלל בוטל");

        ObjectNode stored = Patches.withChangeRequestId(answer, "cr-stored");

        assertThat(stored.required("patches").get(1)).isEqualTo(answer.required("patches").get(1));
        assertThat(stored.required("patches").get(2)).isEqualTo(answer.required("patches").get(2));
    }

    // Document 3: the approver as actor, "Change request <id>: <request text>" and on the next line the model's
    // rationale as note, and the change request's id kept. Expected: both of the scripted patches rewritten so, the
    // rationales being change-request-1.json's
    @Test
    void anApprovalRewritesEveryPendingIntoAnalyst() {
        JsonNode stored = Patches.withChangeRequestId(ChangeRequests.scriptedPatches(), ID).required("patches");

        ArrayNode approved = Patches.approved(stored, ChangeRequests.scripted(), ACTOR);

        for (int i = 0; i < 2; i++) {
            JsonNode pending = stored.get(i).required("rule").required("provenance");
            assertThat(approved.get(i).required("rule").required("provenance")).isEqualTo(JSON.createObjectNode()
                    .put("kind", "analyst")
                    .put("note", "Change request " + ID + ": " + ChangeRequests.scripted() + "\n"
                            + pending.required("rationale").asString())
                    .put("actor", ACTOR)
                    .put("changeRequestId", ID));
        }
        assertThat(stored.get(0).required("rule").required("provenance").required("kind").asString())
                .isEqualTo("pending");
    }

    // Only a pending provenance is the system's to rewrite. Expected: a quoted one and a remove left as they are
    @Test
    void anApprovalLeavesAQuotedProvenanceAndARemoveAsTheyAre() {
        ObjectNode answer = Patches.withChangeRequestId(ChangeRequests.scriptedPatches(), ID);
        ObjectNode quoted = ((ObjectNode) answer.required("patches").get(1).required("rule")).putObject("provenance");
        quoted.put("kind", "quoted").put("paragraph", 4).put("quote", "ההכנסה החודשית נטו");
        ((ArrayNode) answer.required("patches")).addObject().put("op", "remove").put("ruleId", "R-140")
                .put("rationale", "הכלל בוטל");

        ArrayNode approved = Patches.approved(answer.required("patches"), ChangeRequests.scripted(), ACTOR);

        assertThat(approved.get(1)).isEqualTo(answer.required("patches").get(1));
        assertThat(approved.get(2)).isEqualTo(answer.required("patches").get(2));
    }

    // ruleset-1.0: an analyst note is at most 500 characters. Expected: a note of exactly 500 whole, and one
    // character more cut to its first 499 and an ellipsis
    @Test
    void aNoteLongerThanTheSchemaAllowsIsCutToIt() {
        JsonNode stored = Patches.withChangeRequestId(ChangeRequests.scriptedPatches(), ID).required("patches");
        String rationale = stored.get(0).required("rule").required("provenance").required("rationale").asString();
        int around = Patches.NOTE_MAX - ("Change request " + ID + ": \n" + rationale).length();

        String whole = note(Patches.approved(stored, "א".repeat(around), ACTOR));
        String cut = note(Patches.approved(stored, "א".repeat(around + 1), ACTOR));

        assertThat(whole).hasSize(500).endsWith(rationale);
        assertThat(cut).hasSize(500).endsWith("…").startsWith("Change request " + ID + ": א");
    }

    // Document 3: replace in place, add at the end, remove and retire. Expected: R-170 and R-410 with the fixture's
    // conditions in their places, and the remove's id retired
    @Test
    void theStoredPatchesApplyToTheBase() {
        ObjectNode answer = ChangeRequests.scriptedPatches();
        ((ArrayNode) answer.required("patches")).addObject().put("op", "remove").put("ruleId", "R-140")
                .put("rationale", "הכלל בוטל");

        Patches.Applied applied = Patches.apply(Fixtures.lendingV1(), answer.required("patches"));

        JsonNode rules = applied.document().required("rules");
        assertThat(rule(rules, "R-170")).isEqualTo(answer.required("patches").get(0).required("rule"));
        assertThat(rule(rules, "R-410")).isEqualTo(answer.required("patches").get(1).required("rule"));
        assertThat(rules.valueStream().map(rule -> rule.required("id").asString())).doesNotContain("R-140");
        assertThat(applied.retired()).containsExactly("R-140");
    }

    // Expected: stored patches that no longer apply, a replace of a rule the base does not have, are a defect
    @Test
    void storedPatchesThatNoLongerApplyAreADefect() {
        ArrayNode patches = JSON.createArrayNode();
        patches.addObject().put("op", "remove").put("ruleId", "R-999").put("rationale", "כלל שאיננו");

        assertThatThrownBy(() -> Patches.apply(Fixtures.lendingV1(), patches))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PATCH_TARGET_UNKNOWN");
    }

    private static String note(ArrayNode approved) {
        return approved.get(0).required("rule").required("provenance").required("note").asString();
    }

    private static JsonNode rule(JsonNode rules, String id) {
        return rules.valueStream().filter(rule -> rule.required("id").asString().equals(id)).findFirst().orElseThrow();
    }
}
