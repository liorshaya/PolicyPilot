package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import com.liorshaya.policypilot.web.error.TraceIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * The CORS part of the front door and the public routes (Document 5, CSRF: CORS allows only the web app's origin
 * with credentials; Principle 1: every route requires the cookie except the code exchange and the health check).
 */
class WebSecurityConfigTest {

    private static final String ORIGIN = "https://policypilot.liorshaya.com";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void corsAllowsTheConfiguredOriginsWithCredentials() {
        CorsConfiguration cors = corsFor("/api/v1/policies");

        assertThat(cors.getAllowedOrigins()).containsExactly(ORIGIN);
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void corsAllowsTheClientHeaderAndExposesTheTraceIdAndRetryAfter() {
        CorsConfiguration cors = corsFor("/api/v1/policies");

        assertThat(cors.getAllowedHeaders()).contains("X-PolicyPilot-Client", "Content-Type");
        assertThat(cors.getExposedHeaders()).contains("X-Trace-Id", "Retry-After");
    }

    @Test
    void aForeignOriginIsRefusedWithTheEnvelopeAndCounted() throws Exception {
        CorsFilter filter = new CorsFilter(WebSecurityConfig.corsConfigurationSource(List.of(ORIGIN)));
        filter.setCorsProcessor(new EnvelopeCorsProcessor(
                new ErrorResponses(new TraceIds(new SimpleTracer()), JsonMapper.builder().build()),
                new SecurityEvents(registry, "salt".getBytes(StandardCharsets.UTF_8))));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/code");
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("CSRF_REJECTED");
        assertThat(registry.counter("security.session.invalid", "reason", "origin-mismatch").count()).isEqualTo(1.0);
    }

    @Test
    void onlyTheHealthCheckTheCodeExchangeAndTheErrorPageArePublic() {
        var routes = WebSecurityConfig.publicRoutes();

        assertThat(routes.matches(new MockHttpServletRequest("GET", "/actuator/health"))).isTrue();
        assertThat(routes.matches(new MockHttpServletRequest("GET", "/actuator/health/liveness"))).isTrue();
        assertThat(routes.matches(new MockHttpServletRequest("GET", "/error"))).isTrue();
        assertThat(routes.matches(new MockHttpServletRequest("POST", "/api/v1/auth/code"))).isTrue();
        assertThat(routes.matches(new MockHttpServletRequest("GET", "/actuator/info"))).isFalse();
        assertThat(routes.matches(new MockHttpServletRequest("GET", "/api/docs"))).isFalse();
    }

    private static CorsConfiguration corsFor(String path) {
        return WebSecurityConfig.corsConfigurationSource(List.of(ORIGIN))
                .getCorsConfiguration(new MockHttpServletRequest("GET", path));
    }
}
