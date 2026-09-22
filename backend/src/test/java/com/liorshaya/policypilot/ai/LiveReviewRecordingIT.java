package com.liorshaya.policypilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.ai.service.ReviewService;
import com.liorshaya.policypilot.common.Hashes;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.ruleset.service.ReviewFinding;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.SeededDrafts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The first live run of the review prompt (Work Plan day 10: "the reviewer tried on the seeded findings of labeled
 * policies"; Document 6: every new AI path gets one live run before it is trusted). Tagged {@code live}, so CI never
 * runs it; it needs a real {@code OPENAI_API_KEY}:
 *
 * <pre>{@code
 * OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test=LiveReviewRecordingIT -Dlive.tag= \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
 * }</pre>
 *
 * <p>It reviews the demo's draft (the canonical recording of author/v1 for the lending policy) and the draft of every
 * labeled policy (SeededDrafts), writes each answer to {@code fixtures/eval/recordings/openai/review/v1/} under the
 * hash of the rendered prompt, and prints the seeded defects each review found. Recall is measured, not asserted:
 * that is the evaluation runner's report (day 11). The one assertion is the day's Done when: the demo draft's review
 * shows the ambiguity and the conflict of step 1.
 */
@Tag("live")
@TestPropertySource(properties = {"spring.ai.openai.api-key=${OPENAI_API_KEY}",
        "policypilot.ai.daily-token-budget=400000"})
@Isolated
class LiveReviewRecordingIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path RECORDINGS = Path.of("..", "fixtures", "eval", "recordings", "openai", "review");
    /** The demo policy's title, the rule set's name, as the seeded demo shows it. */
    private static final String LENDING_TITLE = "מדיניות אשראי צרכני - הלוואות אישיות";
    /** The canonical author/v1 recording of the lending policy: the draft step 1 is tested with. */
    private static final String DEMO_DRAFT =
            "eval/recordings/openai/author/v1/45e240b93e8f90a34f4c59cdbbd6217584198acf143358e43e66df2ce7cf2d3a.json";

    @Autowired
    private LlmGateway gateway;

    @Autowired
    private ReviewService reviews;

    @Autowired
    private PolicyPilotProperties properties;

    /** The same service over the recordings just written, so the findings are the ones the API would keep. */
    private final ReviewService replay = new ReviewService(RecordedGateway.replaying(RECORDINGS.getParent()),
            new PromptRegistry(PromptRegistry.PROMPTS, Map.of()));

    @Test
    void theReviewerIsRecordedOnTheDemoDraftAndOnEveryLabeledPolicy() {
        List<String> report = new ArrayList<>();

        JsonNode demoDraft = JSON.readTree(Fixtures.json(DEMO_DRAFT).required("response").asString());
        PolicyVersionRef lending = SeededDrafts.policy("consumer-lending");
        List<ReviewFinding> demo = record(lending, LENDING_TITLE, "he", demoDraft);
        report.add("demo draft: " + summary(demo));

        for (String slug : Fixtures.evaluationPolicies()) {
            List<ReviewFinding> findings = record(SeededDrafts.policy(slug), slug, SeededDrafts.language(slug),
                    SeededDrafts.draft(slug));
            List<String> found = new ArrayList<>();
            List<String> missed = new ArrayList<>();
            for (JsonNode seeded : SeededDrafts.seeded(slug)) {
                String label = seeded.required("id").asString() + " " + seeded.required("kind").asString();
                (SeededDrafts.found(seeded, findings) ? found : missed).add(label);
            }
            report.add(slug + ": found " + found + ", missed " + missed + "; " + findings.size() + " findings");
        }
        report.forEach(System.out::println);

        // Work Plan day 10, Done when: "step 1 shows the ambiguity and the conflict" (SF-1 and SF-2 of
        // fixtures/eval/policies/consumer-lending/seeded.findings.json)
        List<JsonNode> seeded = SeededDrafts.seeded("consumer-lending");
        assertThat(SeededDrafts.found(seeded.get(0), demo)).as("SF-1, the stable-income ambiguity").isTrue();
        assertThat(SeededDrafts.found(seeded.get(1), demo)).as("SF-2, the age conflict").isTrue();
    }

    /**
     * One live call, written as a recording, and its findings as the service keeps them after the anchor checks. An
     * input that already has a recording is not asked again, so a run that stopped halfway resumes without spending
     * the same tokens twice.
     */
    private List<ReviewFinding> record(PolicyVersionRef policy, String title, String language, JsonNode draft) {
        PromptSpec spec = reviews.specFor(policy, title, language, draft);
        if (!Files.exists(recordingOf(spec))) {
            Completion<String> answer = gateway.complete(spec, String.class);
            write(spec, answer.value());
        }
        return replay.review(policy, title, language, draft).review().findings();
    }

    private static Path recordingOf(PromptSpec spec) {
        return RECORDINGS.resolve(spec.promptVersion())
                .resolve(Hashes.sha256Hex(spec.system() + "\u001f" + spec.user()) + ".json");
    }

    private static String summary(List<ReviewFinding> findings) {
        return findings.stream().map(finding -> finding.id() + " " + finding.kind().json() + " "
                + finding.paragraphIndexes() + finding.ruleIds()).toList().toString();
    }

    private void write(PromptSpec spec, String response) {
        String name = Hashes.sha256Hex(spec.system() + "\u001f" + spec.user());
        ObjectNode recording = JSON.createObjectNode();
        ObjectNode request = recording.putObject("request");
        request.put("prompt", spec.promptName());
        request.put("version", spec.promptVersion());
        request.put("model", properties.ai().models().strong());
        request.put("inputHash", name);
        request.put("system", spec.system());
        request.put("user", spec.user());
        recording.put("response", response);
        try {
            Files.createDirectories(recordingOf(spec).getParent());
            Files.writeString(recordingOf(spec), recording.toPrettyString());
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the recording", e);
        }
    }
}
