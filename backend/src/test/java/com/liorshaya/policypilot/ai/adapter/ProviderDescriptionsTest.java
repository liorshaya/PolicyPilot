package com.liorshaya.policypilot.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.ai.ProviderDescription;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.support.Requirement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * What {@code GET /system/provider} says the active profile runs on (Brief FR-21; Document 2, API Surface and the
 * profiles table of Configuration and Model Providers; Document 4, Role to model mapping). The profile files that ship
 * are read as Spring Boot orders them, so the expected values are Document 2's two columns and a model renamed in a
 * profile file shows up here, not only in the header.
 */
@Requirement({"FR-21", "NFR-4"})
class ProviderDescriptionsTest {

    // Document 2, the openai column: gpt-5.6-terra (strong) and gpt-5.6-luna (fast), text-embedding-3-small with
    // 1536 dimensions. Expected: exactly those names
    @Test
    void theOpenAiProfileIsDescribedAsDocument2sOpenAiColumn() {
        ConfigurableEnvironment openai = shipped("openai");

        ProviderDescription described = ProviderDescriptions.describe("openai", "openai", openai, bind(openai));

        assertThat(described).isEqualTo(
                new ProviderDescription("openai", "gpt-5.6-terra", "gpt-5.6-luna", "text-embedding-3-small", 1536));
    }

    // Document 2, the ollama column: qwen3:14b for both roles, bge-m3 with 1024 dimensions. Expected: those names
    @Test
    void theOllamaProfileIsDescribedAsDocument2sOllamaColumn() {
        ConfigurableEnvironment ollama = shipped("ollama");

        ProviderDescription described = ProviderDescriptions.describe("ollama", "ollama", ollama, bind(ollama));

        assertThat(described)
                .isEqualTo(new ProviderDescription("ollama", "qwen3:14b", "qwen3:14b", "bge-m3", 1024));
    }

    // Document 4, Role to model mapping: the strong role authors, reviews and changes, the fast role explains and
    // answers. Expected: under openai strong asks gpt-5.6-terra and fast gpt-5.6-luna; under ollama both qwen3:14b
    @Test
    void theStrongRoleAsksTheStrongModelAndTheFastRoleTheFastModel() {
        PolicyPilotProperties openai = bind(shipped("openai"));
        PolicyPilotProperties ollama = bind(shipped("ollama"));

        assertThat(SpringAiLlmGateway.modelFor(ModelRole.STRONG, openai)).isEqualTo("gpt-5.6-terra");
        assertThat(SpringAiLlmGateway.modelFor(ModelRole.FAST, openai)).isEqualTo("gpt-5.6-luna");
        assertThat(SpringAiLlmGateway.modelFor(ModelRole.STRONG, ollama)).isEqualTo("qwen3:14b");
        assertThat(SpringAiLlmGateway.modelFor(ModelRole.FAST, ollama)).isEqualTo("qwen3:14b");
    }

    // A profile that names no embedding model would leave the header to guess one. Expected: the application refuses to
    // start, and the message names the property the profile is missing
    @Test
    void aProfileThatNamesNoEmbeddingModelRefusesToStart() {
        PolicyPilotProperties openai = bind(shipped("openai"));

        assertThatThrownBy(() -> ProviderDescriptions.describe("openai", "openai", new MockEnvironment(), openai))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.ai.openai.embedding.model");
    }

    /**
     * {@code application.yml} under {@code application-<profile>.yml}, the profile file first as Spring Boot orders
     * them, and nothing from the machine: no environment variable or system property can change what the test reads.
     */
    private static ConfigurableEnvironment shipped(String profile) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        YamlPropertySourceLoader yaml = new YamlPropertySourceLoader();
        for (String file : List.of("application-" + profile + ".yml", "application.yml")) {
            try {
                yaml.load(file, new ClassPathResource(file)).forEach(environment.getPropertySources()::addLast);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return environment;
    }

    private static PolicyPilotProperties bind(ConfigurableEnvironment environment) {
        return Binder.get(environment).bind("policypilot", PolicyPilotProperties.class).get();
    }
}
