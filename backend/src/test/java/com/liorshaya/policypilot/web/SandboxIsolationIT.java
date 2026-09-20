package com.liorshaya.policypilot.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Sandbox authorization for policies through the running API (Document 5, Authorization (sandbox): "an id guessed
 * from another sandbox returns 404 (no existence oracle)"; Security Test Plan, integration: Authorization).
 */
@Isolated("the security counters are shared by the whole context, so this class counts alone")
class SandboxIsolationIT extends ApiIntegrationTest {

    private static final String POLICIES = "/api/v1/policies";

    @Autowired
    private MeterRegistry registry;

    @Test
    void anotherSandboxsPolicyIdReturns404() {
        String id = createIn(api().login("198.51.100.130"));

        HttpResponse<String> response = api().get(POLICIES + "/" + id).cookie(api().login("198.51.100.131")).send();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void theOwnerStillSeesIt() {
        String owner = api().login("198.51.100.132");
        String id = createIn(owner);

        assertThat(api().get(POLICIES + "/" + id).cookie(owner).send().statusCode()).isEqualTo(200);
    }

    @Test
    void foreignAndMissingIdsGiveIdenticalResponses() {
        String id = createIn(api().login("198.51.100.133"));
        String other = api().login("198.51.100.134");

        HttpResponse<String> foreign = api().get(POLICIES + "/" + id).cookie(other).send();
        HttpResponse<String> missing = api().get(POLICIES + "/0f4c1c9e-0000-4000-8000-00000000dead").cookie(other).send();

        assertThat(foreign.statusCode()).isEqualTo(missing.statusCode());
        assertThat(withoutTraceId(foreign.body())).isEqualTo(withoutTraceId(missing.body()));
    }

    @Test
    void foreignLookupIncrementsAuthzDeniedAndAMissingOneDoesNot() {
        String id = createIn(api().login("198.51.100.135"));
        String other = api().login("198.51.100.136");
        double before = registry.counter("security.authz.denied", "entity", "policy").count();

        api().get(POLICIES + "/" + id).cookie(other).send();
        api().get(POLICIES + "/0f4c1c9e-0000-4000-8000-00000000beef").cookie(other).send();

        assertThat(registry.counter("security.authz.denied", "entity", "policy").count() - before).isEqualTo(1.0);
    }

    @Test
    void theSandboxComesFromTheCookieNeverFromTheRequest() {
        String owner = api().login("198.51.100.137");
        String id = createIn(owner);
        String ownerSandbox = owner.substring(0, 36);

        HttpResponse<String> response = api().get(POLICIES + "/" + id + "?sandboxId=" + ownerSandbox)
                .cookie(api().login("198.51.100.138")).header("X-Sandbox-Id", ownerSandbox).send();

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private String createIn(String session) {
        String body = "{\"title\":\"Private\",\"language\":\"en\",\"text\":\"Applicants must be at least 21.\"}";
        return JsonPath.read(api().post(POLICIES).web().cookie(session).json(body).send().body(), "$.id");
    }

    private static String withoutTraceId(String body) {
        return body.replaceAll("\"traceId\":\"[^\"]*\"", "");
    }
}
