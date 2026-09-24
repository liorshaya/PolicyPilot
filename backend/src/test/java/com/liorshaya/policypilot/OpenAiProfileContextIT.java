package com.liorshaya.policypilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.support.OfflineEmbeddings;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

/** Work Plan day 1: the context loads with the {@code openai} profile, and the documented defaults bind. */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-real")
@Import(OfflineEmbeddings.class)
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

    // The nightly reset's cron is read from the application.yml that ships, not from the bound properties: the test
    // configuration turns the job off in every test context ("-"), because a cached context keeps its scheduler, and
    // at 03:00 UTC one on the real clock would reset the shared database under a running test
    @Test
    void documentedDefaultsBind() {
        assertThat(properties.rateLimit().perMinute()).isEqualTo(20);
        assertThat(properties.rateLimit().perSandboxPerHour()).isEqualTo(60);
        assertThat(properties.rateLimit().concurrentStreams()).isEqualTo(3);
        assertThat(properties.ai().timeouts().chatFirstTokenSeconds()).isEqualTo(20);
        assertThat(properties.ai().maxRepairAttempts()).isEqualTo(2);
        assertThat(properties.ai().promptVersions())
                .containsEntry("author", "v1")
                .containsEntry("review", "v1")
                .containsEntry("explain", "v1")
                .containsEntry("answer", "v1")
                .containsEntry("change", "v2");
        assertThat(properties.rag().topK()).isEqualTo(8);
        assertThat(properties.rag().minScore()).isEqualTo(0.35);
        assertThat(shipped("policypilot.demo.reset-cron")).isEqualTo("0 0 3 * * *");
        assertThat(properties.demo().fixtureSet()).isEqualTo("cases-200");
    }

    private static String shipped(String key) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject().getProperty(key);
    }
}
