package com.liorshaya.policypilot.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Seeded;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Sandbox authorization per entity type through the running API (Document 5, Authorization (sandbox): "an id guessed
 * from another sandbox returns 404 (no existence oracle)"; Security Test Plan, integration: every entity type
 * fetched and mutated with another sandbox's id). Policies are {@code SandboxIsolationIT}'s; this class walks the
 * rule set entities of day 5.
 */
class EntityIsolationIT extends ApiIntegrationTest {

    private static final String RULESETS = "/api/v1/rulesets";

    @Autowired
    private MeterRegistry registry;

    private String owner;
    private String stranger;

    @BeforeEach
    void twoSessions() {
        owner = api().login();
        stranger = api().login();
    }

    // Document 5. Expected: 404 NOT_FOUND
    @Test
    void anotherSandboxsRuleSetVersionReturns404() {
        UUID draft = draftOf(owner);

        HttpResponse<String> response = api().get(version(draft)).cookie(stranger).send();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("NOT_FOUND");
    }

    // Document 5. Expected: 404 NOT_FOUND and the draft unchanged
    @Test
    void anotherSandboxsDraftCannotBeEditedAndReturns404() {
        UUID draft = draftOf(owner);

        HttpResponse<String> response = api().method("PUT", version(draft) + "/rules").web().cookie(stranger)
                .json(Fixtures.lendingV1().toString()).send();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat((String) JsonPath.read(api().get(version(draft)).cookie(owner).send().body(), "$.status"))
                .isEqualTo("DRAFT");
    }

    // Document 5. Expected: 404 NOT_FOUND and the version still a draft
    @Test
    void anotherSandboxsDraftCannotBePublishedAndReturns404() {
        UUID draft = draftOf(owner);

        HttpResponse<String> response = api().post(version(draft) + "/publish").web().cookie(stranger).send();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat((String) JsonPath.read(api().get(version(draft)).cookie(owner).send().body(), "$.status"))
                .isEqualTo("DRAFT");
    }

    // Document 5: no existence oracle. Expected: identical responses apart from the trace id
    @Test
    void foreignAndMissingIdsGiveIdenticalResponses() {
        UUID draft = draftOf(owner);

        HttpResponse<String> foreign = api().get(version(draft)).cookie(stranger).send();
        HttpResponse<String> missing = api().get(version(UUID.randomUUID())).cookie(stranger).send();

        assertThat(foreign.statusCode()).isEqualTo(missing.statusCode());
        assertThat(withoutTraceId(foreign.body())).isEqualTo(withoutTraceId(missing.body()));
    }

    // Document 5, security.authz.denied. Expected: a foreign rule set id is counted, a missing one is not
    @Test
    void aForeignRuleSetLookupIncrementsAuthzDenied() {
        UUID draft = draftOf(owner);
        double before = registry.counter("security.authz.denied", "entity", "ruleset").count();

        api().get(version(draft)).cookie(stranger).send();
        api().get(version(UUID.randomUUID())).cookie(stranger).send();

        // test classes run in parallel and share the registry, so the assertion is on this test's own increment
        assertThat(registry.counter("security.authz.denied", "entity", "ruleset").count() - before)
                .isGreaterThanOrEqualTo(1.0);
    }

    // Document 5: the protected rows are readable from every sandbox. Expected: 200 for both sessions
    @Test
    void theProtectedVersionIsReadableFromEverySandbox() {
        UUID seeded = seededOf(owner);

        for (String session : List.of(owner, stranger)) {
            HttpResponse<String> response = api().get(version(seeded)).cookie(session).send();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat((Boolean) JsonPath.read(response.body(), "$.protected")).isTrue();
        }
    }

    // Document 5, Authorization (sandbox): the sandbox comes from the session, never from the request
    @Test
    void theSandboxComesFromTheCookieNeverFromTheRequest() {
        UUID draft = draftOf(owner);
        String ownerSandbox = owner.substring(0, owner.indexOf('.'));

        HttpResponse<String> response = api().get(version(draft) + "?sandboxId=" + ownerSandbox).cookie(stranger)
                .header("X-Sandbox-Id", ownerSandbox).send();

        assertThat(response.statusCode()).isEqualTo(404);
    }

    /** A DRAFT rule set of this session: its own copy of the seeded one, made by editing the protected version. */
    private UUID draftOf(String session) {
        String body = api().method("PUT", version(seededOf(session)) + "/rules").web().cookie(session)
                .json(Fixtures.lendingV1().toString()).send().body();
        return UUID.fromString(JsonPath.read(body, "$.rulesetId"));
    }

    private UUID seededOf(String session) {
        List<String> ids = JsonPath.read(api().get(RULESETS).cookie(session).send().body(),
                Seeded.LENDING_RULESET_ID);
        return UUID.fromString(ids.getFirst());
    }

    private static String version(UUID ruleset) {
        return RULESETS + "/" + ruleset + "/versions/1";
    }

    private static String withoutTraceId(String body) {
        return body.replaceAll("\"traceId\":\"[^\"]*\"", "");
    }
}
