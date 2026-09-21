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
 * The chat routes against the OpenAPI document the API serves (Document 2, API Surface: {@code POST /chat/sessions}
 * with {@code {rulesetId, versionNo}}, 201, 404 for a version the sandbox cannot see, 409 for a DRAFT; the messages
 * route's refusals before any stream opens). The streams themselves are ChatStreamIT's.
 */
@Requirement("FR-13")
class ChatControllerContractIT extends ApiIntegrationTest {

    private static final String SESSIONS = "/api/v1/chat/sessions";
    private static final String MESSAGES = "/api/v1/chat/sessions/{id}/messages";

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

    // Document 2: 201 with the session id, the version and its language. Expected: the served document, and he, the
    // lending rule set's language
    @Test
    void openingASessionMatchesTheDocumented201() {
        HttpResponse<String> opened = open(seeded, 1);

        assertThat(opened.statusCode()).isEqualTo(201);
        assertThat(contract.violations("post", SESSIONS, 201, opened.body())).isEmpty();
        assertThat((String) JsonPath.read(opened.body(), "$.language")).isEqualTo("he");
        assertThat((Integer) JsonPath.read(opened.body(), "$.versionNo")).isEqualTo(1);
    }

    // Document 5, no existence oracle. Expected: an unknown rule set is 404
    @Test
    void anUnknownRuleSetMatchesTheDocumented404() {
        HttpResponse<String> opened = open(UUID.randomUUID().toString(), 1);

        assertThat(opened.statusCode()).isEqualTo(404);
        assertThat(contract.violations("post", SESSIONS, 404, opened.body())).isEmpty();
    }

    // Document 2: 409 for a DRAFT. Expected: VERSION_STATUS_CONFLICT for the sandbox's own draft
    @Test
    void aDraftMatchesTheDocumented409() {
        String draft = JsonPath.read(api().method("PUT", "/api/v1/rulesets/" + seeded + "/versions/1/rules").web()
                .cookie(session).json(Fixtures.lendingV1().toString()).send().body(), "$.rulesetId");

        HttpResponse<String> opened = open(draft, 1);

        assertThat(opened.statusCode()).isEqualTo(409);
        assertThat(contract.violations("post", SESSIONS, 409, opened.body())).isEmpty();
        assertThat((String) JsonPath.read(opened.body(), "$.code")).isEqualTo("VERSION_STATUS_CONFLICT");
    }

    // Document 2: the body names a rule set and a version number. Expected: 400 without either
    @Test
    void aBodyWithoutRuleSetOrVersionMatchesTheDocumented400() {
        HttpResponse<String> noVersion = api().post(SESSIONS).web().cookie(session)
                .json("{\"rulesetId\":\"" + seeded + "\"}").send();

        assertThat(noVersion.statusCode()).isEqualTo(400);
        assertThat(contract.violations("post", SESSIONS, 400, noVersion.body())).isEmpty();
    }

    // Document 5, Authorization (sandbox): another sandbox's session reads as absent. Expected: 404 before any stream
    @Test
    void anotherSandboxsSessionMatchesTheDocumented404() {
        String chat = JsonPath.read(open(seeded, 1).body(), "$.id");
        String stranger = api().login("198.51.100.7");

        HttpResponse<String> asked = api().post(MESSAGES.replace("{id}", chat)).web().cookie(stranger)
                .json("{\"question\":\"שאלה\"}").send();

        assertThat(asked.statusCode()).isEqualTo(404);
        assertThat(contract.violations("post", MESSAGES, 404, asked.body())).isEmpty();
    }

    // Document 5, Input limits: the question is limited like a chat message. Expected: 400 for a blank question
    @Test
    void aBlankQuestionMatchesTheDocumented400() {
        String chat = JsonPath.read(open(seeded, 1).body(), "$.id");

        HttpResponse<String> asked = api().post(MESSAGES.replace("{id}", chat)).web().cookie(session)
                .json("{\"question\":\"  \"}").send();

        assertThat(asked.statusCode()).isEqualTo(400);
        assertThat(contract.violations("post", MESSAGES, 400, asked.body())).isEmpty();
    }

    private HttpResponse<String> open(String rulesetId, int versionNo) {
        return api().post(SESSIONS).web().cookie(session)
                .json("{\"rulesetId\":\"" + rulesetId + "\",\"versionNo\":" + versionNo + "}").send();
    }
}
