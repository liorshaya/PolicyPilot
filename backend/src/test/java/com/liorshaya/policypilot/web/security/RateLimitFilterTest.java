package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.support.MutableClock;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import com.liorshaya.policypilot.web.error.TraceIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/**
 * The rate limits and their routing (Document 5, Availability and Abuse Resistance: code exchange 5 per minute per
 * IP; model-calling endpoints 20 per minute per IP and 60 per hour per sandbox; other endpoints 120 per minute per
 * IP; Document 2: 429 with Retry-After). The model limits use the defaults of Document 2's properties (20, 60).
 */
class RateLimitFilterTest {

    private static final Instant T0 = Instant.parse("2026-09-24T09:00:00Z");
    private static final UUID SANDBOX = UUID.fromString("7f1c0e7a-1111-4222-8333-944445555666");

    private final MutableClock clock = new MutableClock(T0);
    private final RateLimits limits = new RateLimits(clock, 20, 60);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void codeExchangeAllowsFivePerMinutePerIp() {
        assertThat(allowed(RateLimits.EndpointClass.AUTH, "198.51.100.1", null, 6)).isEqualTo(5);
    }

    @Test
    void otherEndpointsAllow120PerMinutePerIp() {
        assertThat(allowed(RateLimits.EndpointClass.OTHER, "198.51.100.1", SANDBOX, 121)).isEqualTo(120);
    }

    @Test
    void modelCallingEndpointsAllow20PerMinutePerIp() {
        assertThat(allowed(RateLimits.EndpointClass.MODEL, "198.51.100.1", SANDBOX, 21)).isEqualTo(20);
    }

    @Test
    void modelCallingEndpointsAllow60PerHourPerSandbox() {
        int passed = 0;
        for (int ip = 1; ip <= 4; ip++) {
            passed += allowed(RateLimits.EndpointClass.MODEL, "198.51.100." + ip, SANDBOX, 20);
        }

        assertThat(passed).isEqualTo(60);
        assertThat(limits.tryConsume(RateLimits.EndpointClass.MODEL, "198.51.100.9", SANDBOX)).isPresent();
    }

    @Test
    void theSandboxHourlyLimitWaitsForTheHourNotTheMinute() {
        for (int ip = 1; ip <= 3; ip++) {
            allowed(RateLimits.EndpointClass.MODEL, "198.51.100." + ip, SANDBOX, 20);
        }

        Optional<Duration> wait = limits.tryConsume(RateLimits.EndpointClass.MODEL, "198.51.100.9", SANDBOX);

        assertThat(wait).contains(Duration.ofHours(1));
    }

    @Test
    void anonymousModelRequestIsLimitedByItsIpOnly() {
        assertThat(allowed(RateLimits.EndpointClass.MODEL, "198.51.100.1", null, 21)).isEqualTo(20);
    }

    @Test
    void rejectionTellsTheWaitUntilTheWindowRefills() {
        allowed(RateLimits.EndpointClass.AUTH, "198.51.100.1", null, 5);
        clock.advance(Duration.ofSeconds(20));

        assertThat(limits.tryConsume(RateLimits.EndpointClass.AUTH, "198.51.100.1", null)).contains(Duration.ofSeconds(40));
    }

    @Test
    void bucketsRefillAfterTheirWindow() {
        allowed(RateLimits.EndpointClass.AUTH, "198.51.100.1", null, 5);
        clock.advance(Duration.ofMinutes(1));

        assertThat(allowed(RateLimits.EndpointClass.AUTH, "198.51.100.1", null, 6)).isEqualTo(5);
    }

    @Test
    void aRefusedRequestConsumesNothing() {
        for (int ip = 1; ip <= 3; ip++) {
            allowed(RateLimits.EndpointClass.MODEL, "198.51.100." + ip, SANDBOX, 20);
        }
        allowed(RateLimits.EndpointClass.MODEL, "198.51.100.9", SANDBOX, 1);

        assertThat(allowed(RateLimits.EndpointClass.MODEL, "198.51.100.9", UUID.randomUUID(), 20)).isEqualTo(20);
    }

    @Test
    void clientsDoNotShareBuckets() {
        allowed(RateLimits.EndpointClass.AUTH, "198.51.100.1", null, 5);

        assertThat(limits.tryConsume(RateLimits.EndpointClass.AUTH, "198.51.100.2", null)).isEmpty();
    }

    @Test
    void classesDoNotShareBuckets() {
        allowed(RateLimits.EndpointClass.AUTH, "198.51.100.1", null, 5);

        assertThat(limits.tryConsume(RateLimits.EndpointClass.OTHER, "198.51.100.1", null)).isEmpty();
    }

    @Test
    void fullBucketsArePurgedOnceThereAreTooMany() {
        for (int i = 0; i <= RateLimits.PURGE_THRESHOLD; i++) {
            limits.tryConsume(RateLimits.EndpointClass.OTHER, "10.0." + (i / 256) + "." + (i % 256), null);
        }
        clock.advance(Duration.ofMinutes(1));

        limits.tryConsume(RateLimits.EndpointClass.OTHER, "192.0.2.1", null);

        assertThat(limits.trackedBuckets()).isEqualTo(1);
    }

