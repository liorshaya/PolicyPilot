package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.web.error.ApiExceptionHandler;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import com.liorshaya.policypilot.web.error.TraceIds;
import com.liorshaya.policypilot.web.security.AccessCodeVerifier;
import com.liorshaya.policypilot.web.security.LoginThrottle;
import com.liorshaya.policypilot.web.security.SessionCookies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The code exchange controller without Spring (Document 5, Code exchange and Brute force): the real verifier,
 * throttle, cookie codec and envelope on a fixed clock. The same route through the running API, filters included,
 * is {@code AuthControllerContractIT}.
 */
class AuthControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-24T09:00:00Z");
    private static final String CODE = "qwertyui";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SessionCookies cookies = new SessionCookies("a-cookie-secret-of-more-than-32-bytes");
    private final LoginThrottle throttle = new LoginThrottle();
    private final MockMvc mvc;

    AuthControllerTest() {
        JsonMapper json = JsonMapper.builder().build();
        ErrorResponses errors = new ErrorResponses(new TraceIds(new SimpleTracer()), json);
        AuthController controller = new AuthController(new AccessCodeVerifier(CODE), throttle, cookies,
                new SecurityEvents(registry, "salt".getBytes(StandardCharsets.UTF_8)), Clock.fixed(NOW, ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(errors))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
                .build();
    }

    @Test
    void correctCodeReturns204WithANewSandboxCookie() throws Exception {
        MockHttpServletResponse response = perform(exchange(CODE, "198.51.100.1"));

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHeader("Set-Cookie")).matches("pp_session=[0-9a-f-]{36}\\.1790240400\\..*");
    }

    @Test
    void aValidCookieKeepsItsSandbox() throws Exception {
        UUID sandbox = UUID.fromString("7f1c0e7a-1111-4222-8333-944445555666");

        MockHttpServletResponse response = perform(exchange(CODE, "198.51.100.1")
                .cookie(new Cookie("pp_session", cookies.value(sandbox, NOW.minusSeconds(60)))));

        assertThat(response.getHeader("Set-Cookie")).startsWith("pp_session=" + sandbox + ".1790240400.");
    }

    @Test
    void wrongCodeReturns401AndCountsTheFailure() throws Exception {
        MockHttpServletResponse response = perform(exchange("asdfghjk", "198.51.100.2"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("ACCESS_CODE_INVALID");
        assertThat(registry.counter("security.auth.failed").count()).isEqualTo(1.0);
    }

    @Test
    void theTwentiethFailureStartsALockoutAndCountsIt() throws Exception {
        for (int i = 0; i < 20; i++) {
            perform(exchange("asdfghjk", "198.51.100.3"));
        }

        assertThat(registry.counter("security.auth.lockout").count()).isEqualTo(1.0);
        assertThat(throttle.lockedFor("198.51.100.3", NOW)).isPresent();
    }

    @Test
    void aLockedIpGets429WithRetryAfterEvenWithTheCorrectCode() throws Exception {
        for (int i = 0; i < 20; i++) {
            throttle.recordFailure("198.51.100.4", NOW);
        }

        MockHttpServletResponse response = perform(exchange(CODE, "198.51.100.4"));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("900");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void aMissingCodeIsAWrongCode() throws Exception {
        MockHttpServletResponse response = perform(post(ApiPaths.AUTH_CODE)
                .with(request -> {
                    request.setRemoteAddr("198.51.100.5");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON).content("{}"));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private MockHttpServletRequestBuilder exchange(String code, String ip) {
        return post(ApiPaths.AUTH_CODE)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}");
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }
}
