package com.liorshaya.policypilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.support.OfflineEmbeddings;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Work Plan day 1: the context loads with the {@code ollama} profile without any Ollama server running. It has a
 * database of its own, because the profile sizes {@code chunk.embedding} for bge-m3 and the startup check refuses a
 * column of another size (Document 2, Storage), which is what the shared database of the openai tests holds.
 */
@SpringBootTest
@Import(OfflineEmbeddings.class)
@ActiveProfiles("ollama")
class OllamaProfileContextIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresContainerSupport.PGVECTOR_IMAGE);

    static {
        POSTGRES.start();
    }

    @Autowired
    private PolicyPilotProperties properties;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PromptRegistry registry;

    @Test
    void contextLoadsWithTheOllamaProfile() {
        assertThat(properties).isNotNull();
    }

    @Test
    void ollamaEmbeddingDimensionIs1024() {
        assertThat(properties.embedding().dimension()).isEqualTo(1024);
    }

    // Document 2, Storage: vector(1024) for bge-m3, set by the migration for the active profile
    @Test
    void theMigrationSizesTheChunkColumnForBgeM3() {
        String type = jdbc.sql("""
                select format_type(atttypid, atttypmod) from pg_attribute
                where attrelid = 'chunk'::regclass and attname = 'embedding'""").query(String.class).single();

        assertThat(type).isEqualTo("vector(1024)");
    }

    @Test
    void bothModelRolesMapToTheLocalModel() {
        assertThat(properties.ai().models().strong()).isEqualTo("qwen3:14b");
        assertThat(properties.ai().models().fast()).isEqualTo("qwen3:14b");
    }

    // Document 4, Model Configuration per Prompt: "Author and review get 600 s, change 300 s, explain 180 s, and answer
    // 300 s with its first token within 90 s" under the ollama profile. Expected: those values, bound and applied
    @Test
    void theSlowerModelGetsTheDocumentedTimeouts() {
        assertThat(properties.ai().timeouts().promptSeconds()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "author", 600, "review", 600, "change", 300, "explain", 180, "answer", 300));
        assertThat(properties.ai().timeouts().chatFirstTokenSeconds()).isEqualTo(90);
        assertThat(registry.get("author").timeout()).isEqualTo(Duration.ofSeconds(600));
        assertThat(registry.get("answer").timeout()).isEqualTo(Duration.ofSeconds(300));
    }
}