    // Expected: the routes of Document 2's API table and the endpoint classes of Document 5's limits table
    @ParameterizedTest(name = "{0} {1} is {2}")
    @CsvSource({
        "POST, /api/v1/auth/code, AUTH",
        "GET, /api/v1/auth/code, OTHER",
        "POST, /api/v1/policies/0f4c1c9e-0000-4000-8000-000000000001/rulesets, MODEL",
        "POST, /api/v1/chat/sessions/0f4c1c9e-0000-4000-8000-000000000001/messages, MODEL",
        "POST, /api/v1/rulesets/0f4c1c9e-0000-4000-8000-000000000001/versions/2/changes, MODEL",
        "POST, /api/v1/decisions/0f4c1c9e-0000-4000-8000-000000000001/explain, MODEL",
        "POST, /api/v1/policies, OTHER",
        "GET, /api/v1/policies/0f4c1c9e-0000-4000-8000-000000000001, OTHER",
        "GET, /api/docs, OTHER"})
    void routesAreClassifiedAsDocument5Lists(String method, String path, RateLimits.EndpointClass expected) {
        assertThat(RateLimitFilter.classify(new MockHttpServletRequest(method, path))).isEqualTo(expected);
    }

    @Test
    void theHealthCheckIsNeverLimited() {
        RateLimitFilter filter = new RateLimitFilter(limits, null, null);

        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/docs"))).isFalse();
    }

    @Test
    void theSixthCodeExchangeGets429WithRetryAfterAndIsCounted() throws Exception {
        RateLimitFilter filter = filter();
        for (int i = 0; i < 5; i++) {
            assertThat(run(filter, exchangeFrom("198.51.100.7")).getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse sixth = run(filter, exchangeFrom("198.51.100.7"));

        assertThat(sixth.getStatus()).isEqualTo(429);
        assertThat(sixth.getHeader("Retry-After")).isEqualTo("60");
        assertThat(registry.counter("security.ratelimit.hit", "endpoint", "auth").count()).isEqualTo(1.0);
    }

    @Test
    void anAuthenticatedRequestIsCountedAgainstItsSandbox() throws Exception {
        RateLimitFilter filter = filter();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SandboxSession(SANDBOX, T0), null, List.of()));
        try {
            for (int ip = 1; ip <= 3; ip++) {
                for (int i = 0; i < 20; i++) {
                    run(filter, modelCallFrom("198.51.100." + ip));
                }
            }

            assertThat(run(filter, modelCallFrom("198.51.100.9")).getStatus()).isEqualTo(429);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private RateLimitFilter filter() {
        return new RateLimitFilter(limits,
                new ErrorResponses(new TraceIds(new SimpleTracer()), JsonMapper.builder().build()),
                new SecurityEvents(registry, "salt".getBytes(StandardCharsets.UTF_8)));
    }

    private static MockHttpServletRequest exchangeFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/code");
        request.setRemoteAddr(ip);
        return request;
    }

    private static MockHttpServletRequest modelCallFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/api/v1/policies/0f4c1c9e-0000-4000-8000-000000000001/rulesets");
        request.setRemoteAddr(ip);
        return request;
    }

    private static MockHttpServletResponse run(RateLimitFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        return response;
    }

    private int allowed(RateLimits.EndpointClass endpointClass, String ip, UUID sandbox, int attempts) {
        int passed = 0;
        for (int i = 0; i < attempts; i++) {
            if (limits.tryConsume(endpointClass, ip, sandbox).isEmpty()) {
                passed++;
            }
        }
        return passed;
    }

    // Document 5, Availability: Batch decide, 5 requests per minute per sandbox
    @Test
    void batchDecideAllowsFivePerMinutePerSandbox() {
        UUID sandbox = UUID.randomUUID();

        for (int i = 0; i < RateLimits.BATCH_PER_MINUTE_PER_SANDBOX; i++) {
            assertThat(limits.tryConsume(RateLimits.EndpointClass.BATCH, "203.0.113.7", sandbox)).isEmpty();
        }

        assertThat(limits.tryConsume(RateLimits.EndpointClass.BATCH, "203.0.113.7", sandbox)).isPresent();
    }

    // Document 5: the batch limit is per sandbox, so the address it comes from does not matter
    @Test
    void theBatchLimitFollowsTheSandboxNotTheAddress() {
        UUID sandbox = UUID.randomUUID();
        for (int i = 0; i < RateLimits.BATCH_PER_MINUTE_PER_SANDBOX; i++) {
            limits.tryConsume(RateLimits.EndpointClass.BATCH, "203.0.113.7", sandbox);
        }

        assertThat(limits.tryConsume(RateLimits.EndpointClass.BATCH, "203.0.113.8", sandbox)).isPresent();
        assertThat(limits.tryConsume(RateLimits.EndpointClass.BATCH, "203.0.113.7", UUID.randomUUID())).isEmpty();
    }
}
