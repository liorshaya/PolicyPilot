package com.liorshaya.policypilot.support;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The OpenAPI document the running API serves at {@code /api/docs}, used as the contract (Document 6, Contract level:
 * "does the API match its OpenAPI document"). A response body is validated against the schema the document gives
 * for that operation and status, with the JSON Schema validator the rules package already uses.
 */
public final class OpenApiContract {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ObjectNode document;

    public OpenApiContract(String openApiJson) {
        this.document = (ObjectNode) JSON.readTree(openApiJson);
    }

    /** The validation errors of {@code body} against the documented response of {@code method path} with {@code status}. */
    public List<String> violations(String method, String path, int status, String body) {
        JsonNode response = document.path("paths").path(path).path(method.toLowerCase()).path("responses")
                .path(Integer.toString(status));
        if (response.isMissingNode()) {
            return List.of(method + " " + path + " documents no " + status + " response");
        }
        JsonNode content = response.path("content");
        JsonNode schema = content.isEmpty() ? null : content.properties().iterator().next().getValue().path("schema");
        if (schema == null || schema.isMissingNode()) {
            return List.of(method + " " + path + " " + status + " has no schema");
        }
        ObjectNode root = document.deepCopy();
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.set("allOf", JSON.createArrayNode().add(schema));
        Schema validator = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(SchemaLocation.of("https://policypilot.test/api/docs"), root);
        return validator.validate(JSON.readTree(body)).stream().map(Error::toString).toList();
    }

    /** Whether the document describes {@code method path}. */
    public boolean documents(String method, String path) {
        return !document.path("paths").path(path).path(method.toLowerCase()).isMissingNode();
    }
}
