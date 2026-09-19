package com.liorshaya.policypilot.support;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * The fixture comparison of {@code reference_check.py} ({@code subset}; fixtures/README.md): every key of the
 * expected object is present in the actual one with an equal value, lists have the same length and match element by
 * element, and numbers are compared by value, so a partial trace step such as {@code {"ruleId": "R-100", "status":
 * "skipped"}} is enough.
 */
public final class JsonSubset {

    private JsonSubset() {}

    /** {@code null} when {@code expected} is a subset of {@code actual}, otherwise the first difference. */
    public static @Nullable String mismatch(JsonNode expected, JsonNode actual) {
        return mismatch(expected, actual, "$");
    }

    private static @Nullable String mismatch(JsonNode expected, JsonNode actual, String path) {
        if (expected.isObject()) {
            if (!actual.isObject()) {
                return path + ": expected an object, got " + actual;
            }
            for (Map.Entry<String, JsonNode> entry : expected.properties()) {
                JsonNode value = actual.get(entry.getKey());
                if (value == null) {
                    return path + "." + entry.getKey() + ": missing";
                }
                String problem = mismatch(entry.getValue(), value, path + "." + entry.getKey());
                if (problem != null) {
                    return problem;
                }
            }
            return null;
        }
        if (expected.isArray()) {
            if (!actual.isArray() || actual.size() != expected.size()) {
                return path + ": expected " + expected.size() + " items, got " + actual;
            }
            for (int i = 0; i < expected.size(); i++) {
                String problem = mismatch(expected.get(i), actual.get(i), path + "[" + i + "]");
                if (problem != null) {
                    return problem;
                }
            }
            return null;
        }
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0 ? null
                    : path + ": expected " + expected + ", got " + actual;
        }
        return expected.equals(actual) ? null : path + ": expected " + expected + ", got " + actual;
    }
}
