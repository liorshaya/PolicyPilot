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
import com.liorshaya.policypilot.common.Hashes;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.patch.PatchValidation;
import com.liorshaya.policypilot.rules.patch.PatchValidator;
import com.liorshaya.policypilot.rules.validation.Finding;
import com.liorshaya.policypilot.rules.validation.Severity;
import com.liorshaya.policypilot.rules.validation.ValidationCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The change use case (Document 4, Prompt 5: Change, and Repair Loop; Brief FR-17): the request and its candidate
 * rules go to the model, the answer goes through Patch validation (Document 3), and an answer with errors goes back
 * with the exact error list, at most twice. A refusal of the proposal validator is final: repairing it would drop what
 * the request smuggled in before the analyst saw it (Document 5, RT-04).
 *
 * <p>Nothing here decides or stores anything: the service answers a proposal and the caller stores it. Every answer of
 * a proposal that was refused or never became valid is forgotten from the response cache, so asking again asks the
 * model again (Document 4, After the prompt).
 */
@Service
public class ChangeService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PROMPT = "change";
    private static final RuleSetMapper RULES = new RuleSetMapper();

    private final LlmGateway gateway;
    private final PromptRegistry prompts;
    private final DslCheatSheet cheatSheet;
    /** Patch validation; it holds the compiled schemas and is safe to share. */
    private final PatchValidator validator = new PatchValidator();

    public ChangeService(LlmGateway gateway, PromptRegistry prompts, DslCheatSheet cheatSheet) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.cheatSheet = cheatSheet;
    }

    /**
     * Proposes patches for a request.
     *
     * @param base the published version the request is against
     * @param request the request as the analyst wrote it, normalized
     * @param candidates the rules candidate selection found; the model sees these and no others
     * @param progress told when each stage begins, so the caller can stream it
     * @throws LlmMalformedOutputException when no answer, repairs included, was a JSON object
     */
    public Proposal propose(ChangeBase base, String request, Candidates candidates, Consumer<Stage> progress) {
        PromptDefinition change = prompts.get(PROMPT);
        progress.accept(Stage.PROPOSING);
        PromptSpec spec = specOf(change, base, request, candidates);
        List<PromptSpec> asked = new ArrayList<>(List.of(spec));
        Answer answer = ask(spec);

        progress.accept(Stage.VALIDATING);
        PatchValidator.Scope scope = new PatchValidator.Scope(request, Set.copyOf(candidates.ruleIds()),
                base.retiredIds());
        PatchValidation validation = answer.validate(validator, base, scope);
        int repairs = 0;
        while (!validation.valid() && !validation.refused() && repairs < change.repairs()) {
            repairs++;
            spec = spec.repairedWith(repairPrompt(answer, validation, base));
            asked.add(spec);
            answer = ask(spec);
            validation = answer.validate(validator, base, scope);
        }
        if (!validation.valid()) {
            asked.forEach(gateway::forget);
        }
        JsonNode document = answer.document();
        if (document == null) {
            throw new LlmMalformedOutputException(
                    "the model did not answer with JSON after " + (repairs + 1) + " attempts", answer.raw(), null);
        }
        return new Proposal(candidates, document, validation, repairs);
    }

    /**
     * The first call a request would make, for a harness that records live answers: the recordings are keyed by the
     * rendered prompt, so they must be made from exactly the spec the service itself would send.
     */
    public PromptSpec specFor(ChangeBase base, String request, Candidates candidates) {
        return specOf(prompts.get(PROMPT), base, request, candidates);
    }

    /** One answer from the model: the parsed Patches object, or the raw text when it was not a JSON object. */
    private record Answer(@Nullable JsonNode document, String raw) {

        PatchValidation validate(PatchValidator validator, ChangeBase base, PatchValidator.Scope scope) {
            if (document == null) {
                // Document 4: an answer that is not valid JSON counts as a validation failure and is repaired
                return new PatchValidation(List.of(new Finding(ValidationCode.DSL_SCHEMA, "",
                        "the answer was not a JSON object", List.of(), List.of())), List.of(), null, Set.of(),
                        List.of());
            }
            return validator.validate(document, base.document(),
                    base.paragraphs().stream().map(PolicyVersionRef.Paragraph::text).toList(), scope);
        }
    }

    private Answer ask(PromptSpec spec) {
        Completion<String> answer = gateway.complete(spec, String.class);
        try {
            // the provider's variant makes every optional property nullable, and a null is how the model says
            // "absent"; the canonical schema never asked for them (Document 4, Output discipline)
            JsonNode parsed = ProviderSchemaVariant.stripNulls(JSON.readTree(answer.value()));
            return new Answer(parsed.isObject() ? parsed : null, answer.value());
        } catch (RuntimeException e) {
            return new Answer(null, answer.value());
        }
    }

    private PromptSpec specOf(PromptDefinition change, ChangeBase base, String request, Candidates candidates) {
        String language = PromptRegistry.languageName(base.ruleSet().language().json());
        // the version as the DSL writes it, not in the order its store gave the keys back (jsonb reorders them), so
        // the prompt, its cache key and its recordings are the same whether the version came from the database
        ObjectNode written = RULES.toJson(base.ruleSet());
        String user = change.user().render(Map.ofEntries(
                Map.entry("cheatsheet", cheatSheet.text()),
                Map.entry("language", language),
                Map.entry("title", Sections.escape(base.title())),
                Map.entry("paragraphCount", String.valueOf(base.paragraphs().size())),
                Map.entry("policy", Sections.numbered(base.paragraphs())),
                Map.entry("fields", lines(written.required("fields"))),
                Map.entry("defaults", Sections.escape(written.required("defaults").toString())),
                Map.entry("candidateCount", String.valueOf(candidates.ruleIds().size())),
                Map.entry("versionNo", String.valueOf(base.versionNo())),
                Map.entry("candidates", candidateLines(written.required("rules"), candidates.ruleIds())),
                Map.entry("retiredIds", String.join("\n", new TreeSet<>(base.retiredIds()))),
                Map.entry("changeRequestId", requestKey(request)),
                Map.entry("request", Sections.escape(request))));
        String system = change.system().render(Map.of("language", language));
        return new PromptSpec(change.name(), change.version(), change.role(), system, user, change.outputSchema(),
                change.temperature(), change.maxOutputTokens(), change.timeout(), 1);
    }

    /**
     * The id the prompt gives the request: a key of its text, not the stored request's id, so the same request on the
     * same version renders the same prompt in every sandbox and the response cache serves it (Document 4).
     */
    static String requestKey(String request) {
        return "cr-" + Hashes.sha256Hex(request).substring(0, 8);
    }

    /** One JSON document per line, as a data section writes them (Document 4, Data delimiters). */
    private static String lines(JsonNode items) {
        List<String> lines = new ArrayList<>();
        items.forEach(item -> lines.add(Sections.escape(item.toString())));
        return String.join("\n", lines);
    }

    /** The candidate rules as the DSL writes them, one per line, in the order candidate selection gave them. */
    private static String candidateLines(JsonNode rules, List<String> candidates) {
        Map<String, JsonNode> byId = new HashMap<>();
        rules.forEach(rule -> byId.put(rule.required("id").asString(), rule));
        return candidates.stream().map(byId::get).map(rule -> Sections.escape(rule.toString()))
                .collect(Collectors.joining("\n"));
    }

    /** The repair user prompt of Document 4 with the errors of this answer: its schema, patch and copy errors. */
    private String repairPrompt(Answer answer, PatchValidation validation, ChangeBase base) {
        List<RepairPrompt.Error> errors = new ArrayList<>();
        validation.findings().stream().filter(finding -> finding.severity() == Severity.ERROR)
                .map(RepairPrompt.Error::of).forEach(errors::add);
        validation.problems().stream().map(RepairPrompt.Error::of).forEach(errors::add);
        JsonNode document = answer.document();
        List<JsonNode> rules = new ArrayList<>();
        if (document != null) {
            document.path("patches").forEach(patch -> rules.add(patch.path("rule")));
        }
        return RepairPrompt.render(prompts.get("repair"), errors, rules, base.paragraphs(),
                document == null ? Sections.escape(answer.raw()) : document.toString());
    }

    /** The stages the change stream reports after its own {@code analyzing} (Document 2, API Surface). */
    public enum Stage {
        PROPOSING,
        VALIDATING
    }
}
