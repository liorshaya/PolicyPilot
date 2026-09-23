package com.liorshaya.policypilot.rules.patch;

import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A Patches object as it is stored and as an approval publishes it (Document 3, Patch validation: "the system writes
 * the change request's id into every pending provenance; the value the model wrote is never kept"; Provenance: on
 * approval "the system rewrites each pending into analyst").
 */
public final class Patches {

    /** The schema's longest analyst note (ruleset-1.0: {@code analystProvenance.note.maxLength}). */
    static final int NOTE_MAX = 500;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Patches() {}

    /**
     * The base with a stored proposal's patches applied, and the ids its removes retired.
     *
     * @param document the base with the patches applied: rules replaced in place, added at the end, removed
     * @param retired the ids the removes retired, which the new version records
     */
    public record Applied(ObjectNode document, List<String> retired) {

        public Applied {
            retired = List.copyOf(retired);
        }
    }

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

    /**
     * A copy of the stored patches as an approval publishes them: every {@code pending} provenance becomes
     * {@code analyst} with the approver as {@code actor}, {@code Change request <id>: <request text>} and, on the next
     * line, the model's rationale as {@code note}, cut to the schema's 500 characters, and the change request's id
     * kept. The whole request stays in the change request and in the approval's audit entry.
     *
     * @param patches the patches array as the change request stores it
     */
    public static ArrayNode approved(JsonNode patches, String requestText, String actor) {
        ArrayNode approved = (ArrayNode) patches.deepCopy();
        for (JsonNode patch : approved) {
            JsonNode provenance = patch.path("rule").path("provenance");
            if ("pending".equals(provenance.path("kind").asString(""))) {
                String id = provenance.required("changeRequestId").asString();
                String note = "Change request " + id + ": " + requestText + "\n"
                        + provenance.required("rationale").asString();
                ((ObjectNode) patch.required("rule")).set("provenance", JSON.createObjectNode()
                        .put("kind", "analyst").put("note", cut(note)).put("actor", actor).put("changeRequestId", id));
            }
        }
        return approved;
    }

    /**
     * The base with a stored proposal's patches applied as Document 3 applies them. The patches passed Patch
     * validation when they were stored and the base is still the latest version, so one that no longer applies is a
     * defect, never a refusal.
     *
     * @param patches the patches array as the change request stores it
     * @throws IllegalStateException when a patch no longer applies
     */
    public static Applied apply(ObjectNode base, JsonNode patches) {
        ObjectNode proposal = JSON.createObjectNode();
        proposal.set("patches", patches);
        PatchApplier.Applied applied = PatchApplier.apply(proposal, base, List.of());
        if (!applied.problems().isEmpty()) {
            throw new IllegalStateException("the stored patches no longer apply: " + applied.problems());
        }
        return new Applied(applied.document(), applied.retired());
    }

    /** The note as the schema holds it: whole, or its first 499 characters and an ellipsis. */
    private static String cut(String note) {
        if (note.codePointCount(0, note.length()) <= NOTE_MAX) {
            return note;
        }
        return note.substring(0, note.offsetByCodePoints(0, NOTE_MAX - 1)) + "…";
    }
}
