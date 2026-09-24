package com.liorshaya.policypilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.service.AuthorService;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.eval.FieldHints;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.validation.RuleSetValidator;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.rules.validation.ValidationResult;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.LiveProvider;
import com.liorshaya.policypilot.support.LiveRecordingGateway;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The author pass of evaluation run 2 (Work Plan day 15; Document 4, Evaluation Set and Metrics): each of the 18
 * labeled policies authored once, live, by the active author prompt with its field hints (Document 4, Field hints),
 * and the lending policy once more without hints, the render of every test that pastes a policy and asks for no more.
 * Each answer is recorded under {@code fixtures/eval/recordings/<provider>/author/<version>/}, which is what
 * {@code EvalRunnerIT} scores and every other test replays; a render already recorded is not asked again, so a pass
 * that stopped pays only for what is missing.
 *
 * <p>It is tagged {@code live}, so CI never runs it. The provider is {@code -Dprovider} (Document 4: a column each):
 * {@code openai} needs a real {@code OPENAI_API_KEY}, {@code ollama} a local Ollama at {@code OLLAMA_BASE_URL} with the
 * profile's models pulled. {@code live.budget} caps what the pass may spend, the day's budget when it is not given; a
 * call that could cross it is not asked:
 *
 * <pre>{@code
 * OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test=LiveAuthorPassIT -Dlive.tag= -Dlive.budget=360000 \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
 * }</pre>
 */
@Tag("live")
@ActiveProfiles(resolver = LiveProvider.class, inheritProfiles = false)
@TestPropertySource(properties = {"spring.ai.openai.api-key=${OPENAI_API_KEY:not-a-real-key}",
        "policypilot.ai.daily-token-budget=400000"})
@Isolated
class LiveAuthorPassIT extends ApiIntegrationTest {

    /**
     * Fewer characters per input token than the author prompt measured: Document 4 gives about 12,000 input tokens for
     * the lending render of 12,737 characters, most of them Hebrew and JSON. So the estimate is high.
     */
    private static final double CHARACTERS_PER_TOKEN = 1.0;
    /** More output than an authoring measured: 6,379 document and 2,184 reasoning tokens (author/prompt.yml). */
    private static final int OUTPUT_ALLOWANCE = 10_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private LlmGateway gateway;

    @Autowired
    private AuthorService author;

    @Autowired
    private PolicyPilotProperties properties;

    private final RuleSetValidator validator = new RuleSetValidator();

    @Test
    void everyLabeledPolicyIsAuthoredLiveWithItsFieldHintsAndRecorded() {
        LiveRecordingGateway live = new LiveRecordingGateway(LiveProvider.name(), gateway,
                properties.ai().models().strong(),
                Long.getLong("live.budget", properties.ai().dailyTokenBudget()), CHARACTERS_PER_TOKEN,
                OUTPUT_ALLOWANCE);
        Map<String, String> verdicts = new LinkedHashMap<>();
        List<String> expectedFiles = new ArrayList<>();

        for (String slug : Fixtures.evaluationPolicies()) {
            JsonNode label = Fixtures.json("eval/policies/" + slug + "/expected.ruleset.json");
            verdicts.put(slug, authorOnce(live, slug, label, FieldHints.of(label), expectedFiles));
        }
        verdicts.put("consumer-lending, no hints", authorOnce(live, "consumer-lending",
                Fixtures.json("eval/policies/consumer-lending/expected.ruleset.json"), null, expectedFiles));

        System.out.println("author pass: " + verdicts);
        System.out.println("spent: " + live.spent());
        assertThat(expectedFiles).as("every render recorded").allMatch(file -> Files.exists(Path.of(file)));
    }

    /** One render: asked when it is not recorded yet, and its verdict, VALID or the first validator code. */
    private String authorOnce(LiveRecordingGateway live, String slug, JsonNode label, @Nullable String hints,
            List<String> files) {
        PolicyVersionRef policy = policyOf(slug);
        PromptSpec spec = author.specFor(policy, label.required("name").stringValue(),
                label.required("language").stringValue(), hints);
        files.add(LiveRecordingGateway.fileOf(LiveProvider.name(), spec).toString());
        if (Files.exists(LiveRecordingGateway.fileOf(LiveProvider.name(), spec))) {
            return "recorded before";
        }
        try {
            return verdict(live.complete(spec, String.class).value(), policy);
        } catch (RuntimeException e) {
            // a timeout, a cut-off answer or the budget: the pass goes on, and a rerun asks only what is missing. The
            // cause is named by its class only: a provider's refusal of a key quotes part of the key in its message
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return "NOT RECORDED: " + e.getClass().getSimpleName() + " (" + cause.getClass().getSimpleName() + ")";
        }
    }

    private static PolicyVersionRef policyOf(String slug) {
        List<PolicyVersionRef.Paragraph> paragraphs = new ArrayList<>();
        List<String> texts = Fixtures.paragraphs(Fixtures.evaluationPolicyText(slug));
        for (int i = 0; i < texts.size(); i++) {
            paragraphs.add(new PolicyVersionRef.Paragraph(UUID.randomUUID(), i + 1, texts.get(i)));
        }
        return new PolicyVersionRef(UUID.randomUUID(), UUID.randomUUID(), 1, paragraphs);
    }

    /** VALID, or the code of the first error, measured as the pipeline measures it (nulls stripped first). */
    private String verdict(String answer, PolicyVersionRef policy) {
        JsonNode document;
        try {
            document = JSON.readTree(answer);
        } catch (RuntimeException e) {
            return "NOT_JSON";
        }
        ValidationResult result = validator.validate(
                com.liorshaya.policypilot.ai.adapter.ProviderSchemaVariant.stripNulls(document),
                ValidationContext.AUTHORING, policy.texts(), Set.of());
        return result.hasErrors() ? result.findings().getFirst().code().name() : "VALID";
    }
}
