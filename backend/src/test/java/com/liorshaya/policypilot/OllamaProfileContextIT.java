package com.liorshaya.policypilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Work Plan day 1: the context loads with the {@code ollama} profile without any Ollama server running. */
@SpringBootTest
@ActiveProfiles("ollama")
class OllamaProfileContextIT extends PostgresContainerSupport {

    @Autowired
    private PolicyPilotProperties properties;

    @Test
    void contextLoadsWithTheOllamaProfile() {
        assertThat(properties).isNotNull();
    }

    @Test
    void ollamaEmbeddingDimensionIs1024() {
        assertThat(properties.embedding().dimension()).isEqualTo(1024);
    }

    @Test
    void bothModelRolesMapToTheLocalModel() {
        assertThat(properties.ai().models().strong()).isEqualTo("qwen3:14b");
        assertThat(properties.ai().models().fast()).isEqualTo("qwen3:14b");
    }
}
