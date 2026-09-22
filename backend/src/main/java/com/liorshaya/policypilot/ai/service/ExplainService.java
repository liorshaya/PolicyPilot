package com.liorshaya.policypilot.ai.service;

import com.liorshaya.policypilot.ai.Completion;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.LlmMalformedOutputException;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.adapter.ProviderSchemaVariant;
import com.liorshaya.policypilot.ai.prompt.PromptDefinition;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.ai.prompt.Sections;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The explain use case (Document 4, Prompt 3: Explain; Brief FR-11): one decision object goes to the model and
 * nothing else, and every entry that comes back is checked against the trace (Document 2, NFR-2 Explainability). A
 * factor must name a rule that fired and that rule's own paragraph; a rule not applied must have been evaluated and
 * not fired; a condition must be a flag the decision carries. An entry that fails is dropped and logged, so a skipped
 * rule or an invented paragraph never reaches the reader.
 *
 * <p>The decision object carries no id, so the rendered prompt, and with it the cached answer, is the same for the
 * same trace in every sandbox (Document 4, Prompt 3, Caching).
 */
@Service
public class ExplainService {

    private static final Logger log = LoggerFactory.getLogger(ExplainService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SCHEMA_LOCATION = "classpath:schemas/explanation-1.0.schema.json";

    private final LlmGateway gateway;
    private final PromptRegistry prompts;
    private final Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                    registry -> registry.schemaRegistryConfig(SchemaRegistryConfig.builder()
                            .locale(Locale.ENGLISH).build()))
            .getSchema(SchemaLocation.of(SCHEMA_LOCATION));

