package com.liorshaya.policypilot.rules.validation;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/**
 * The schema step of Document 3, Patch validation: a Patches object against {@code schemas/patches-1.0.schema.json},
 * whose {@code rule}, {@code field} and {@code defaults} are the rule set's own definitions by {@code $ref}, so a
 * patched rule fails exactly as a rule of a rule set does. Every violation is DSL_SCHEMA at its JSON pointer, with
 * the rule id or field name of the patch it falls inside. The instance holds the compiled schema; it is immutable and
 * safe to share.
 */
public final class PatchSchemaValidator {

    private static final String SCHEMA_LOCATION = "classpath:schemas/patches-1.0.schema.json";
    private static final String PATCHES = "/patches/";

    private final Schema schema = Schemas.load(SCHEMA_LOCATION);

    /** The schema violations of a Patches object; empty when it conforms. */
    public List<Finding> validate(JsonNode patches) {
        List<Finding> findings = new ArrayList<>();
        for (Error error : ClosestAlternative.select(schema.validate(patches))) {
            String pointer = error.getInstanceLocation().toString();
            JsonNode patch = patchAt(patches, pointer);
            findings.add(new Finding(ValidationCode.DSL_SCHEMA, pointer, Schemas.message(error, pointer),
                    anchor(patch.path("ruleId"), patch.path("rule").path("id")),
                    anchor(patch.path("field").path("name"))));
        }
        return findings;
    }

    /** The patch the pointer falls inside, or a missing node when it falls outside every patch. */
    private static JsonNode patchAt(JsonNode patches, String pointer) {
        if (!pointer.startsWith(PATCHES)) {
            return MissingNode.getInstance();
        }
        return patches.at(PATCHES + pointer.substring(PATCHES.length()).split("/", 2)[0]);
    }

    /** The first of the candidates that is a string, as a one-element list; empty when none is. */
    private static List<String> anchor(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate.isString()) {
                return List.of(candidate.stringValue());
            }
        }
        return List.of();
    }
}
