package com.liorshaya.policypilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.cache.ProposalCache;
import com.liorshaya.policypilot.ai.repository.CachedResponseRepository;
import com.liorshaya.policypilot.ai.service.AuthorService;
import com.liorshaya.policypilot.common.Hashes;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.validation.RuleSetValidator;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.rules.validation.ValidationResult;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The ten live authoring runs of the lending policy (Work Plan day 7; Gate G1: "the author prompt produces a
 * schema-valid draft for the lending policy in at least 9 of 10 recorded runs"). This is the only test in the
 * repository that calls a provider, it is tagged {@code live} so CI never runs it, and it needs a real
 * {@code OPENAI_API_KEY}:
 *
 * <pre>{@code
 * OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test=LiveAuthoringRecordingIT -Dlive.tag= \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
 * }</pre>
 *
 * <p>Every answer is written to {@code fixtures/eval/recordings/openai/author/v1/}, keyed by the hash of the
 * rendered prompt, so every other test can replay it offline (Document 6, AI Layer Testing). The response cache
 * is cleared between runs on purpose: ten cached copies of one answer would measure nothing.
 */
@Tag("live")
@TestPropertySource(properties = {"spring.ai.openai.api-key=${OPENAI_API_KEY}",
        "policypilot.ai.daily-token-budget=400000"})
@Isolated
class LiveAuthoringRecordingIT extends ApiIntegrationTest {

    /** Ten runs is what Gate G1 measures; {@code -Dlive.runs=1} is for checking the plumbing first. */
    private static final int RUNS = Integer.getInteger("live.runs", 10);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path RECORDINGS = Path.of("..", "fixtures", "eval", "recordings", "openai", "author");

    @Autowired
    private LlmGateway gateway;

    @Autowired
    private AuthorService author;

    @Autowired
    private CachedResponseRepository cache;

    @Autowired
    private PolicyPilotProperties properties;

    private final RuleSetValidator validator = new RuleSetValidator();

    private static PolicyVersionRef lendingPolicy() {
        List<PolicyVersionRef.Paragraph> paragraphs = new ArrayList<>();
        List<String> texts = Fixtures.lendingParagraphs();
        for (int i = 0; i < texts.size(); i++) {
            paragraphs.add(new PolicyVersionRef.Paragraph(UUID.randomUUID(), i + 1, texts.get(i)));
        }
        return new PolicyVersionRef(UUID.randomUUID(), UUID.randomUUID(), 1, paragraphs);
    }

    @Test
    void tenLiveRunsOfTheLendingPolicyAreRecordedAndNineOfThemAreValid() {
        PolicyVersionRef policy = lendingPolicy();
        PromptSpec spec = author.specFor(policy, "מדיניות אשראי צרכני - הלוואות אישיות", "he", null);
        String model = properties.ai().models().strong();
        String key = ProposalCache.keyOf(spec, model);
        List<String> outcomes = new ArrayList<>();
        List<Long> seconds = new ArrayList<>();
        boolean recordedAValidRun = false;

        for (int run = 1; run <= RUNS; run++) {
            cache.deleteById(key);
            long started = System.nanoTime();
            Completion<String> answer = gateway.complete(spec, String.class);
            seconds.add(java.time.Duration.ofNanos(System.nanoTime() - started).toSeconds());
            String verdict = verdict(answer.value(), policy);
            boolean firstValidRun = "VALID".equals(verdict) && !recordedAValidRun;
            recordedAValidRun |= firstValidRun;
            write(spec, model, run, answer.value(), run == 1 || firstValidRun);
            outcomes.add(verdict);
        }

        long valid = outcomes.stream().filter("VALID"::equals).count();
        System.out.println("live authoring runs: " + outcomes);
        System.out.println("schema-valid first try: " + valid + " of " + RUNS);
        System.out.println("seconds per run: " + seconds);
        // Gate G1 of Document 7, measured over the ten runs it asks for
        assertThat(valid * 10).isGreaterThanOrEqualTo(9L * RUNS);
        assertThat(Files.exists(RECORDINGS.resolve(spec.promptVersion())
                .resolve(Hashes.sha256Hex(spec.system() + "\u001f" + spec.user()) + ".json"))).isTrue();
    }

    /** VALID, or the code of the first error the validator reported, so the run list reads at a glance. */
    private String verdict(String answer, PolicyVersionRef policy) {
        JsonNode document;
        try {
            document = JSON.readTree(answer);
        } catch (RuntimeException e) {
            return "NOT_JSON";
        }
        // the pipeline strips the nulls a strict mode forced the model to write before it validates, so the
        // measure of "schema-valid first try" is taken the same way
        ValidationResult result = validator.validate(
                com.liorshaya.policypilot.ai.adapter.ProviderSchemaVariant.stripNulls(document),
                ValidationContext.AUTHORING, policy.texts(), Set.of());
        return result.hasErrors() ? result.findings().getFirst().code().name() : "VALID";
    }

    /**
     * One recording per run. The numbered copies are the evidence for the gate, every answer as it came; the copy
     * under the prompt's hash is the one {@code RecordedGateway} replays, and it is the first run that validated,
     * because the gate allows one failure in ten and CI must not replay that one.
     */
    private void write(PromptSpec spec, String model, int run, String response, boolean canonical) {
        ObjectNode recording = JSON.createObjectNode();
        ObjectNode request = recording.putObject("request");
        request.put("prompt", spec.promptName());
        request.put("version", spec.promptVersion());
        request.put("model", model);
        request.put("inputHash", Hashes.sha256Hex(spec.system() + "\u001f" + spec.user()));
        // the rendered prompts travel with the answer: a replay that misses can be diffed instead of guessed at
        request.put("system", spec.system());
        request.put("user", spec.user());
        recording.put("response", response);
        recording.put("run", run);
        String name = Hashes.sha256Hex(spec.system() + "\u001f" + spec.user());
        try {
            Path directory = RECORDINGS.resolve(spec.promptVersion());
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name + ".run" + run + ".json"), recording.toPrettyString());
            if (canonical) {
                Files.writeString(directory.resolve(name + ".json"), recording.toPrettyString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the recording", e);
        }
    }
}
