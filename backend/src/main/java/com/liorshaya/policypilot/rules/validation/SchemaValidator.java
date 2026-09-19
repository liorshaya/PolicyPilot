package com.liorshaya.policypilot.rules.validation;

import com.liorshaya.policypilot.rules.model.RuleSet;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/**
 * Layer 1: the committed JSON Schema, draft 2020-12 (Document 3, JSON Schema). Two violations get their own
 * codes: a {@code dslVersion} other than {@code 1.0} is DSL_VERSION_UNSUPPORTED, and the derived-field constraint
 * (not required, no default) is DERIVED_REQUIRED; everything else is DSL_SCHEMA with the JSON pointer and the
 * schema keyword.
 */
final class SchemaValidator {

    /** The canonical schema, identical to {@code fixtures/schemas/ruleset-1.0.schema.json} (CI stage 1 checks). */
    private static final String SCHEMA_LOCATION = "classpath:schemas/ruleset-1.0.schema.json";

    /** The field schema's {@code allOf[1]}: a derived field is not required and has no default. */
    private static final String DERIVED_CONSTRAINT = "/properties/fields/items/$ref/allOf/1/";

    private static final String VERSION_POINTER = "/dslVersion";

    private final Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                    registry -> registry.schemaRegistryConfig(SchemaRegistryConfig.builder()
                            .locale(Locale.ENGLISH)
                            .pathType(PathType.JSON_POINTER)
                            .build()))
            .getSchema(SchemaLocation.of(SCHEMA_LOCATION));

    List<Finding> validate(JsonNode document) {
        List<Finding> findings = new ArrayList<>();
        boolean versionReported = unsupportedVersion(document);
        if (versionReported) {
            findings.add(new Finding(ValidationCode.DSL_VERSION_UNSUPPORTED, VERSION_POINTER,
                    "dslVersion must be " + RuleSet.DSL_VERSION, List.of(), List.of()));
        }
        for (Error error : ClosestAlternative.select(schema.validate(document))) {
            String pointer = error.getInstanceLocation().toString();
            if (!(versionReported && pointer.equals(VERSION_POINTER))) {
                findings.add(finding(document, error, pointer));
            }
        }
        return findings;
    }

    private static boolean unsupportedVersion(JsonNode document) {
        JsonNode version = document.get("dslVersion");
        return version != null && !(version.isString() && RuleSet.DSL_VERSION.equals(version.stringValue()));
    }

    private static Finding finding(JsonNode document, Error error, String pointer) {
        ValidationCode code = error.getEvaluationPath().toString().startsWith(DERIVED_CONSTRAINT)
                ? ValidationCode.DERIVED_REQUIRED
                : ValidationCode.DSL_SCHEMA;
        String message = error.getKeyword() + " at " + (pointer.isEmpty() ? "the document root" : pointer) + ": "
                + error.getMessage();
        return new Finding(code, pointer, message, anchor(document, pointer, "/rules/", "id"),
                anchor(document, pointer, "/fields/", "name"));
    }

    /** The id of the rule, or the name of the field, that the pointer falls inside, when the document has one. */
    private static List<String> anchor(JsonNode document, String pointer, String collection, String key) {
        if (!pointer.startsWith(collection)) {
            return List.of();
        }
        String index = pointer.substring(collection.length()).split("/", 2)[0];
        JsonNode value = document.at(collection + index + "/" + key);
        return value.isString() ? List.of(value.stringValue()) : List.of();
    }
}
