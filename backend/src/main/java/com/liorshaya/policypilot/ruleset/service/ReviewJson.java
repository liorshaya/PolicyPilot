package com.liorshaya.policypilot.ruleset.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The review as {@code ruleset_version.review_json} holds it (Document 2, Data Model), and back. The field names
 * are the API's, so the stored review reads the way the analyst's screen shows it.
 */
final class ReviewJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ReviewJson() {}

    static String write(Review review) {
        return tree(review).toString();
    }

    /** The review as a JSON object, which the publish audit entry also quotes. */
    static ObjectNode tree(Review review) {
        ObjectNode root = JSON.createObjectNode();
        root.put("status", review.status().name());
        root.put("promptVersion", review.promptVersion());
        ArrayNode findings = root.putArray("findings");
        for (ReviewFinding finding : review.findings()) {
            findings.add(finding(finding));
        }
        ObjectNode coverage = root.putObject("coverage");
        review.coverage().forEach((paragraph, rules) -> coverage.set(paragraph, JSON.valueToTree(rules)));
        return root;
    }

    static ObjectNode finding(ReviewFinding finding) {
        ObjectNode node = JSON.createObjectNode();
        node.put("id", finding.id());
        node.put("kind", finding.kind().json());
        node.put("severity", finding.severity());
        node.set("ruleIds", JSON.valueToTree(finding.ruleIds()));
        node.set("paragraphIndexes", JSON.valueToTree(finding.paragraphIndexes()));
        node.put("message", finding.message());
        node.put("suggestion", finding.suggestion());
        node.put("confidence", finding.confidence());
        Acknowledgement acknowledgement = finding.acknowledgement();
        if (acknowledgement != null) {
            ObjectNode ack = node.putObject("acknowledgement");
            if (acknowledgement.resolution() != null) {
                ack.put("resolution", acknowledgement.resolution().json());
            }
            if (acknowledgement.note() != null) {
                ack.put("note", acknowledgement.note());
            }
            ack.put("actor", acknowledgement.actor());
            ack.put("at", acknowledgement.at().toString());
        }
        return node;
    }

    static @Nullable Review read(@Nullable String stored) {
        if (stored == null) {
            return null;
        }
        JsonNode root = JSON.readTree(stored);
        List<ReviewFinding> findings = new ArrayList<>();
        for (JsonNode node : root.path("findings")) {
            findings.add(new ReviewFinding(node.required("id").asString(),
                    FindingKind.of(node.required("kind").asString()), strings(node.path("ruleIds")),
                    integers(node.path("paragraphIndexes")), node.required("message").asString(),
                    node.required("suggestion").asString(), node.required("confidence").asDouble(),
                    acknowledgement(node.path("acknowledgement"))));
        }
        Map<String, List<String>> coverage = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : root.path("coverage").properties()) {
            coverage.put(entry.getKey(), strings(entry.getValue()));
        }
        return new Review(ReviewStatus.valueOf(root.required("status").asString()),
                root.required("promptVersion").asString(), findings, coverage);
    }

    private static @Nullable Acknowledgement acknowledgement(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        GapResolution resolution = node.has("resolution") ? GapResolution.of(node.get("resolution").asString())
                : null;
        String note = node.has("note") ? node.get("note").asString() : null;
        return new Acknowledgement(resolution, note, node.required("actor").asString(),
                Instant.parse(node.required("at").asString()));
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }

    private static List<Integer> integers(JsonNode array) {
        List<Integer> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asInt()));
        return values;
    }
}
