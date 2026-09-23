package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.Requirement;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The change route's refusals against the OpenAPI document the API serves (Document 2, API Surface: "before any
 * stream opens, 404 for a version the sandbox cannot see and 409 for one that is not PUBLISHED"; the body
 * {@code {text}} held to Document 5's input limits). The stream itself is ChangeStreamIT's.
 */
@Requirement("FR-17")
class ChangeControllerContractIT extends ApiIntegrationTest {

    private static final String CHANGES = "/api/v1/rulesets/{id}/versions/{no}/changes";
    private static final String REQUEST = "{\"text\":\"העלאת סף ההכנסה המינימלית ל-9,000 ש\\\"ח\"}";

    private String session;
    private OpenApiContract contract;
    private String seeded;

    @BeforeEach
    void logIn() {
        session = api().login();
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
        List<String> ids = JsonPath.read(api().get("/api/v1/rulesets").cookie(session).send().body(),
                "$.rulesets[?(@.protected == true)].id");
        seeded = ids.getFirst();
    }

    // Document 5, no existence oracle. Expected: an unknown rule set is 404
    @Test
    void anUnknownRuleSetMatchesTheDocumented404() {
        HttpResponse<String> submitted = submit(session, UUID.randomUUID().toString(), 1, REQUEST);

        assertThat(submitted.statusCode()).isEqualTo(404);
        assertThat(contract.violations("post", CHANGES, 404, submitted.body())).isEmpty();
    }

    // Document 5, Authorization (sandbox): another sandbox's version reads as absent. Expected: 404, not 409, for a
    // draft the stranger's sandbox does not own
    @Test
    void anotherSandboxsVersionMatchesTheDocumented404() {
        String draft = draftOf(session);
        String stranger = api().login("198.51.100.9");

        HttpResponse<String> submitted = submit(stranger, draft, 1, REQUEST);

        assertThat(submitted.statusCode()).isEqualTo(404);
        assertThat(contract.violations("post", CHANGES, 404, submitted.body())).isEmpty();
    }

    // Document 2: a change is proposed against a PUBLISHED version. Expected: VERSION_STATUS_CONFLICT for a draft
    @Test
    void aDraftMatchesTheDocumented409() {
        HttpResponse<String> submitted = submit(session, draftOf(session), 1, REQUEST);

        assertThat(submitted.statusCode()).isEqualTo(409);
        assertThat(contract.violations("post", CHANGES, 409, submitted.body())).isEmpty();
        assertThat((String) JsonPath.read(submitted.body(), "$.code")).isEqualTo("VERSION_STATUS_CONFLICT");
    }

    // Document 5, Input limits: a change request is limited like a chat message. Expected: 400 for a blank text, the
    // detail at /text
    @Test
    void aBlankTextMatchesTheDocumented400() {
        HttpResponse<String> submitted = submit(session, seeded, 1, "{\"text\":\"   \"}");

        assertThat(submitted.statusCode()).isEqualTo(400);
        assertThat(contract.violations("post", CHANGES, 400, submitted.body())).isEmpty();
        assertThat((String) JsonPath.read(submitted.body(), "$.details[0].path")).isEqualTo("/text");
    }

    private HttpResponse<String> submit(String cookie, String rulesetId, int versionNo, String body) {
        return api().post(CHANGES.replace("{id}", rulesetId).replace("{no}", String.valueOf(versionNo))).web()
                .cookie(cookie).json(body).send();
    }

    /** An edit of the seeded rule set, which forks it into a draft of the session's sandbox. */
    private String draftOf(String cookie) {
        return JsonPath.read(api().method("PUT", "/api/v1/rulesets/" + seeded + "/versions/1/rules").web()
                .cookie(cookie).json(Fixtures.lendingV1().toString()).send().body(), "$.rulesetId");
    }
}
