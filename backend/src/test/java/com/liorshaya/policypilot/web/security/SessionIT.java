package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.Api;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * The signed session through the running API (Document 5, Cookie and Security Test Plan, integration: expired
 * cookie, tampered signature). Isolated because it moves the clock. {@code /api/docs} is the protected route used.
 */
@Isolated
class SessionIT extends ApiIntegrationTest {

    private static final String PROTECTED = "/api/docs";

    @Test
    void validCookieReachesAProtectedRoute() {
        String session = api().login("198.51.100.60");

        assertThat(api().get(PROTECTED).cookie(session).send().statusCode()).isEqualTo(200);
    }

    @Test
    void expiredCookieReturns401() {
        String session = api().login("198.51.100.61");
        clock.advance(Duration.ofHours(24));

        HttpResponse<String> response = api().get(PROTECTED).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("SESSION_INVALID");
    }

    @Test
    void tamperedCookieReturns401() {
        String session = api().login("198.51.100.62");
        String tampered = "00000000-0000-4000-8000-000000000000" + session.substring(36);

        HttpResponse<String> response = api().get(PROTECTED).cookie(tampered).send();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("SESSION_INVALID");
    }

    @Test
    void cookieIsRenewedOnUseAfterAnHour() {
        String session = api().login("198.51.100.63");
        clock.advance(Duration.ofHours(1));

        HttpResponse<String> response = api().get(PROTECTED).cookie(session).send();

        assertThat(Api.sessionCookie(response)).hasValueSatisfying(renewed -> {
            assertThat(renewed).startsWith(session.substring(0, 36)).isNotEqualTo(session);
            assertThat(renewed).contains(".1790244000.");
        });
    }

    @Test
    void renewedCookieOutlivesTheFirstOne() {
        String session = api().login("198.51.100.64");
        clock.advance(Duration.ofHours(12));
        String renewed = Api.sessionCookie(api().get(PROTECTED).cookie(session).send()).orElseThrow();
        clock.advance(Duration.ofHours(13));

        assertThat(api().get(PROTECTED).cookie(session).send().statusCode()).isEqualTo(401);
        assertThat(api().get(PROTECTED).cookie(renewed).send().statusCode()).isEqualTo(200);
    }

    // Expected: Document 2, CORS and headers: standard security headers and HSTS; HSTS is sent on HTTPS only, which
    // Railway's proxy announces with X-Forwarded-Proto
    @Test
    void securityHeadersAndHstsAreSet() {
        String session = api().login("198.51.100.65");

        HttpResponse<String> response = api().get(PROTECTED).cookie(session)
                .header("X-Forwarded-Proto", "https").send();

        assertThat(response.headers().firstValue("Strict-Transport-Security")).hasValueSatisfying(
                hsts -> assertThat(hsts).contains("max-age="));
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
    }
}
