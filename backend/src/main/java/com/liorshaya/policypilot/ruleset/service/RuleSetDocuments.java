package com.liorshaya.policypilot.ruleset.service;

import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.Provenance;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.validation.Finding;
import com.liorshaya.policypilot.rules.validation.Severity;
import com.liorshaya.policypilot.ruleset.entity.RuleEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The parts of a rule set version that need no database: the stored JSON of a document, the {@code rule} rows a
 * publish denormalizes from it (Document 2, Data Model) and the problems the API reports for a refused document.
 */
public final class RuleSetDocuments {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();
    private static final RuleSetMapper RULES = new RuleSetMapper();

    private RuleSetDocuments() {}

    /** The document as it is stored, with decimals written plainly so the stored JSON reads like the fixture. */
    public static String json(JsonNode document) {
        return JSON.writeValueAsString(document);
    }

    /** The document's {@code fields} array, stored beside it as the version's field schema. */
    public static String fieldSchema(JsonNode document) {
        JsonNode fields = document.path("fields");
        return JSON.writeValueAsString(fields.isArray() ? fields : JSON.createArrayNode());
    }

    /** The stored document, parsed with the production limits and exact decimals. */
    public static ObjectNode read(String json) {
        return (ObjectNode) RULES.readTree(json);
    }

    /**
     * One {@code rule} row per rule of the document, in its order: the provenance kind, the paragraph row a
     * {@code quoted} rule cites and its quote (Document 2, {@code rule}; Document 3, Provenance).
     */
    public static List<RuleEntity> rows(UUID versionId, RuleSet ruleSet, ArrayNode rules, PolicyVersionRef policy) {
        Map<Integer, UUID> paragraphs = policy.paragraphs().stream()
                .collect(java.util.stream.Collectors.toMap(PolicyVersionRef.Paragraph::index,
                        PolicyVersionRef.Paragraph::id));
        List<RuleEntity> rows = new ArrayList<>();
        for (int i = 0; i < ruleSet.rules().size(); i++) {
            Rule rule = ruleSet.rules().get(i);
            Provenance provenance = rule.provenance();
            rows.add(new RuleEntity(UUID.randomUUID(), versionId, rule.id(), rule.priority(), rule.label(),
                    kind(provenance), paragraphOf(provenance, paragraphs), quoteOf(provenance),
                    JSON.writeValueAsString(rules.get(i))));
        }
        return rows;
    }

    /** The errors of a validation, as the API reports them: the pointer and the code, never the document's text. */
    public static List<RulesetProblem> problems(List<Finding> findings) {
        return findings.stream()
                .filter(finding -> finding.severity() == Severity.ERROR)
                .map(finding -> new RulesetProblem(finding.path(), finding.code().name()))
                .toList();
    }

    /** The findings that are not errors, as an audit entry lists them on publish (Document 3, Publishing gate). */
    public static ArrayNode warnings(List<Finding> findings) {
        ArrayNode warnings = JSON.createArrayNode();
        findings.stream()
                .filter(finding -> finding.severity() == Severity.WARNING)
                .forEach(finding -> warnings.addObject()
                        .put("code", finding.code().name())
                        .put("path", finding.path()));
        return warnings;
    }

    private static String kind(Provenance provenance) {
        return switch (provenance) {
            case Provenance.Quoted ignored -> "quoted";
            case Provenance.Analyst ignored -> "analyst";
            case Provenance.Pending ignored -> "pending";
        };
    }

    private static @Nullable UUID paragraphOf(Provenance provenance, Map<Integer, UUID> paragraphs) {
        return map(provenance, quoted -> paragraphs.get(quoted.paragraph()));
    }

    private static @Nullable String quoteOf(Provenance provenance) {
        return map(provenance, Provenance.Quoted::quote);
    }

    private static <T> @Nullable T map(Provenance provenance, Function<Provenance.Quoted, T> of) {
        return provenance instanceof Provenance.Quoted quoted ? of.apply(quoted) : null;
    }
}
