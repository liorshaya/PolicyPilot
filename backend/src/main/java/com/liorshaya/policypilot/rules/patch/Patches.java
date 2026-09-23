package com.liorshaya.policypilot.rules.patch;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A Patches object as it is stored (Document 3, Patch validation: "When a proposal is stored, the system writes the
 * change request's id into every pending provenance; the value the model wrote is never kept").
 */
public final class Patches {

    private Patches() {}

    /** A copy of the patches whose every {@code pending} provenance carries the stored change request's id. */
    public static ObjectNode withChangeRequestId(JsonNode patches, String changeRequestId) {
        ObjectNode stored = (ObjectNode) patches.deepCopy();
        for (JsonNode patch : stored.path("patches")) {
            JsonNode provenance = patch.path("rule").path("provenance");
            if ("pending".equals(provenance.path("kind").asString(""))) {
                ((ObjectNode) provenance).put("changeRequestId", changeRequestId);
            }
        }
        return stored;
    }
}
