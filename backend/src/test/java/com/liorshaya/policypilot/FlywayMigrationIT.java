package com.liorshaya.policypilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.OfflineEmbeddings;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** Work Plan day 1, Docker Compose task: Flyway ran and the {@code vector} extension exists. */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-real")
@Import(OfflineEmbeddings.class)
@ActiveProfiles("openai")
class FlywayMigrationIT extends PostgresContainerSupport {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void flywayAppliedTheFirstMigrationSuccessfully() {
        List<String> versions = jdbc.sql("select version from flyway_schema_history where success order by installed_rank")
                .query(String.class)
                .list();

        assertThat(versions).startsWith("1");
    }

    @Test
    void pgvectorExtensionIsInstalled() {
        Integer installed = jdbc.sql("select count(*) from pg_extension where extname = 'vector'")
                .query(Integer.class)
                .single();

        assertThat(installed).isEqualTo(1);
    }
}
