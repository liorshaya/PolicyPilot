package com.liorshaya.policypilot.rules.patch;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.validation.Finding;
import com.liorshaya.policypilot.rules.validation.PatchSchemaValidator;
import com.liorshaya.policypilot.rules.validation.RuleSetValidator;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.rules.validation.ValidationResult;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Document 3, Patch validation: a proposal's Patches object through the schema, the proposal validator and the
 * application to a copy of the version, then the copy through the rule set validator in the CHANGE_PROPOSAL context
 * with the added and replaced rules as the model's. It stops at the first step that fails, as the rule set validator
 * stops at its first failing layer. A finding on a patched rule or an added field is reported at its patch, where the
 * model and the analyst can act on it; any other keeps its path in the copy. The instance holds the compiled schemas;
 * it is immutable and safe to share.
 */
public final class PatchValidator {

    /** A path inside one rule or one field of the copy: the collection, the index, and the rest. */
    private static final Pattern ITEM = Pattern.compile("/(rules|fields)/([0-9]+)(.*)");

    private final PatchSchemaValidator schema = new PatchSchemaValidator();
    private final RuleSetValidator ruleSets = new RuleSetValidator();
    private final RuleSetMapper mapper = new RuleSetMapper();

    /**
     * @param patches the Patches object the model answered, nulls stripped
     * @param base the document of the version the change applies to; it is not changed
     * @param paragraphs the paragraphs of the policy version the base cites; paragraph {@code n} is at {@code n - 1}
     * @param scope what the model was shown and asked
     */
    public PatchValidation validate(JsonNode patches, ObjectNode base, List<String> paragraphs, Scope scope) {
        List<Finding> schemaFindings = schema.validate(patches);
        if (!schemaFindings.isEmpty()) {
            return new PatchValidation(schemaFindings, List.of(), null, Set.of(), List.of());
        }
        List<PatchProblem> refusals = ProposalValidator.refusals(patches, mapper.toRuleSet(base),
                Mentions.of(scope.request()), scope.candidates());
        if (!refusals.isEmpty()) {
            return new PatchValidation(List.of(), refusals, null, Set.of(), List.of());
        }
        PatchApplier.Applied applied = PatchApplier.apply(patches, base, scope.retiredIds());
        if (!applied.problems().isEmpty()) {
            return new PatchValidation(List.of(), applied.problems(), null, Set.of(), List.of());
        }
        ValidationResult copy = ruleSets.validate(applied.document(), ValidationContext.CHANGE_PROPOSAL, paragraphs,
                applied.ruleAt().keySet());
        List<Finding> findings = copy.findings().stream().map(finding -> atItsPatch(finding, applied)).toList();
        return new PatchValidation(findings, List.of(), applied.document(), applied.ruleAt().keySet(),
                applied.retired());
    }

    /**
     * What the model was shown and asked (Document 4, Prompt 5).
     *
     * @param request the change request as the analyst wrote it
     * @param candidates the candidate rules the prompt listed
     * @param retiredIds the rule ids the lineage has retired
     */
    public record Scope(String request, Set<String> candidates, Set<String> retiredIds) {

        public Scope {
            candidates = Set.copyOf(candidates);
            retiredIds = Set.copyOf(retiredIds);
        }
    }

    /**
     * The finding at the patch that wrote its rule or field, when a patch did: {@code /rules/12/condition} is
     * {@code /patches/0/rule/condition} when patch 0 wrote the rule at index 12. Otherwise it keeps its path.
     */
    private static Finding atItsPatch(Finding finding, PatchApplier.Applied applied) {
        Matcher item = ITEM.matcher(finding.path());
        if (!item.matches()) {
            return finding;
        }
        boolean rule = item.group(1).equals("rules");
        JsonNode written = applied.document().required(item.group(1)).path(Integer.parseInt(item.group(2)));
        Integer patch = (rule ? applied.ruleAt() : applied.fieldAt()).get(written.path(rule ? "id" : "name")
                .asString(""));
        if (patch == null) {
            return finding;
        }
        return new Finding(finding.code(), "/patches/" + patch + (rule ? "/rule" : "/field") + item.group(3),
                finding.message(), finding.ruleIds(), finding.fieldNames());
    }
}
