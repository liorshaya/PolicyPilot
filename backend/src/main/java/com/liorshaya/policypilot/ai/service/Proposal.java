package com.liorshaya.policypilot.ai.service;

import com.liorshaya.policypilot.rules.patch.PatchValidation;
import com.liorshaya.policypilot.rules.patch.Patches;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the change use case answers (Document 4, Prompt 5): the candidates the model was shown, its last answer, what
 * Patch validation found in that answer (Document 3), and how many repairs it took.
 *
 * @param answer the Patches object the model answered last, nulls stripped
 */
public record Proposal(Candidates candidates, JsonNode answer, PatchValidation validation, int repairs) {

    /** Whether the answer is a proposal a person may see: every patch applied and the copy has no error. */
    public boolean valid() {
        return validation.valid();
    }

    /** Whether the proposal validator refused it: nothing is repaired or stored (Document 5, RT-04). */
    public boolean refused() {
        return validation.refused();
    }

    /** The answer as the change request with this id stores it: every pending provenance carries the id. */
    public ObjectNode storedAs(String changeRequestId) {
        return Patches.withChangeRequestId(answer, changeRequestId);
    }
}
