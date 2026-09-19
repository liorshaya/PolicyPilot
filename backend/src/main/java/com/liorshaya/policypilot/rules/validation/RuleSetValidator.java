package com.liorshaya.policypilot.rules.validation;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Validates a rule set document in three layers, schema then semantic then structural, and stops after the first
 * layer that reports an error, so the most basic problems come first (Document 3, Static Validation). The instance
 * holds the compiled schema; it is immutable and safe to share.
 */
public final class RuleSetValidator {

    private final SchemaValidator schema = new SchemaValidator();
    private final RuleSetMapper mapper = new RuleSetMapper();
    private final SemanticValidator semantic = new SemanticValidator();
    private final StructuralChecks structural = new StructuralChecks();

    /**
     * @param document the rule set as a JSON tree, as {@link RuleSetMapper#readTree} returns it
     * @param context who produced the document
     * @param paragraphs the paragraphs of the policy version the rule set cites; paragraph {@code n} is at {@code n - 1}
     * @param modelRuleIds in {@link ValidationContext#CHANGE_PROPOSAL}, the rules the model patched; empty otherwise
     */
    public ValidationResult validate(
            JsonNode document, ValidationContext context, List<String> paragraphs, Set<String> modelRuleIds) {
        List<Finding> schemaFindings = schema.validate(document);
        if (!schemaFindings.isEmpty()) {
            return new ValidationResult(schemaFindings, null);
        }
        RuleSet ruleSet = mapper.toRuleSet(document);
        List<Finding> findings = new ArrayList<>(semantic.validate(ruleSet, context, paragraphs, modelRuleIds));
        // every semantic code is an error (Document 3), so any semantic finding stops the validator here
        if (findings.isEmpty()) {
            findings.addAll(structural.check(ruleSet));
        }
        return new ValidationResult(findings, ruleSet);
    }
}
