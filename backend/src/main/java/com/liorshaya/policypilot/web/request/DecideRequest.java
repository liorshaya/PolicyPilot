package com.liorshaya.policypilot.web.request;

import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The body of {@code POST /rulesets/{id}/versions/{no}/decide} (Document 2, API Surface): exactly one of one
 * {@code case}, a list of {@code cases} (at most 500, Document 5) or a seeded {@code fixtureSet}. The body is read
 * from the request text with exact decimals, so a case reaches the engine as it was written.
 */
public record DecideRequest(@Nullable ObjectNode singleCase, @Nullable List<ObjectNode> cases,
        @Nullable String fixtureSet) {

    /** At most 500 cases per request (Document 5, Availability and Abuse Resistance: Batch decide). */
    public static final int MAX_CASES = 500;

    private static final Set<String> PROPERTIES = Set.of("case", "cases", "fixtureSet");

    /** Reads the body, refusing anything that is not exactly one of the three shapes. */
    public static DecideRequest of(JsonNode body) {
        if (!body.isObject()) {
            throw invalid("", "is not a decide request");
        }
        if (!PROPERTIES.containsAll(body.propertyNames())) {
            throw invalid("", "has an unknown property");
        }
        DecideRequest request = new DecideRequest(caseOf(body), casesOf(body), fixtureSetOf(body));
        if (request.shapes() != 1) {
            throw invalid("", "needs exactly one of case, cases and fixtureSet");
        }
        return request;
    }

    /** Whether this request decides a batch, which is limited per sandbox (Document 5, Batch decide). */
    public boolean isBatch() {
        return cases != null || fixtureSet != null;
    }

    private int shapes() {
        return (singleCase != null ? 1 : 0) + (cases != null ? 1 : 0) + (fixtureSet != null ? 1 : 0);
    }

    private static @Nullable ObjectNode caseOf(JsonNode body) {
        JsonNode node = body.get("case");
        if (node == null) {
            return null;
        }
        if (!node.isObject()) {
            throw invalid("/case", "is not a case");
        }
        return (ObjectNode) node;
    }

    private static @Nullable List<ObjectNode> casesOf(JsonNode body) {
        JsonNode node = body.get("cases");
        if (node == null) {
            return null;
        }
        if (!node.isArray()) {
            throw invalid("/cases", "is not a list of cases");
        }
        if (node.size() > MAX_CASES) {
            throw invalid("/cases", "has more than " + MAX_CASES + " cases");
        }
        List<ObjectNode> cases = node.valueStream().map(element -> {
            if (!element.isObject()) {
                throw invalid("/cases", "holds something that is not a case");
            }
            return (ObjectNode) element;
        }).toList();
        return cases;
    }

    private static @Nullable String fixtureSetOf(JsonNode body) {
        JsonNode node = body.get("fixtureSet");
        if (node == null) {
            return null;
        }
        if (!node.isString()) {
            throw invalid("/fixtureSet", "is not a fixture set name");
        }
        return node.stringValue();
    }

    private static ApiException invalid(String path, String problem) {
        return new ApiException(ErrorCode.REQUEST_INVALID, List.of(new ErrorDetail(path, problem)));
    }
}
