package com.liorshaya.policypilot.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * The demo secrets are validated when the application starts (Document 5, Data Protection, Secrets; OWASP A02,
 * configuration validated at startup). A minimal context binds the real {@code application.yml} and overrides one
 * secret per test; nothing else of the application starts.
 */
class SecretsValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesOnly.class);

    @Test
    void theTestSecretsStartTheContext() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startupFailsWithoutAnAccessCode() {
        runner.withPropertyValues("policypilot.access-code=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("POLICYPILOT_ACCESS_CODE");
        });
    }

    @Test
    void startupFailsWithoutACookieSecret() {
        runner.withPropertyValues("policypilot.cookie-secret=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("POLICYPILOT_COOKIE_SECRET");
        });
    }

    @Test
    void cookieSecretShorterThan32BytesIsRefused() {
        runner.withPropertyValues("policypilot.cookie-secret=" + "x".repeat(31)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("at least 32 bytes");
        });
    }

    @Test
    void cookieSecretOfExactly32BytesIsAccepted() {
        runner.withPropertyValues("policypilot.cookie-secret=" + "x".repeat(32))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void accessCodeMustBeEightLowercaseLetters() {
        for (String code : new String[] {"abcdefg", "abcdefghi", "Abcdefgh", "abcd1234"}) {
            runner.withPropertyValues("policypilot.access-code=" + code).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("8 lowercase letters");
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PolicyPilotProperties.class)
    static class PropertiesOnly {}
}
