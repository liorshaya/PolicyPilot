package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.Api;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Body, upload and charset limits and 429 through the running API (Document 5, Security Test Plan, integration:
 * Limits; limits table: request body 1 MB, uploads 2 MB, other endpoints 120 per minute per IP; Encoding: UTF-8
 * only; JSON: nesting depth 32). The code exchange serves as the JSON route: its body is parsed before any check.
 */
class RequestLimitsIT extends ApiIntegrationTest {

    private static final String AUTH_CODE = "/api/v1/auth/code";
    private static final int ONE_MEGABYTE = 1024 * 1024;

    @Autowired
    private MeterRegistry registry;

    @Test
    void jsonBodyOverOneMegabyteReturns413() {
        HttpResponse<String> response = api().post(AUTH_CODE).web().json(codeOfLength(ONE_MEGABYTE)).send();

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void jsonBodyJustUnderOneMegabyteIsRead() {
        String body = codeOfLength(ONE_MEGABYTE - "{\"code\":\"\"}".length());

        assertThat(body.getBytes(StandardCharsets.UTF_8)).hasSize(ONE_MEGABYTE);
        assertThat(api().post(AUTH_CODE).web().json(body).send().statusCode()).isEqualTo(401);
    }

    @Test
    void chunkedBodyOverOneMegabyteReturns413() throws IOException, InterruptedException {
        byte[] body = codeOfLength(2 * ONE_MEGABYTE).getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(Api.base(port) + AUTH_CODE))
                .header("Content-Type", "application/json")
                .header("Origin", Api.WEB_ORIGIN)
                .header("X-PolicyPilot-Client", "web")
                .header("X-Forwarded-For", "198.51.100.90")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(request.bodyPublisher().orElseThrow().contentLength()).isNegative();
        assertThat(response.statusCode()).isEqualTo(413);
    }

    @Test
    void uploadOverTwoMegabytesReturns413() {
        byte[] body = new byte[2 * ONE_MEGABYTE + 64 * 1024 + 1];

        HttpResponse<String> response = api().post("/api/v1/policies").web()
                .body("multipart/form-data; boundary=x", body).send();

        assertThat(response.statusCode()).isEqualTo(413);
    }

    @Test
    void nonUtf8CharsetReturns415() {
        HttpResponse<String> response = api().post(AUTH_CODE).web()
                .body("application/json; charset=ISO-8859-1", "{\"code\":\"x\"}".getBytes(StandardCharsets.ISO_8859_1))
                .send();

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void explicitUtf8CharsetIsAccepted() {
        HttpResponse<String> response = api().post(AUTH_CODE).web()
                .body("application/json; charset=utf-8", "{\"code\":\"x\"}".getBytes(StandardCharsets.UTF_8)).send();

        assertThat(response.statusCode()).isEqualTo(401);
    }

    // Expected: Document 5, JSON and deserialization: nesting depth limit 32
    @Test
    void jsonNestedDeeperThan32Returns400() {
        String deep = "[".repeat(33) + "]".repeat(33);

        HttpResponse<String> response = api().post(AUTH_CODE).web().json("{\"code\":" + deep + "}").send();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("REQUEST_INVALID");
    }

    @Test
    void rateLimitReturns429WithRetryAfterAndTheEnvelope() {
        String session = api().login("198.51.100.91");
        for (int i = 0; i < 120; i++) {
            assertThat(api().get("/api/docs").cookie(session).from("198.51.100.92").send().statusCode()).isEqualTo(200);
        }

        HttpResponse<String> limited = api().get("/api/docs").cookie(session).from("198.51.100.92").send();

        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).contains("60");
        assertThat((String) JsonPath.read(limited.body(), "$.code")).isEqualTo("RATE_LIMITED");
    }

    @Test
    void rateLimitHitIncrementsItsCounter() {
        double before = registry.counter("security.ratelimit.hit", "endpoint", "auth").count();
        for (int i = 0; i < 6; i++) {
            api().post(AUTH_CODE).web().from("198.51.100.93").json("{\"code\":\"wrongone\"}").send();
        }

        assertThat(registry.counter("security.ratelimit.hit", "endpoint", "auth").count() - before).isEqualTo(1.0);
    }

    private static String codeOfLength(int length) {
        return "{\"code\":\"" + "a".repeat(length) + "\"}";
    }
}
