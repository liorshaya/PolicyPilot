package com.liorshaya.policypilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Work Plan day 1: the context loads with the {@code openai} profile, and the documented defaults bind. */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-real")
@ActiveProfiles("openai")
class OpenAiProfileContextIT extends PostgresContainerSupport {

    @Autowired
    private PolicyPilotProperties properties;

    @Test
    void contextLoadsWithTheOpenAiProfile() {
        assertThat(properties).isNotNull();
    }

    @Test
    void openAiEmbeddingDimensionIs1536() {
        assertThat(properties.embedding().dimension()).isEqualTo(1536);
    }

    @Test
    void documentedDefaultsBind() {
        assertThat(properties.rateLimit().perMinute()).isEqualTo(20);
        assertThat(properties.rateLimit().perSandboxPerHour()).isEqualTo(60);
        assertThat(properties.rateLimit().concurrentStreams()).isEqualTo(3);
        assertThat(properties.ai().timeouts().authorSeconds()).isEqualTo(60);
        assertThat(properties.ai().timeouts().chatFirstTokenSeconds()).isEqualTo(20);
        assertThat(properties.ai().maxRepairAttempts()).isEqualTo(2);
        assertThat(properties.ai().promptVersions())
                .containsEntry("author", "v1")
                .containsEntry("review", "v1")
                .containsEntry("explain", "v1")
                .containsEntry("answer", "v1")
                .containsEntry("change", "v1");
        assertThat(properties.rag().topK()).isEqualTo(8);
        assertThat(properties.rag().minScore()).isEqualTo(0.35);
        assertThat(properties.demo().resetCron()).isEqualTo("0 0 3 * * *");
        assertThat(properties.demo().fixtureSet()).isEqualTo("cases-200");
    }
}
