package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.rag.service.Retrieval;
import com.liorshaya.policypilot.rag.service.RetrievalService;
import com.liorshaya.policypilot.rag.service.RetrievedChunk;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.liorshaya.policypilot.support.RecordedEmbeddingGateway;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.Reviews;
import java.util.Set;
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
 * The retrieval Threshold on the local profile (Document 4, Retrieval Pipeline: The threshold per embedding model).
 * The lending policy is published on the {@code ollama} profile and embedded with the bge-m3 vectors the Ollama
 * retrieval pass recorded ({@code fixtures/eval/recordings/ollama/embedding/bge-m3/}), so no Ollama runs. The
 * expectations are Document 4's and the labeled set's: the profile's 0.58 stops the demo's rate question, Q-04, a
 * refusal whose best chunk scores 0.572 on bge-m3, before any model call, and lets the term question, Q-03, through.
 */
@Requirement({"FR-15", "FR-21"})
@SpringBootTest
@ActiveProfiles("ollama")
@Import(RecordedOllamaRetrievalIT.Recorded.class)
class RecordedOllamaRetrievalIT {

    /** A database of its own: its vectors are bge-m3's, 1024 wide, in a column sized for them. */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresContainerSupport.PGVECTOR_IMAGE);

    static {
        POSTGRES.start();
    }

    @Autowired
    private PolicyService policies;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private RetrievalService retrieval;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PolicyPilotProperties properties;

    private UUID sandbox;
    private UUID rulesetId;

    @BeforeEach
    void publishTheLendingPolicy() {
        sandbox = UUID.randomUUID();
        PolicyView policy = policies.create(sandbox, "consumer-lending", PolicyLanguage.HE,
                Fixtures.lendingPolicyText());
        UUID policyVersion = policies.version(policy.id(), 1, sandbox).orElseThrow().id();
        VersionView draft = rulesets.createDraft(sandbox, policyVersion, Fixtures.lendingV1(),
                ValidationContext.ANALYST_EDIT, Set.of());
        rulesetId = Reviews.reviewed(rulesets, draft, sandbox).rulesetId();
        new RagFixtures(policies, rulesets, jdbc)
                .awaitStatus(rulesets.publish(rulesetId, 1, sandbox).orElseThrow().versionId(), "READY");
    }

    // Document 4: "0.58 is the lowest value that stops the demo's rate question, Q-04 at 0.572". Expected: the
    // profile's threshold is 0.58, and Q-04, a refusal of the labeled set, is not covered
    @Test
    void theDemosRateQuestionIsStoppedBeforeTheModel() {
        JsonNode rate = labeled("Q-04");

        assertThat(properties.rag().minScore()).isEqualTo(0.58);
        assertThat(rate.required("refusal").asBoolean()).isTrue();
        assertThat(retrieve(rate.required("question").asString()).covered()).isFalse();
    }

    // The labeled set's Q-03 is covered, and its best chunk scores 0.741 on bge-m3. Expected: covered, with its
    // expected p:2 among the chunks kept
    @Test
    void theTermQuestionIsCovered() {
        Retrieval term = retrieve(labeled("Q-03").required("question").asString());

        assertThat(term.covered()).isTrue();
        assertThat(term.chunks()).extracting(RetrievedChunk::id).contains("p:2");
    }

    private Retrieval retrieve(String question) {
        return retrieval.retrieve(rulesetId, 1, sandbox, question).orElseThrow();
    }

    private static JsonNode labeled(String id) {
        for (JsonNode question : Fixtures.json("eval/questions.json").required("questions")) {
            if (question.required("id").asString().equals(id)) {
                return question;
            }
        }
        throw new IllegalArgumentException("no labeled question " + id);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorded {

        @Bean
        @Primary
        RecordedEmbeddingGateway recordedEmbeddingGateway(PolicyPilotProperties properties) {
            return RecordedEmbeddingGateway.replaying(RecordedEmbeddingGateway.directoryOf("ollama", "bge-m3"),
                    properties.embedding().dimension());
        }
    }
}
