package com.liorshaya.policypilot.rules.validation;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import java.util.Locale;

/**
 * The committed JSON Schemas, draft 2020-12, as every validator here loads them: English messages, JSON pointers,
 * and each schema's {@code $id} mapped to its file on the classpath, so one schema can point into another by
 * {@code $ref} ({@code https://policypilot.dev/schemas/ruleset-1.0.json} is {@code schemas/ruleset-1.0.schema.json}).
 */
final class Schemas {

    private static final String ID_PREFIX = "https://policypilot.dev/schemas/";

    private Schemas() {}

    /** The schema at a classpath location, with the references it makes resolved. */
    static Schema load(String classpathLocation) {
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, registry -> registry
                        .schemaRegistryConfig(SchemaRegistryConfig.builder()
                                .locale(Locale.ENGLISH)
                                .pathType(PathType.JSON_POINTER)
                                .build())
                        .schemaIdResolvers(resolvers -> resolvers.mappings(
                                id -> id.startsWith(ID_PREFIX),
                                id -> "classpath:schemas/" + id.substring(ID_PREFIX.length())
                                        .replace(".json", ".schema.json"))))
                .getSchema(SchemaLocation.of(classpathLocation));
    }

    /** A schema error as a finding's message: the keyword, where, and what the validator said. */
    static String message(Error error, String pointer) {
        return error.getKeyword() + " at " + (pointer.isEmpty() ? "the document root" : pointer) + ": "
                + error.getMessage();
    }
}
