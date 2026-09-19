package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.common.SecurityEvents;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The session filter on {@code /api/**} (Document 2, Security and Demo Protections; Document 6 layout names this
 * class). The filter authenticates or leaves the request anonymous; the 401 itself is the authorization rule's,
 * proven through the running API by {@code AuthenticationWalkIT}.
 */
class AccessCodeFilterTest {

    private static final UUID SANDBOX = UUID.fromString("7f1c0e7a-1111-4222-8333-944445555666");
    private static final Instant NOW = Instant.parse("2026-09-24T09:00:00Z");

    private final SessionCookies cookies = new SessionCookies("a-cookie-secret-of-more-than-32-bytes");
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AccessCodeFilter filter = new AccessCodeFilter(cookies, Clock.fixed(NOW, ZoneOffset.UTC),
            new SecurityEvents(registry, "salt".getBytes(StandardCharsets.UTF_8)), WebSecurityConfig.publicRoutes());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestWithoutCookieStaysAnonymousAndCountsAsMissing() throws Exception {
        Authentication authentication = run(apiRequest(), new MockHttpServletResponse());

        assertThat(authentication).isNull();
        assertThat(registry.counter("security.session.invalid", "reason", "missing").count()).isEqualTo(1.0);
    }

    @Test
    void requestWithABadSignatureStaysAnonymousAndCountsItsReason() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.setCookies(new Cookie("pp_session", SANDBOX + "." + NOW.getEpochSecond() + ".forged"));

        Authentication authentication = run(request, new MockHttpServletResponse());

        assertThat(authentication).isNull();
        assertThat(registry.counter("security.session.invalid", "reason", "bad-signature").count()).isEqualTo(1.0);
    }

    @Test
    void validCookieAuthenticatesTheRequestAsItsSandbox() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.setCookies(new Cookie("pp_session", cookies.value(SANDBOX, NOW)));

        Authentication authentication = run(request, new MockHttpServletResponse());

        assertThat(authentication.getPrincipal()).isEqualTo(new SandboxSession(SANDBOX, NOW));
    }

    @Test
    void freshCookieIsNotReissued() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.setCookies(new Cookie("pp_session", cookies.value(SANDBOX, NOW.minus(Duration.ofMinutes(59)))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(request, response);

        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void cookieOlderThanAnHourIsReissuedForTheSameSandbox() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.setCookies(new Cookie("pp_session", cookies.value(SANDBOX, NOW.minus(Duration.ofHours(1)))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(request, response);

        assertThat(response.getHeader("Set-Cookie")).startsWith("pp_session=" + cookies.value(SANDBOX, NOW) + ";");
    }

    @Test
    void healthCheckIsNotFiltered() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health"))).isTrue();
    }

    @Test
    void codeExchangeIsNotFiltered() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/auth/code"))).isTrue();
    }

    @Test
    void anythingElseUnderApiIsFiltered() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/auth/code"))).isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/docs"))).isFalse();
    }

    private MockHttpServletRequest apiRequest() {
        return new MockHttpServletRequest("GET", "/api/v1/policies/" + UUID.randomUUID());
    }

    private Authentication run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        filter.doFilter(request, response, new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
