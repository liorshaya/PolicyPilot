package com.liorshaya.policypilot.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The OpenAPI authentication walk (Document 5, Security Test Plan, integration: "no cookie gives 401 on every
 * /api/** route, walked from the OpenAPI document, so a new endpoint cannot be forgotten"). The expected routes are
 * read from the document the running API serves; the only exception is the code exchange (Document 2, API Surface).
 */
class AuthenticationWalkIT extends ApiIntegrationTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");
    private static final String SOME_ID = "0f4c1c9e-0000-4000-8000-000000000001";

    @Test
    void everyRouteOfTheOpenApiDocumentAnswers401WithoutACookie() {
        List<String> refused = new ArrayList<>();
        List<String> walked = new ArrayList<>();
        for (String route : documentedRoutes()) {
            if (route.equals("POST /api/v1/auth/code")) {
                continue;
            }
            String method = route.substring(0, route.indexOf(' '));
            String path = route.substring(route.indexOf(' ') + 1).replaceAll("\\{[^}]+}", SOME_ID);
            HttpResponse<String> response = api().method(method, path).web().from("198.51.100.80").json("{}").send();
            walked.add(route);
            if (response.statusCode() != 401 || !response.body().contains("\"SESSION_INVALID\"")) {
                refused.add(route + " answered " + response.statusCode());
            }
        }

        assertThat(refused).as("walked %s", walked).isEmpty();
    }

    @Test
    void theWalkReadsTheDocumentTheApiServes() {
        assertThat(documentedRoutes()).contains("POST /api/v1/auth/code");
    }

    @Test
    void apiDocsRequireTheCookie() {
        HttpResponse<String> response = api().get("/api/docs").send();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("SESSION_INVALID");
    }

    @Test
    void healthIsPublicAndReturnsOnlyTheStatus() {
        HttpResponse<String> response = api().get("/actuator/health").send();

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> body = JsonPath.read(response.body(), "$");
        assertThat(body.keySet()).isSubsetOf("status", "groups");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/info", "/actuator/env", "/actuator/prometheus", "/api/docs/ui"})
    void noOtherOperationsEndpointIsPublic(String path) {
        assertThat(api().get(path).send().statusCode()).isEqualTo(401);
    }

    /** Every {@code METHOD /path} of the OpenAPI document, read with a valid session. */
    private List<String> documentedRoutes() {
        String session = api().login("198.51.100.81");
        String document = api().get("/api/docs").cookie(session).send().body();
        Map<String, Map<String, Object>> paths = JsonPath.read(document, "$.paths");
        List<String> routes = new ArrayList<>();
        paths.forEach((path, operations) -> operations.keySet().stream()
                .filter(HTTP_METHODS::contains)
                .forEach(method -> routes.add(method.toUpperCase() + " " + path)));
        return routes;
    }
}
