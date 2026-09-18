package com.liorshaya.policypilot.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    void platformDatabaseUrlIsTranslatedIntoDatasourceProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://policypilot:secret@db:5432/policypilot");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db:5432/policypilot");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("policypilot");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("secret");
        assertThat(environment.getPropertySources().contains(DatabaseUrlEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isTrue();
    }

    @Test
    void jdbcDatabaseUrlAddsNothing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "jdbc:postgresql://localhost:5432/policypilot");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains(DatabaseUrlEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
        assertThat(environment.getProperty("spring.datasource.url")).isNull();
    }

    @Test
    void missingDatabaseUrlAddsNothing() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains(DatabaseUrlEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }
}