    public ExplainService(LlmGateway gateway, PromptRegistry prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    /** Who reads the explanation (Document 4, Prompt 3): an officer sees ids and values, an applicant plain text. */
    public enum Audience {
        OFFICER,
        APPLICANT;

        public String json() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The audience by its API name, or null for a name that is not one. */
        public static @Nullable Audience of(String json) {
            for (Audience audience : values()) {
                if (audience.json().equals(json)) {
                    return audience;
                }
            }
            return null;
        }
    }

    /**
     * Explains one decision.
     *
     * @param decision the decision object exactly as the engine emitted it (Document 3), without its row id
     * @param audience who reads it
     * @param language the rule set's language, which the explanation is written in
     * @throws com.liorshaya.policypilot.ai.LlmUnavailableException when the provider failed
     * @throws LlmMalformedOutputException when the answer was not an Explanation
     */
    public Explained explain(ObjectNode decision, Audience audience, String language) {
        PromptSpec spec = specFor(decision, audience, language);
        Completion<String> answer = gateway.complete(spec, String.class);
        JsonNode explanation = parse(answer.value());
        Trace trace = Trace.of(decision);

        List<String> dropped = new ArrayList<>();
        List<Factor> factors = new ArrayList<>();
        for (JsonNode factor : explanation.path("factors")) {
            String ruleId = factor.required("ruleId").asString();
            JsonNode paragraph = factor.path("paragraph");
            Integer cited = paragraph.isInt() ? paragraph.asInt() : null;
            if (!"fired".equals(trace.statusOf(ruleId))) {
                dropped.add(drop("factors", ruleId, "NOT_FIRED", spec));
            } else if (!Objects.equals(cited, trace.paragraphOf(ruleId))) {
                dropped.add(drop("factors", ruleId, "PARAGRAPH_NOT_THE_RULES", spec));
            } else {
                factors.add(new Factor(ruleId, cited, factor.required("statement").asString()));
            }
        }
        List<NotApplied> notApplied = new ArrayList<>();
        for (JsonNode entry : explanation.path("notApplied")) {
            String ruleId = entry.required("ruleId").asString();
            if (!"not_fired".equals(trace.statusOf(ruleId))) {
                dropped.add(drop("notApplied", ruleId, "NOT_EVALUATED_AND_NOT_FIRED", spec));
            } else {
                notApplied.add(new NotApplied(ruleId, entry.required("statement").asString()));
            }
        }
        List<Condition> conditions = new ArrayList<>();
        for (JsonNode entry : explanation.path("conditions")) {
            String flagCode = entry.required("flagCode").asString();
            if (!trace.flags().contains(flagCode)) {
                dropped.add(drop("conditions", flagCode, "NOT_A_FLAG_OF_THE_DECISION", spec));
            } else {
                conditions.add(new Condition(flagCode, entry.required("statement").asString()));
            }
        }
        return new Explained(new Explanation(explanation.required("summary").asString(), factors, conditions,
                notApplied, language), spec.promptVersion(), dropped);
    }

    /**
     * The call this decision would make, for a harness that records live answers and for the cache key: the prompt
     * holds the decision object, the audience and the language, and nothing else.
     */
    public PromptSpec specFor(ObjectNode decision, Audience audience, String language) {
        PromptDefinition explain = prompts.get("explain");
        String rendered = explain.user().render(Map.of(
                "audience", audience.json(),
                "language", PromptRegistry.languageName(language),
                "decision", Sections.escape(decision.toString())));
        String system = explain.system().render(Map.of("language", PromptRegistry.languageName(language)));
        return new PromptSpec(explain.name(), explain.version(), explain.role(), system, rendered,
                explain.outputSchema(), explain.temperature(), explain.maxOutputTokens(), explain.timeout(), 1);
    }

    private JsonNode parse(String answer) {
        JsonNode parsed;
        try {
            parsed = ProviderSchemaVariant.stripNulls(JSON.readTree(answer));
        } catch (RuntimeException e) {
            throw new LlmMalformedOutputException("the explanation was not JSON", answer, e);
        }
        restoreNullParagraphs(parsed);
        if (!parsed.isObject() || !schema.validate(parsed).isEmpty()) {
            throw new LlmMalformedOutputException("the answer is not an Explanation", answer, null);
        }
        return parsed;
    }

    /**
     * A factor of an analyst rule cites no paragraph, and its {@code "paragraph": null} is part of the contract; the
     * null stripping every structured answer passes through removes it, so it is put back before the schema check.
     */
    private static void restoreNullParagraphs(JsonNode parsed) {
        for (JsonNode factor : parsed.path("factors")) {
            if (factor instanceof ObjectNode object && !object.has("paragraph")) {
                object.putNull("paragraph");
            }
        }
    }

    private static String drop(String section, String id, String reason, PromptSpec spec) {
        // Document 4, Prompt 3: "a violation drops the offending entry and logs it"
        log.atWarn().setMessage("ai.explain.entry_dropped").addKeyValue("prompt", spec.promptVersion())
                .addKeyValue("section", section).addKeyValue("id", id).addKeyValue("reason", reason).log();
        return section + ":" + id + ":" + reason;
    }

    /** What the trace says about each rule, and the flags the decision carries. */
    private record Trace(Map<String, String> status, Map<String, Integer> paragraph, Set<String> flags) {

        static Trace of(ObjectNode decision) {
            Map<String, String> status = new HashMap<>();
            Map<String, Integer> paragraph = new HashMap<>();
            for (JsonNode step : decision.path("trace")) {
                String ruleId = step.path("ruleId").asString("");
                status.put(ruleId, step.path("status").asString(""));
                JsonNode cited = step.path("provenance").path("paragraph");
                paragraph.put(ruleId, cited.isInt() ? cited.asInt() : null);
            }
            Set<String> flags = new HashSet<>();
            decision.path("flags").forEach(flag -> flags.add(flag.path("code").asString("")));
            return new Trace(status, paragraph, flags);
        }

        @Nullable String statusOf(String ruleId) {
            return status.get(ruleId);
        }

        @Nullable Integer paragraphOf(String ruleId) {
            return paragraph.get(ruleId);
        }
    }

    /** A rule that fired, the paragraph its provenance cites (null for an analyst rule), and one sentence. */
    public record Factor(String ruleId, @Nullable Integer paragraph, String statement) {}

    /** A flag the decision carries, as a condition a person still checks. */
    public record Condition(String flagCode, String statement) {}

    /** A rule that was evaluated and did not fire, when it helps the reader. */
    public record NotApplied(String ruleId, String statement) {}

    /** The explanation after the trace checks (Document 4, Explanation contract). */
    public record Explanation(String summary, List<Factor> factors, List<Condition> conditions,
            List<NotApplied> notApplied, String language) {

        public Explanation {
            factors = List.copyOf(factors);
            conditions = List.copyOf(conditions);
            notApplied = List.copyOf(notApplied);
        }
    }

    /** What the explain use case answers: the explanation, the prompt version, and what was dropped and why. */
    public record Explained(Explanation explanation, String promptVersion, List<String> dropped) {

        public Explained {
            dropped = List.copyOf(dropped);
        }
    }
}
