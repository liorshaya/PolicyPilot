package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.Api;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * {@code POST /api/v1/auth/code} through the running API (Document 2, API Surface and error envelope; Document 5,
 * Code exchange, Brute force, Security Logging). Isolated: the lockout tests move the clock and count exact
 * increments of the security counters. Every test comes from its own client IP.
 */
@Isolated
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerContractIT extends ApiIntegrationTest {

    @Autowired
    private MeterRegistry registry;

    // Expected: Document 5, sequence diagram; 2026-09-24T09:00:00Z (the test clock) is epoch second 1790240400
    @Test
    void correctCodeReturns204AndSetsTheSessionCookie() {
        HttpResponse<String> response = exchange("198.51.100.10", Api.ACCESS_CODE);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.headers().firstValue("Set-Cookie")).hasValueSatisfying(cookie -> assertThat(cookie)
                .matches("pp_session=[0-9a-f-]{36}\\.1790240400\\.[A-Za-z0-9_-]{43};.*")
                .contains("; Path=/", "; Max-Age=86400", "; Secure", "; HttpOnly", "; SameSite=Lax"));
    }

    @Test
    void wrongCodeReturns401WithTheEnvelopeAndNoCookie() {
        HttpResponse<String> response = exchange("198.51.100.11", "wrongone");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("ACCESS_CODE_INVALID");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void aVisitorWithAValidCookieKeepsTheirSandbox() {
        String first = api().login("198.51.100.12");

        HttpResponse<String> again = api().post(ApiPaths.AUTH_CODE).web().from("198.51.100.12").cookie(first)
                .json("{\"code\":\"" + Api.ACCESS_CODE + "\"}").send();

        assertThat(Api.sessionCookie(again)).hasValueSatisfying(
                cookie -> assertThat(cookie.substring(0, 36)).isEqualTo(first.substring(0, 36)));
    }

    @Test
    void twoVisitorsGetTwoSandboxes() {
        String one = api().login("198.51.100.13");
        String two = api().login("198.51.100.14");

        assertThat(one.substring(0, 36)).isNotEqualTo(two.substring(0, 36));
    }

    @Test
    void theCodeNeverAppearsInTheLogs(CapturedOutput output) {
        exchange("198.51.100.15", Api.ACCESS_CODE);
        exchange("198.51.100.15", "wrongone");

        assertThat(output.getAll()).doesNotContain(Api.ACCESS_CODE);
    }

    // Expected: Document 5, Brute force: 20 failures within 15 minutes lock the IP for 15 minutes (900 s)
    @Test
    void twentyFailuresLockTheIpAndTheLockLiftsAfterFifteenMinutes() {
        String ip = "198.51.100.16";
        for (int i = 0; i < 20; i++) {
            assertThat(exchange(ip, "wrongone").statusCode()).isEqualTo(401);
        }

        HttpResponse<String> locked = exchange(ip, Api.ACCESS_CODE);
        assertThat(locked.statusCode()).isEqualTo(429);
        assertThat(locked.headers().firstValue("Retry-After")).contains("900");

        clock.advance(Duration.ofMinutes(15));
        assertThat(exchange(ip, Api.ACCESS_CODE).statusCode()).isEqualTo(204);
    }

    @Test
    void theCorrectCodeIsRefusedDuringTheLockout() {
        String ip = "198.51.100.17";
        for (int i = 0; i < 20; i++) {
            exchange(ip, "wrongone");
        }
        clock.advance(Duration.ofMinutes(14));

        HttpResponse<String> response = exchange(ip, Api.ACCESS_CODE);

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("RATE_LIMITED");
        assertThat(response.headers().firstValue("Retry-After")).contains("60");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void lockoutIsPerIp() {
        for (int i = 0; i < 20; i++) {
            exchange("198.51.100.18", "wrongone");
        }

        assertThat(exchange("198.51.100.19", Api.ACCESS_CODE).statusCode()).isEqualTo(204);
    }

    @Test
    void failuresAndTheLockoutIncrementTheirCounters() {
        double failedBefore = registry.counter("security.auth.failed").count();
        double lockoutsBefore = registry.counter("security.auth.lockout").count();

        for (int i = 0; i < 20; i++) {
            exchange("198.51.100.20", "wrongone");
        }

        assertThat(registry.counter("security.auth.failed").count() - failedBefore).isEqualTo(20.0);
        assertThat(registry.counter("security.auth.lockout").count() - lockoutsBefore).isEqualTo(1.0);
    }

    @Test
    void malformedBodyIsA400() {
        HttpResponse<String> response = api().post(ApiPaths.AUTH_CODE).web().from("198.51.100.21")
                .json("{\"code\": ").send();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("REQUEST_INVALID");
    }

    private HttpResponse<String> exchange(String ip, String code) {
        return api().post(ApiPaths.AUTH_CODE).web().from(ip).json("{\"code\":\"" + code + "\"}").send();
    }
}
