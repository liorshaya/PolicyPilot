package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import com.liorshaya.policypilot.web.error.TraceIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * The order of the front door's filters (Document 5, Authentication, Session and Authorization; CSRF): CORS answers
 * first, then the custom header and Origin check, then the session cookie, then the rate limits, which need the
 * session's sandbox, and authorization last. Only the security chain is built: no database and no server start.
 */
class SecurityFilterChainTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ChainOnly.class);

    @Test
    void theFiltersRunInTheOrderTheControlsNeed() {
        runner.run(context -> {
            List<Class<? extends Filter>> order = context.getBean(SecurityFilterChain.class).getFilters().stream()
                    .<Class<? extends Filter>>map(Filter::getClass)
                    .filter(List.of(CorsFilter.class, CsrfDefenseFilter.class, AccessCodeFilter.class,
                            RateLimitFilter.class, AuthorizationFilter.class)::contains)
                    .toList();

            assertThat(order).containsExactly(CorsFilter.class, CsrfDefenseFilter.class, AccessCodeFilter.class,
                    RateLimitFilter.class, AuthorizationFilter.class);
        });
    }

    @Test
    void theChainKeepsNoHttpSessionAndNoFormLogin() {
        runner.run(context -> assertThat(context.getBean(SecurityFilterChain.class).getFilters())
                .extracting(filter -> filter.getClass().getSimpleName())
                .doesNotContain("UsernamePasswordAuthenticationFilter", "BasicAuthenticationFilter",
                        "CsrfFilter", "DefaultLoginPageGeneratingFilter"));
    }

    @Test
    void theSecretsOfTheConfigurationBuildTheCookieAndCodeBeans() {
        runner.run(context -> {
            assertThat(context.getBean(AccessCodeVerifier.class).matches("testcode")).isTrue();
            assertThat(context.getBean(SessionCookies.class)).isNotNull();
            assertThat(context.getBean(LoginThrottle.class)).isNotNull();
            assertThat(context.getBean(RateLimits.class)).isNotNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableConfigurationProperties(PolicyPilotProperties.class)
    @Import(WebSecurityConfig.class)
    static class ChainOnly {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        ErrorResponses errorResponses() {
            return new ErrorResponses(new TraceIds(new SimpleTracer()), JsonMapper.builder().build());
        }

        @Bean
        SecurityEvents securityEvents() {
            return new SecurityEvents(new SimpleMeterRegistry(), "salt".getBytes(StandardCharsets.UTF_8));
        }
    }
}
