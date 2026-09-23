package com.liorshaya.policypilot.change;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.change.ChangeAnalysis;
import com.liorshaya.policypilot.ai.service.Candidates;
import com.liorshaya.policypilot.ai.service.ChangeBase;
import com.liorshaya.policypilot.change.service.ChangeProgress;
import com.liorshaya.policypilot.change.service.ChangeRequestService;
import com.liorshaya.policypilot.change.service.ChangeRequestView;
import com.liorshaya.policypilot.change.service.Submitted;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.liorshaya.policypilot.support.RecordedEmbeddingGateway;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.Requirement;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

/**
 * The scripted change request on what the provider recorded (Document 6, AI Layer Testing: every test after the first
 * live run is offline; Work Plan day 12). The seeded lending version is embedded at startup by
 * {@link RecordedEmbeddingGateway}, so candidate selection ranks the rule chunks on the provider's vectors, and the
 * model's answer is the one LiveChangeRecordingIT recorded, found by the prompt the service renders from the version
 * as the database returns it. The expectations are change-request-1.json's.
 */
@Requirement("FR-17")
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-real")
@ActiveProfiles("openai")
@Import(RecordedChangeIT.Recorded.class)
class RecordedChangeIT {

    /** A database of its own: another context's embedding job must never meet the recorded vectors. */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresContainerSupport.PGVECTOR_IMAGE);

    static {
        POSTGRES.start();
    }

    private static final JsonNode EXPECTED =
            Fixtures.json("policies/consumer-lending/change-request-1.json").required("expected");

    /** The progress of a request nobody streams. */
    private static final ChangeProgress UNWATCHED = new ChangeProgress() {
        @Override
        public void analyzing() {}

        @Override
        public void proposing(Candidates candidates) {}

        @Override
        public void validating() {}

        @Override
        public void regression() {}
    };

    @Autowired
    private ChangeAnalysis analysis;

    @Autowired
    private ChangeRequestService changes;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordedGateway model;

    private final UUID sandbox = UUID.randomUUID();
    private ChangeBase lending;

    @BeforeEach
    void theSeededVersionIsEmbedded() {
        model.reset();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!"READY".equals(jdbc.sql("""
                select v.embedding_status from ruleset_version v join ruleset r on r.id = v.ruleset_id
                where r.protected and v.version_no = 1""").query(String.class).single())) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the seeded version never became READY");
            }
            Thread.onSpinWait();
        }
        lending = changes.base(rulesets.protectedRulesets().getFirst().id(), 1, sandbox).orElseThrow();
    }

    // Work Plan day 12: "the scripted request yields the expected five candidates", on the provider's vectors: the two
    // nearest rule chunks and the closure over the fields they test. Expected: the fixture's five and its one field
    @Test
    void theScriptedRequestYieldsTheExpectedFive() {
        Candidates candidates = analysis.candidates(lending, ChangeRequests.scripted());

        assertThat(candidates.ruleIds()).containsExactlyInAnyOrderElementsOf(strings(EXPECTED.required("candidates")));
        assertThat(candidates.fields()).containsExactlyElementsOf(strings(EXPECTED.required("candidateFields")));
    }

    // Work Plan day 12, Done when, offline: the recorded answer proposes R-170 and R-410 with pending provenance.
    // Expected: the fixture's two replacements with their conditions and untouched rules, each patch pending with the
    // stored request's id, from one answer and no repair
    @Test
    void theRecordedAnswerIsStoredAsTheFixturesTwoReplacements() {
        Submitted submitted = changes.submit(lending, ChangeRequests.scripted(), sandbox, UNWATCHED);

        assertThat(submitted).isInstanceOf(Submitted.Stored.class);
        ChangeRequestView request = ((Submitted.Stored) submitted).request();
        JsonNode patches = request.proposal().required("patches");
        JsonNode expected = EXPECTED.required("patches");
        assertThat(patches).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            JsonNode patch = patches.get(i);
            assertThat(patch.required("op")).isEqualTo(expected.get(i).required("op"));
            assertThat(patch.required("ruleId")).isEqualTo(expected.get(i).required("ruleId"));
            assertThat(patch.required("rule").required("condition"))
                    .isEqualTo(expected.get(i).required("rule").required("condition"));
            assertThat(patch.required("rule").required("provenance").required("kind").asString()).isEqualTo("pending");
            assertThat(patch.required("rule").required("provenance").required("changeRequestId").asString())
                    .isEqualTo(request.id().toString());
        }
        assertThat(strings(request.proposal().required("untouched")))
                .containsExactlyElementsOf(strings(EXPECTED.required("untouched")));
        assertThat(model.asked()).hasSize(1);
    }

    private static List<String> strings(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorded {

        @Bean
        @Primary
        RecordedGateway recordedAnswers() {
            return RecordedGateway.replaying(Path.of("..", "fixtures", "eval", "recordings", "openai"));
        }

        @Bean
        @Primary
        RecordedEmbeddingGateway recordedEmbeddingGateway(PolicyPilotProperties properties) {
            return RecordedEmbeddingGateway.replaying(properties.embedding().dimension());
        }
    }
}
