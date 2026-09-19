package com.liorshaya.policypilot.web.request;

import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The body of {@code POST /rulesets/{id}/versions/{no}/simulate} (Document 2, API Surface; Document 3, Simulation):
 * a stored {@code decisionId} or a {@code case}, plus the {@code overrides} to apply to it.
 */
public record SimulateRequest(@Nullable UUID decisionId, @Nullable ObjectNode caseInput, ObjectNode overrides) {

    private static final Set<String> PROPERTIES = Set.of("decisionId", "case", "overrides");

    /** Reads the body, refusing anything that is not exactly one base with its overrides. */
    public static SimulateRequest of(JsonNode body) {
        if (!body.isObject() || !PROPERTIES.containsAll(body.propertyNames())) {
            throw invalid("", "is not a simulate request");
        }
        JsonNode overrides = body.get("overrides");
        if (overrides == null || !overrides.isObject() || overrides.isEmpty()) {
            throw invalid("/overrides", "names no field to override");
        }
        UUID decisionId = decisionId(body);
        ObjectNode caseInput = caseInput(body);
        if ((decisionId == null) == (caseInput == null)) {
            throw invalid("", "needs exactly one of decisionId and case");
        }
        return new SimulateRequest(decisionId, caseInput, (ObjectNode) overrides);
    }

    private static @Nullable UUID decisionId(JsonNode body) {
        JsonNode node = body.get("decisionId");
        if (node == null) {
            return null;
        }
        try {
            return UUID.fromString(node.asString(""));
        } catch (IllegalArgumentException e) {
            throw invalid("/decisionId", "is not a decision id");
        }
    }

    private static @Nullable ObjectNode caseInput(JsonNode body) {
        JsonNode node = body.get("case");
        if (node == null) {
            return null;
        }
        if (!node.isObject()) {
            throw invalid("/case", "is not a case");
        }
        return (ObjectNode) node;
    }

    private static ApiException invalid(String path, String problem) {
        return new ApiException(ErrorCode.REQUEST_INVALID, List.of(new ErrorDetail(path, problem)));
    }
}
