package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.Api;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * The three CSRF defenses of Document 5 through the running API (Document 5, CSRF; Document 6 layout names this
 * class): CORS allows only the web app's origin with credentials; every state-changing request carries
 * {@code X-PolicyPilot-Client: web} and an allowlisted {@code Origin}; a refusal is 403 {@code CSRF_REJECTED}.
 */
class CsrfDefensesIT extends ApiIntegrationTest {

    private static final String AUTH_CODE = "/api/v1/auth/code";
    private static final String FOREIGN_ORIGIN = "https://evil.example";

    @Test
    void postWithoutTheClientHeaderIsRejected() {
        HttpResponse<String> response = api().post(AUTH_CODE).header("Origin", Api.WEB_ORIGIN)
                .json(code(Api.ACCESS_CODE)).send();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("CSRF_REJECTED");
    }

    @Test
    void postFromAForeignOriginIsRejected() {
        HttpResponse<String> response = api().post(AUTH_CODE).header("Origin", FOREIGN_ORIGIN)
                .header("X-PolicyPilot-Client", "web").json(code(Api.ACCESS_CODE)).send();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("CSRF_REJECTED");
    }

    @Test
    void postWithoutAnOriginIsRejected() {
        HttpResponse<String> response = api().post(AUTH_CODE).header("X-PolicyPilot-Client", "web")
                .json(code(Api.ACCESS_CODE)).send();

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void postWithAnotherClientValueIsRejected() {
        HttpResponse<String> response = api().post(AUTH_CODE).header("Origin", Api.WEB_ORIGIN)
                .header("X-PolicyPilot-Client", "curl").json(code(Api.ACCESS_CODE)).send();

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void postFromTheAllowlistedOriginWithTheHeaderPasses() {
        HttpResponse<String> response = api().post(AUTH_CODE).web().from("198.51.100.40")
                .json(code(Api.ACCESS_CODE)).send();

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void codeExchangeRequiresTheHeaderAndAnAllowlistedOrigin() {
        HttpResponse<String> response = api().post(AUTH_CODE).from("198.51.100.41").json(code(Api.ACCESS_CODE)).send();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(Api.sessionCookie(response)).isEmpty();
    }

    @Test
    void authenticatedPostWithoutTheHeaderIsRejectedBeforeRouting() {
        String session = api().login("198.51.100.42");

        HttpResponse<String> response = api().post("/api/v1/policies").cookie(session)
                .header("Origin", Api.WEB_ORIGIN).json("{}").send();

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void getNeedsNeitherHeaderNorOrigin() {
        String session = api().login("198.51.100.43");

        HttpResponse<String> response = api().get("/api/docs").cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void preflightFromTheAllowlistedOriginAllowsCredentials() {
        HttpResponse<String> response = preflight(Api.WEB_ORIGIN);

        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(Api.WEB_ORIGIN);
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
    }

    @Test
    void preflightFromAForeignOriginGetsNoCorsHeaders() {
        HttpResponse<String> response = preflight(FOREIGN_ORIGIN);

        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        assertThat(response.statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> preflight(String origin) {
        return api().method("OPTIONS", AUTH_CODE)
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-policypilot-client")
                .send();
    }

    private static String code(String code) {
        return "{\"code\":\"" + code + "\"}";
    }
}
