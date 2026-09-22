package com.liorshaya.policypilot.support;

import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.ruleset.service.ReviewFinding;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The drafts the reviewer is given during evaluation, and the rule that says whether it found a seeded defect
 * (fixtures/README.md, Evaluation set; Document 4, Reviewer recall). A labeled policy's draft is its expected rule set
 * with every {@code ruleset} mutation of its seeded findings applied, the way the reference's {@code apply_mutation}
 * applies one: {@code set} changes a value, {@code remove} drops a rule, {@code duplicate} copies a rule under a new
 * id.
 */
public final class SeededDrafts {

    private SeededDrafts() {}

    /** The seeded findings of a labeled policy. */
    public static List<JsonNode> seeded(String slug) {
        List<JsonNode> findings = new ArrayList<>();
        Fixtures.json("eval/policies/" + slug + "/seeded.findings.json").path("findings").forEach(findings::add);
        return findings;
    }

    /** The expected rule set with every ruleset mutation of the seeded findings applied, in their order. */
    public static ObjectNode draft(String slug) {
        ObjectNode draft = (ObjectNode) Fixtures.json("eval/policies/" + slug + "/expected.ruleset.json");
        for (JsonNode finding : seeded(slug)) {
            if ("ruleset".equals(finding.path("planted").asString(""))) {
                apply(draft, finding.required("mutation"));
            }
        }
        return draft;
    }

    /** The labeled policy's paragraphs, as a policy version the draft cites. */
    public static PolicyVersionRef policy(String slug) {
        List<String> texts = Fixtures.paragraphs(Fixtures.evaluationPolicyText(slug));
        List<PolicyVersionRef.Paragraph> paragraphs = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            paragraphs.add(new PolicyVersionRef.Paragraph(UUID.randomUUID(), i + 1, texts.get(i)));
        }
        return new PolicyVersionRef(UUID.randomUUID(), UUID.randomUUID(), 1, paragraphs);
    }

    /** {@code he} or {@code en}, from the policy file's name. */
    public static String language(String slug) {
        return Fixtures.evaluationPolicyText(slug).endsWith(".en.md") ? "en" : "he";
    }

    /**
     * fixtures/README.md: a seeded defect is found "when a finding has the same kind and an overlapping anchor (a
     * shared paragraph index or rule id)".
     */
    public static boolean found(JsonNode seeded, List<ReviewFinding> findings) {
        String kind = seeded.required("kind").asString();
        List<Integer> paragraphs = new ArrayList<>();
        seeded.path("paragraphIndexes").forEach(index -> paragraphs.add(index.asInt()));
        List<String> rules = new ArrayList<>();
        seeded.path("ruleIds").forEach(id -> rules.add(id.asString()));
        return findings.stream().anyMatch(finding -> finding.kind().json().equals(kind)
                && (finding.paragraphIndexes().stream().anyMatch(paragraphs::contains)
                        || finding.ruleIds().stream().anyMatch(rules::contains)));
    }

    private static void apply(ObjectNode draft, JsonNode mutation) {
        ArrayNode rules = draft.withArray("rules");
        String ruleId = mutation.required("ruleId").asString();
        int index = indexOf(rules, ruleId);
        switch (mutation.required("op").asString()) {
            case "set" -> {
                // the path steps through objects by name and arrays by index, as the reference's does
                JsonNode path = mutation.required("path");
                JsonNode node = rules.get(index);
                for (int i = 0; i < path.size() - 1; i++) {
                    JsonNode step = path.get(i);
                    node = step.isInt() ? node.required(step.asInt()) : node.required(step.asString());
                }
                JsonNode last = path.get(path.size() - 1);
                if (last.isInt()) {
                    ((ArrayNode) node).set(last.asInt(), mutation.required("value"));
                } else {
                    ((ObjectNode) node).set(last.asString(), mutation.required("value"));
                }
            }
            case "remove" -> rules.remove(index);
            case "duplicate" -> {
                ObjectNode copy = (ObjectNode) rules.get(index).deepCopy();
                copy.put("id", mutation.required("asId").asString());
                rules.add(copy);
            }
            default -> throw new IllegalArgumentException("unknown mutation " + mutation);
        }
    }

    private static int indexOf(ArrayNode rules, String ruleId) {
        for (int i = 0; i < rules.size(); i++) {
            if (ruleId.equals(rules.get(i).path("id").asString(""))) {
                return i;
            }
        }
        throw new IllegalArgumentException("no rule " + ruleId + " to mutate");
    }
}
