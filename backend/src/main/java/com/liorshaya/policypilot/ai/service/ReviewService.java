package com.liorshaya.policypilot.ai.service;

import com.liorshaya.policypilot.ai.Completion;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.LlmMalformedOutputException;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.adapter.ProviderSchemaVariant;
import com.liorshaya.policypilot.ai.prompt.PromptDefinition;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.ai.prompt.Sections;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.ruleset.service.FindingKind;
import com.liorshaya.policypilot.ruleset.service.Review;
import com.liorshaya.policypilot.ruleset.service.ReviewFinding;
import com.liorshaya.policypilot.ruleset.service.ReviewStatus;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The review use case (Document 4, Prompt 2: Review; Brief FR-5): a validated draft goes to the model next to the
 * policy it was written from, and what comes back is checked before anyone sees it. The answer must be an object with
 * a findings list; then each finding must match the Findings contract, pass the three rules the schema cannot express
 * and name only rules the draft has and paragraphs the policy has. A finding that fails is dropped and logged as a
 * reviewer error, never shown; the rest are numbered {@code F-1}, {@code F-2}, ... in the order the model gave them.
 * An answer that is not a findings list at all is forgotten by the cache, so it is never served again.
 *
 * <p>Nothing is stored here: the caller keeps the review with its draft (Document 2, Flow 1).
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SCHEMA_LOCATION = "classpath:schemas/findings-1.0.schema.json";

    private final LlmGateway gateway;
    private final PromptRegistry prompts;
    private final Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                    registry -> registry.schemaRegistryConfig(SchemaRegistryConfig.builder()
                            .locale(Locale.ENGLISH).build()))
            .getSchema(SchemaLocation.of(SCHEMA_LOCATION));

    public ReviewService(LlmGateway gateway, PromptRegistry prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    /**
     * Reviews one validated draft.
     *
     * @param policy the version the draft cites
     * @param title the policy's title, shown to the model in the data section
     * @param language the policy's language, which the messages are written in
     * @param draft the validated DSL document
     * @param fresh true to read the draft again rather than take a cached answer (Document 2, {@code POST .../review})
     * @throws com.liorshaya.policypilot.ai.LlmUnavailableException when the provider failed
     * @throws LlmMalformedOutputException when the answer was not an object with a findings list
     */
    public Reviewed review(PolicyVersionRef policy, String title, String language, JsonNode draft, boolean fresh) {
        PromptSpec spec = specFor(policy, title, language, draft);
        if (fresh) {
            gateway.forget(spec);
        }
        Completion<String> answer = gateway.complete(spec, String.class);
        JsonNode findings;
        try {
            findings = parse(answer.value());
        } catch (LlmMalformedOutputException e) {
            // Document 2, Flow 1: an answer the checks refuse is never served from the cache again
            gateway.forget(spec);
            throw e;
        }
        Set<String> ruleIds = ruleIds(draft);
        int paragraphs = policy.paragraphs().size();

        List<ReviewFinding> kept = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (JsonNode finding : findings.path("findings")) {
            Optional<String> problem = keepsTheContract(finding)
                    ? problemOf(finding, ruleIds, paragraphs)
                    : Optional.of("CONTRACT");
            if (problem.isPresent()) {
                dropped.add(problem.get());
                // Document 4: a finding whose anchors do not exist is a reviewer error for the evaluation
                log.atWarn().setMessage("ai.review.finding_dropped").addKeyValue("prompt", spec.promptVersion())
                        .addKeyValue("kind", finding.path("kind").asString("")).addKeyValue("reason", problem.get())
                        .log();
                continue;
            }
            kept.add(findingOf(finding, "F-" + (kept.size() + 1)));
        }
        Review review = new Review(ReviewStatus.DONE, spec.promptVersion(), kept,
                coverage(findings.path("coverage"), ruleIds, paragraphs));
        return new Reviewed(review, dropped);
    }

    /**
     * The call this draft would make, for a harness that records live answers: the recordings are keyed by the
     * rendered prompt, so they must be made from exactly the spec the service itself sends.
     */
    public PromptSpec specFor(PolicyVersionRef policy, String title, String language, JsonNode draft) {
        PromptDefinition review = prompts.get("review");
        String rendered = review.user().render(Map.of(
                "language", PromptRegistry.languageName(language),
                "title", Sections.escape(title),
                "paragraphCount", String.valueOf(policy.paragraphs().size()),
                "policy", numbered(policy),
                "ruleCount", String.valueOf(draft.path("rules").size()),
                "fieldCount", String.valueOf(draft.path("fields").size()),
                "draft", Sections.escape(compact(draft))));
        String system = review.system().render(Map.of("language", PromptRegistry.languageName(language)));
        return new PromptSpec(review.name(), review.version(), review.role(), system, rendered,
                review.outputSchema(), review.temperature(), review.maxOutputTokens(), review.timeout(), 1);
    }

    /** The prompt version the review would run with, for a review that failed before it had an answer. */
    public String promptVersion() {
        return prompts.get("review").version();
    }

    /**
     * Document 4, Output Contracts: the review fails only when the answer is not an object with a findings list; a
     * finding that breaks the contract is dropped later, one by one, and a misshapen coverage entry is dropped here.
     */
    private JsonNode parse(String answer) {
        JsonNode parsed;
        try {
            parsed = ProviderSchemaVariant.stripNulls(JSON.readTree(answer));
        } catch (RuntimeException e) {
            throw new LlmMalformedOutputException("the review answer was not JSON", answer, e);
        }
        if (!(parsed instanceof ObjectNode object) || !object.path("findings").isArray()) {
            throw new LlmMalformedOutputException("the review answer is not a findings list", answer, null);
        }
        if (!object.path("coverage").isObject()) {
            object.putObject("coverage");
        }
        dropMisshapenCoverage(object);
        return object;
    }

    /** Whether one finding matches the Findings contract, checked on its own so the others stand without it. */
    private boolean keepsTheContract(JsonNode finding) {
        ObjectNode alone = JSON.createObjectNode();
        alone.putArray("findings").add(finding);
        alone.putObject("coverage");
        return schema.validate(alone).isEmpty();
    }

    /**
     * Document 4, Output Contracts: a coverage entry that is not a list of rule ids is dropped and logged rather than
     * failing the review, because coverage only feeds the evaluation; the findings stay under the whole contract.
     */
    private static void dropMisshapenCoverage(ObjectNode answer) {
        if (!(answer.get("coverage") instanceof ObjectNode coverage)) {
            return;
        }
        List<String> misshapen = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : coverage.properties()) {
            JsonNode rules = entry.getValue();
            boolean listOfIds = rules.isArray() && rules.valueStream().allMatch(JsonNode::isString);
            if (!listOfIds) {
                misshapen.add(entry.getKey());
            }
        }
        if (!misshapen.isEmpty()) {
            log.atWarn().setMessage("ai.review.coverage_dropped").addKeyValue("paragraphs", misshapen).log();
            coverage.remove(misshapen);
        }
    }

    /**
     * Why a finding that keeps the contract still cannot be shown, or empty when it can (Document 4, Output Contracts
     * and Prompt 2).
     */
    private static Optional<String> problemOf(JsonNode finding, Set<String> ruleIds, int paragraphs) {
        FindingKind kind = FindingKind.of(finding.required("kind").asString());
        String severity = finding.required("severity").asString();
        List<String> rules = strings(finding.path("ruleIds"));
        List<Integer> anchors = integers(finding.path("paragraphIndexes"));
        if (!kind.severity().equals(severity)) {
            return Optional.of("SEVERITY_NOT_OF_KIND");
        }
        if (kind == FindingKind.INJECTION && anchors.isEmpty()) {
            return Optional.of("INJECTION_WITHOUT_PARAGRAPH");
        }
        if (rules.isEmpty() && anchors.isEmpty()) {
            return Optional.of("NO_ANCHOR");
        }
        if (!ruleIds.containsAll(rules)) {
            return Optional.of("UNKNOWN_RULE");
        }
        if (anchors.stream().anyMatch(paragraph -> paragraph > paragraphs)) {
            return Optional.of("UNKNOWN_PARAGRAPH");
        }
        return Optional.empty();
    }

    private static ReviewFinding findingOf(JsonNode finding, String id) {
        return new ReviewFinding(id, FindingKind.of(finding.required("kind").asString()),
                strings(finding.path("ruleIds")), integers(finding.path("paragraphIndexes")),
                finding.required("message").asString(), finding.required("suggestion").asString(),
                finding.required("confidence").asDouble(), null);
    }

    /** The coverage map with only the paragraphs and rules that exist, in the reviewer's order. */
    private static Map<String, List<String>> coverage(JsonNode coverage, Set<String> ruleIds, int paragraphs) {
        Map<String, List<String>> kept = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : coverage.properties()) {
            Integer paragraph = paragraphNumber(entry.getKey());
            if (paragraph != null && paragraph >= 1 && paragraph <= paragraphs) {
                kept.put(entry.getKey(), strings(entry.getValue()).stream().filter(ruleIds::contains).toList());
            }
        }
        return kept;
    }

    private static Integer paragraphNumber(String key) {
        try {
            return Integer.valueOf(key);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The draft as Document 4 asks for it: compact JSON, one line per field and per rule, the rule's id first, so a
     * rule id is the first thing on every line the reviewer anchors to.
     */
    static String compact(JsonNode draft) {
        StringBuilder text = new StringBuilder();
        ObjectNode head = JSON.createObjectNode();
        head.set("id", draft.path("id"));
        head.set("name", draft.path("name"));
        head.set("defaults", draft.path("defaults"));
        text.append(head).append('\n');
        for (JsonNode field : draft.path("fields")) {
            text.append(field).append('\n');
        }
        for (JsonNode rule : draft.path("rules")) {
            ObjectNode ordered = JSON.createObjectNode();
            ordered.set("id", rule.path("id"));
            rule.properties().forEach(entry -> {
                if (!entry.getKey().equals("id")) {
                    ordered.set(entry.getKey(), entry.getValue());
                }
            });
            text.append(ordered).append('\n');
        }
        return text.toString().stripTrailing();
    }

    /** The paragraphs with the {@code [n]} prefix the findings anchor to (Document 4, Data delimiters). */
    private static String numbered(PolicyVersionRef policy) {
        StringBuilder text = new StringBuilder();
        for (PolicyVersionRef.Paragraph paragraph : policy.paragraphs()) {
            text.append('[').append(paragraph.index()).append("] ").append(Sections.escape(paragraph.text()))
                    .append('\n');
        }
        return text.toString().stripTrailing();
    }

    private static Set<String> ruleIds(JsonNode draft) {
        Set<String> ids = new HashSet<>();
        draft.path("rules").forEach(rule -> ids.add(rule.path("id").asString("")));
        return ids;
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

    /** What the review answers: the review to store, and why each dropped finding was dropped. */
    public record Reviewed(Review review, List<String> dropped) {

        public Reviewed {
            dropped = List.copyOf(dropped);
        }
    }
}
