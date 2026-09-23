package com.liorshaya.policypilot.rules.patch;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Requirement;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A Patches object as it is stored (Document 3, Patch validation: "the system writes the change request's id into every
 * pending provenance; the value the model wrote is never kept").
 */
@Requirement("FR-19")
class PatchesTest {

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
}
