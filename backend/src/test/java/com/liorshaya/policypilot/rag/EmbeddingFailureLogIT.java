package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.rag.service.EmbeddingJob;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.EcsLogCapture;
import com.liorshaya.policypilot.support.FakeEmbeddingGateway;
import com.liorshaya.policypilot.support.Requirement;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The log line of a failed embedding (Document 5, Security Logging: every event is one JSON line). Day 14 found
 * {@code rag.embedding.failed} dropped whole by the encoder: a key named {@code error} collided with the ECS
 * {@code error} object the exception is written into, 18 events lost in one integration run. Isolated, because every
 * test context that starts reinitializes logback and detaches the capture's appender.
 */
@Isolated
@Requirement("FR-12")
class EmbeddingFailureLogIT extends ApiIntegrationTest {

    @Autowired
    private PolicyService policies;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private JdbcClient jdbc;

    // Expected: one ECS line at WARN (an event the encoder refused is taken too, so the bug shows), the version under
    // its own key, and the fake provider's exception under ECS error.type and error.message
    @Test
    void aFailureIsLoggedAsOneEcsLineWithItsCause() {
        try (EcsLogCapture log = EcsLogCapture.of(EmbeddingJob.class)) {
            String paragraph = "פסקה שנכשלת ונרשמת " + FakeEmbeddingGateway.FAIL + " " + UUID.randomUUID();
            String version = new RagFixtures(policies, rulesets, jdbc).publish(paragraph).versionId().toString();

            EcsLogCapture.Line line = log.await("rag.embedding.failed",
                    event -> event.refused() != null || event.json().path("version").asString().equals(version),
                    Duration.ofSeconds(15)).orElseThrow();

            assertThat(line.refused()).isNull();
            assertThat(line.json().required("log").required("level").asString()).isEqualTo("WARN");
            assertThat(line.json().required("version").asString()).isEqualTo(version);
            assertThat(line.json().required("error").required("type").asString())
                    .isEqualTo(LlmUnavailableException.class.getName());
            assertThat(line.json().required("error").required("message").asString()).isEqualTo("fake provider error");
        }
    }
}
