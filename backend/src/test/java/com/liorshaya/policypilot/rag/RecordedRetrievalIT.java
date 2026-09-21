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

/**
 * Retrieval on the vectors the live pass recorded (Document 6, AI Layer Testing: every test after the first live run
 * is offline). The lending policy is published in a sandbox of its own and embedded by {@link RecordedEmbeddingGateway},
 * so the ranking is the one the provider's vectors give. The expectations are the day's gate and the questions'
 * {@code expectedChunks}: Q-03 ("what is the maximum loan term?") retrieves paragraph 2, and Q-07, which names R-320,
 * retrieves its rule.
 */
@Requirement({"FR-12", "FR-13"})
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-real")
@ActiveProfiles("openai")
@Import(RecordedRetrievalIT.Recorded.class)
class RecordedRetrievalIT {

    /**
     * A database of its own: another context's embedding job must never meet this context's recorded gateway, which
     * fails on any text it has not recorded.
     */
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

    private RagFixtures fixtures;
    private UUID sandbox;
    private UUID rulesetId;

    @BeforeEach
    void publishTheLendingPolicy() {
        fixtures = new RagFixtures(policies, rulesets, jdbc);
        sandbox = UUID.randomUUID();
        PolicyView policy = policies.create(sandbox, "consumer-lending", PolicyLanguage.HE,
                Fixtures.lendingPolicyText());
        UUID policyVersion = policies.version(policy.id(), 1, sandbox).orElseThrow().id();
        VersionView draft = rulesets.createDraft(sandbox, policyVersion, Fixtures.lendingV1(),
                ValidationContext.ANALYST_EDIT, Set.of());
        rulesetId = draft.rulesetId();
        fixtures.awaitStatus(rulesets.publish(rulesetId, 1, sandbox).orElseThrow().versionId(), "READY");
    }

    // Work Plan day 8, Done when: "a Hebrew question retrieves its expected paragraph". Expected: Q-03's p:2, from
    // fixtures/eval/questions.json, among the chunks kept
    @Test
    void theHebrewTermQuestionRetrievesParagraphTwo() {
        Retrieval result = retrieve("מהי תקופת ההחזר המקסימלית להלוואה?");

        assertThat(result.covered()).isTrue();
        assertThat(result.chunks()).extracting(RetrievedChunk::id).contains("p:2");
    }

    // Q-07's expected r:R-320, which the question names. Expected: the rule among the chunks kept
    @Test
    void theQuestionNamingARuleRetrievesIt() {
        assertThat(retrieve("מה עושה הכלל R-320?").chunks()).extracting(RetrievedChunk::id).contains("r:R-320");
    }

    private Retrieval retrieve(String question) {
        return retrieval.retrieve(rulesetId, 1, sandbox, question).orElseThrow();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorded {

        @Bean
        @Primary
        RecordedEmbeddingGateway recordedEmbeddingGateway(PolicyPilotProperties properties) {
            return RecordedEmbeddingGateway.replaying(properties.embedding().dimension());
        }
    }
}
