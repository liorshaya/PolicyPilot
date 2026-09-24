package com.liorshaya.policypilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.ai.service.ExplainService;
import com.liorshaya.policypilot.ai.service.ExplainService.Audience;
import com.liorshaya.policypilot.ai.service.ExplainService.Explained;
import com.liorshaya.policypilot.common.Hashes;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.ruleset.service.PublishedVersion;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.Seeded;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The first live run of the explain prompt (Document 6: every new AI path gets one live run before it is trusted).
 * Tagged {@code live}, so CI never runs it; it needs a real {@code OPENAI_API_KEY}:
 *
 * <pre>{@code
 * OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test=LiveExplainRecordingIT -Dlive.tag= \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
 * }</pre>
 *
 * <p>It explains case 17 to an officer and to an applicant, and case 2 (approved with the stable-income flag) to an
 * officer, each decided by the engine on the seeded version the cloud site decides on, and writes the answers to
 * {@code fixtures/eval/recordings/openai/explain/v1/}. The assertion is the day's Done when: "Explain on case 17 cites
 * R-330 and its paragraph".
 */
@Tag("live")
@TestPropertySource(properties = {"spring.ai.openai.api-key=${OPENAI_API_KEY}",
        "policypilot.ai.daily-token-budget=400000"})
@Isolated
class LiveExplainRecordingIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path RECORDINGS = Path.of("..", "fixtures", "eval", "recordings", "openai", "explain");

    @Autowired
    private LlmGateway gateway;

    @Autowired
    private ExplainService explainer;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private DecisionService decisions;

    @Autowired
    private PolicyPilotProperties properties;

    private final ExplainService replay = new ExplainService(RecordedGateway.replaying(RECORDINGS.getParent()),
            new PromptRegistry(PromptRegistry.PROMPTS, Map.of()));

    @Test
    void caseSeventeenAndAnApprovedCaseAreExplainedAndRecorded() {
        UUID sandbox = UUID.randomUUID();
        PublishedVersion version = rulesets.published(Seeded.lendingRuleset(rulesets).id(), 1, sandbox)
                .orElseThrow();
        ObjectNode seventeen = decisions.decide(version, sandbox, input(17)).decision();
        ObjectNode two = decisions.decide(version, sandbox, input(2)).decision();

        Explained officer = record(seventeen, Audience.OFFICER);
        Explained applicant = record(seventeen, Audience.APPLICANT);
        Explained approved = record(two, Audience.OFFICER);
        for (Explained explained : new Explained[] {officer, applicant, approved}) {
            System.out.println(explained.explanation() + " dropped " + explained.dropped());
        }

        // Work Plan day 10, Done when: "Explain on case 17 cites R-330 and its paragraph" (paragraph 7, the provenance
        // of R-330 in fixtures/policies/consumer-lending/sample-decision.json)
        assertThat(officer.explanation().factors()).anySatisfy(factor -> {
            assertThat(factor.ruleId()).isEqualTo("R-330");
            assertThat(factor.paragraph()).isEqualTo(7);
        });
        assertThat(applicant.explanation().factors()).extracting(ExplainService.Factor::ruleId).contains("R-330");
    }

    private static ObjectNode input(int caseNo) {
        return (ObjectNode) Fixtures.json("policies/consumer-lending/cases-200.json").required("cases").valueStream()
                .filter(fixture -> fixture.path("id").asInt() == caseNo).findFirst().orElseThrow()
                .required("input");
    }

    /** One live call written as a recording, then read back through the whole pipeline. */
    private Explained record(ObjectNode decision, Audience audience) {
        PromptSpec spec = explainer.specFor(decision, audience, "he");
        Path file = RECORDINGS.resolve(spec.promptVersion())
                .resolve(Hashes.sha256Hex(spec.system() + "\u001f" + spec.user()) + ".json");
        if (!Files.exists(file)) {
            long started = System.nanoTime();
            Completion<String> answer = gateway.complete(spec, String.class);
            System.out.println(audience + ": " + (System.nanoTime() - started) / 1_000_000 + " ms, " + answer.usage());
            ObjectNode recording = JSON.createObjectNode();
            ObjectNode request = recording.putObject("request");
            request.put("prompt", spec.promptName());
            request.put("version", spec.promptVersion());
            request.put("model", properties.ai().models().fast());
            request.put("inputHash", file.getFileName().toString().replace(".json", ""));
            request.put("system", spec.system());
            request.put("user", spec.user());
            recording.put("response", answer.value());
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, recording.toPrettyString());
            } catch (IOException e) {
                throw new UncheckedIOException("could not write the recording", e);
            }
        }
        return replay.explain(decision, audience, "he");
    }
}
