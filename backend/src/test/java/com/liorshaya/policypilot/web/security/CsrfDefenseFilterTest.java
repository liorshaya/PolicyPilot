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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * The custom header and Origin check, the second and third CSRF defenses of Document 5, class by class. The same
 * rules through the running API, the CORS allowlist included, are {@code CsrfDefensesIT}.
 */
class CsrfDefenseFilterTest {

    private static final String ORIGIN = "https://policypilot.liorshaya.com";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CsrfDefenseFilter filter = new CsrfDefenseFilter(List.of(ORIGIN),
            new ErrorResponses(new TraceIds(new SimpleTracer()), JsonMapper.builder().build()),
            new SecurityEvents(registry, "salt".getBytes(StandardCharsets.UTF_8)));

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void safeMethodsPassWithoutHeaderOrOrigin(String method) throws Exception {
        assertThat(passes(new MockHttpServletRequest(method, "/api/docs"), new MockHttpServletResponse())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void stateChangingMethodsPassWithHeaderAndAllowlistedOrigin(String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/policies");
        request.addHeader("X-PolicyPilot-Client", "web");
        request.addHeader("Origin", ORIGIN);

        assertThat(passes(request, new MockHttpServletResponse())).isTrue();
    }

    @Test
    void missingHeaderIs403AndCountedAsMissingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/policies");
        request.addHeader("Origin", ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(passes(request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("CSRF_REJECTED");
        assertThat(registry.counter("security.session.invalid", "reason", "missing-header").count()).isEqualTo(1.0);
    }

    @Test
    void foreignOriginIs403AndCountedAsOriginMismatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/policies");
        request.addHeader("X-PolicyPilot-Client", "web");
        request.addHeader("Origin", "https://policypilot.liorshaya.com.evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(passes(request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(registry.counter("security.session.invalid", "reason", "origin-mismatch").count()).isEqualTo(1.0);
    }

    @Test
    void missingOriginIs403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/rulesets/x/versions/1/rules");
        request.addHeader("X-PolicyPilot-Client", "web");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(passes(request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private boolean passes(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicBoolean passed = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> passed.set(true));
        return passed.get();
    }
}
