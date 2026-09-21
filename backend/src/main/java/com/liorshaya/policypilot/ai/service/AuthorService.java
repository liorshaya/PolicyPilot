package com.liorshaya.policypilot.ai.service;

import com.liorshaya.policypilot.ai.Completion;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.LlmMalformedOutputException;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.adapter.ProviderSchemaVariant;
import com.liorshaya.policypilot.ai.prompt.DslCheatSheet;
import com.liorshaya.policypilot.ai.prompt.PromptDefinition;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.ai.prompt.Sections;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.validation.Finding;
import com.liorshaya.policypilot.rules.validation.ValidationCode;
import com.liorshaya.policypilot.rules.validation.RuleSetValidator;
import com.liorshaya.policypilot.rules.validation.Severity;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.rules.validation.ValidationResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The authoring use case (Document 4, Prompt 1: Author, and Repair Loop; Brief FR-2 and FR-3): the policy's
 * paragraphs go to the model, what comes back is validated by the same validator an analyst's edit passes, and a
 * document with errors goes back to the model with the exact error list, at most twice.
 *
 * <p>Nothing here decides anything or stores anything: the service answers a draft document and its findings, and
 * the caller stores it. A third failure answers the error list and the last document, so an analyst can see what
 * went wrong.
 */
@Service
public class AuthorService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final LlmGateway gateway;
    private final PromptRegistry prompts;
    private final DslCheatSheet cheatSheet;
    /** The same validator an analyst's edit passes; it holds the compiled schema and is safe to share. */
    private final RuleSetValidator validator = new RuleSetValidator();

    public AuthorService(LlmGateway gateway, PromptRegistry prompts, DslCheatSheet cheatSheet) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.cheatSheet = cheatSheet;
    }

    /**
     * Writes a rule set for one policy version.
     *
     * @param policy the version whose paragraphs the rules must cite
     * @param title the policy's title, shown to the model in the data section
     * @param language the policy's language, which the labels and reasons are written in
     * @param hints what the analyst added, or null
     * @param progress told when each stage begins, so the caller can stream it
     */
    public Authored write(PolicyVersionRef policy, String title, String language, @Nullable String hints,
            Consumer<Stage> progress) {
        PromptDefinition author = prompts.get("author");
        progress.accept(Stage.AUTHORING);
        PromptSpec spec = specOf(author, policy, title, language, hints);
        Answer answer = ask(spec);

        progress.accept(Stage.VALIDATING);
        ValidationResult result = answer.validate(validator, policy);
        int repairs = 0;
        while (result.hasErrors() && repairs < author.repairs()) {
            repairs++;
            spec = spec.repairedWith(repairPrompt(answer, result.findings(), policy));
            answer = ask(spec);
            result = answer.validate(validator, policy);
        }
        if (answer.document() == null) {
            // three answers that were not JSON at all: there is no document to show, so the failure is the answer
            throw new LlmMalformedOutputException(
                    "the model did not answer with JSON after " + (repairs + 1) + " attempts", answer.raw(), null);
        }
        return new Authored(answer.document(), result.findings(), repairs, !result.hasErrors());
    }

    /** One answer from the model: the parsed document, or the raw text when it was not JSON at all. */
    private record Answer(@Nullable JsonNode document, String raw) {

        ValidationResult validate(RuleSetValidator validator, PolicyVersionRef policy) {
            if (document == null) {
                // Document 4: an answer that is not valid JSON counts as a validation failure and is repaired
                return new ValidationResult(List.of(new Finding(ValidationCode.DSL_SCHEMA, "",
                        "the answer was not a JSON object", List.of(), List.of())), null);
            }
            return validator.validate(document, ValidationContext.AUTHORING, policy.texts(), Set.of());
        }
    }

    private Answer ask(PromptSpec spec) {
        Completion<String> answer = gateway.complete(spec, String.class);
        try {
            // a strict structured-output mode makes the model fill every optional property, and a null is how it
            // says "absent"; the canonical schema never asked for them (Document 4, Output discipline)
            JsonNode parsed = ProviderSchemaVariant.stripNulls(JSON.readTree(answer.value()));
            return new Answer(parsed.isObject() ? parsed : null, answer.value());
        } catch (RuntimeException e) {
            return new Answer(null, answer.value());
        }
    }

    /**
     * The first call this policy would make, for a harness that records live answers: the recordings are keyed by
     * the rendered prompt, so they must be made from exactly the spec the service itself would send.
     */
    public PromptSpec specFor(PolicyVersionRef policy, String title, String language, @Nullable String hints) {
        return specOf(prompts.get("author"), policy, title, language, hints);
    }

    private PromptSpec specOf(PromptDefinition author, PolicyVersionRef policy, String title, String language,
            @Nullable String hints) {
        String rendered = author.user().render(Map.of(
                "cheatsheet", cheatSheet.text(),
                "examples", author.examples() == null ? "" : author.examples(),
                "language", PromptRegistry.languageName(language),
                "title", Sections.escape(title),
                "paragraphCount", String.valueOf(policy.paragraphs().size()),
                "policy", numbered(policy),
                "hints", hints == null || hints.isBlank() ? "" : "<hints>\n" + Sections.escape(hints) + "\n</hints>"));
        String system = author.system().render(Map.of("language", PromptRegistry.languageName(language)));
        return new PromptSpec(author.name(), author.version(), author.role(), system, rendered,
                author.outputSchema(), author.temperature(), author.maxOutputTokens(), author.timeout(), 1);
    }

    /** The paragraphs with the {@code [n]} prefix the prompt cites by (Document 4, Data delimiters). */
    private static String numbered(PolicyVersionRef policy) {
        StringBuilder text = new StringBuilder();
        for (PolicyVersionRef.Paragraph paragraph : policy.paragraphs()) {
            text.append('[').append(paragraph.index()).append("] ").append(Sections.escape(paragraph.text()))
                    .append('\n');
        }
        return text.toString().stripTrailing();
    }


    /** The repair user prompt of Document 4, with only the errors and the paragraphs their quotes came from. */
    private String repairPrompt(Answer answer, List<Finding> findings, PolicyVersionRef policy) {
        List<Finding> errors = findings.stream().filter(finding -> finding.severity() == Severity.ERROR).toList();
        JsonNode document = answer.document();
        return prompts.get("repair").user().render(Map.of(
                "count", String.valueOf(errors.size()),
                "errors", errorList(errors),
                "paragraphTexts", paragraphsFor(errors, document, policy),
                "document", document == null ? Sections.escape(answer.raw()) : document.toString()));
    }

    private static String errorList(List<Finding> errors) {
        StringBuilder text = new StringBuilder();
        for (Finding error : errors) {
            ObjectNode entry = JSON.createObjectNode();
            entry.put("code", error.code().name());
            entry.put("severity", error.severity().name().toLowerCase(java.util.Locale.ROOT));
            entry.put("path", error.path());
            entry.put("message", error.message());
            entry.set("ruleIds", JSON.valueToTree(error.ruleIds()));
            entry.set("fieldNames", JSON.valueToTree(error.fieldNames()));
            text.append(entry).append('\n');
        }
        return text.toString().stripTrailing();
    }

    /**
     * The full text of every paragraph a failing rule cites, which is what makes the second attempt succeed
     * almost always (Document 4, Error list shape).
     */
    private static String paragraphsFor(
            List<Finding> errors, @Nullable JsonNode document, PolicyVersionRef policy) {
        Set<String> failing = new LinkedHashSet<>();
        errors.forEach(error -> failing.addAll(error.ruleIds()));
        List<Integer> cited = new ArrayList<>();
        JsonNode rules = document == null ? JSON.createArrayNode() : document.path("rules");
        for (JsonNode rule : rules) {
            if (failing.contains(rule.path("id").asString("")) ) {
                JsonNode paragraph = rule.path("provenance").path("paragraph");
                if (paragraph.isInt() && !cited.contains(paragraph.asInt())) {
                    cited.add(paragraph.asInt());
                }
            }
        }
        if (cited.isEmpty()) {
            policy.paragraphs().forEach(paragraph -> cited.add(paragraph.index()));
        }
        StringBuilder text = new StringBuilder();
        for (PolicyVersionRef.Paragraph paragraph : policy.paragraphs()) {
            if (cited.contains(paragraph.index())) {
                text.append('[').append(paragraph.index()).append("] ")
                        .append(Sections.escape(paragraph.text())).append('\n');
            }
        }
        return text.toString().stripTrailing();
    }

    /** What the authoring pipeline answers: the document, its findings and how it got there. */
    public record Authored(JsonNode document, List<Finding> findings, int repairs, boolean valid) {

        public Authored {
            findings = List.copyOf(findings);
        }
    }

    /** The stages the generation stream reports (Document 2, API Surface). */
    public enum Stage {
        PARSING,
        AUTHORING,
        VALIDATING
    }
}
