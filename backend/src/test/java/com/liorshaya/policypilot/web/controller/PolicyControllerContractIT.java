package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.PdfSamples;
import com.liorshaya.policypilot.support.Requirement;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The contract of {@code POST /api/v1/policies} and {@code GET /api/v1/policies/{id}} (Document 2, API Surface;
 * Document 6, Contract level and the FR-1 row: text, .md, .pdf). Responses are validated against the OpenAPI
 * document the API serves; the demo policy's expected paragraphs come from Document 3's table.
 */
@Requirement("FR-1")
class PolicyControllerContractIT extends ApiIntegrationTest {

    private static final String POLICIES = "/api/v1/policies";
    private static final String FIRST_PARAGRAPH = "הלוואה אישית תינתן ליחיד שגילו 21 עד 70 בעת הגשת הבקשה.";

    private String session;
    private OpenApiContract contract;

    @BeforeEach
    void logIn() {
        session = api().login();
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
    }

    // Document 2, GET /policies. Expected: the served OpenAPI document, and the seeded policy in the list
    @Test
    void listPoliciesMatchesTheDocumented200() throws IOException {
        createFromText(lendingText());

        HttpResponse<String> response = api().get(POLICIES).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", POLICIES, 200, response.body())).isEmpty();
        assertThat((List<?>) JsonPath.read(response.body(), "$.policies[?(@.protected == true)]")).hasSize(1);
        assertThat((Integer) JsonPath.read(response.body(), "$.policies[0].paragraphs")).isEqualTo(9);
        assertThat((String) JsonPath.read(response.body(), "$.policies[0].language")).isEqualTo("he");
    }

    // Document 5, Authorization (sandbox): a policy of another sandbox is not in this list
    @Test
    void theListHoldsTheProtectedPoliciesAndTheSessionsOwn() throws IOException {
        String mine = JsonPath.read(createFromText(lendingText()).body(), "$.id");
        String theirs = JsonPath.read(
                api().post(POLICIES).web().cookie(api().login())
                        .json("{\"title\":\"Theirs\",\"language\":\"en\",\"text\":\"Applicants must be 21.\"}")
                        .send().body(),
                "$.id");

        List<String> ids = JsonPath.read(api().get(POLICIES).cookie(session).send().body(), "$.policies[*].id");

        assertThat(ids).contains(mine).doesNotContain(theirs);
    }

    @Test
    void postWithTextReturns201WithLocationAndTheParagraphSplit() throws IOException {
        HttpResponse<String> response = createFromText(lendingText());

        assertThat(response.statusCode()).isEqualTo(201);
        String id = JsonPath.read(response.body(), "$.id");
        assertThat(response.headers().firstValue("Location")).contains(POLICIES + "/" + id);
        assertThat((List<?>) JsonPath.read(response.body(), "$.versions[0].paragraphs")).hasSize(9);
        assertThat((String) JsonPath.read(response.body(), "$.versions[0].paragraphs[0].text")).isEqualTo(FIRST_PARAGRAPH);
        assertThat((Integer) JsonPath.read(response.body(), "$.versions[0].paragraphs[8].index")).isEqualTo(9);
        assertThat((Integer) JsonPath.read(response.body(), "$.versions[0].versionNo")).isEqualTo(1);
        assertThat((String) JsonPath.read(response.body(), "$.language")).isEqualTo("he");
        assertThat((Boolean) JsonPath.read(response.body(), "$.protected")).isFalse();
    }

    @Test
    void postWithAMarkdownFileReturns201() throws IOException {
        HttpResponse<String> response = api().post(POLICIES).web().cookie(session)
                .multipart("Lending", "he", "policy.md", lendingText().getBytes(StandardCharsets.UTF_8)).send();

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat((List<?>) JsonPath.read(response.body(), "$.versions[0].paragraphs")).hasSize(9);
    }

    @Test
    void postWithAPdfReturns201() {
        byte[] pdf = PdfSamples.text(List.of(List.of(
                "Applicants must be at least 21 years old.", "The loan amount is at most 150,000.")));

        HttpResponse<String> response = api().post(POLICIES).web().cookie(session)
                .multipart("Lending", "en", "policy.pdf", pdf).send();

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat((List<String>) JsonPath.read(response.body(), "$.versions[0].paragraphs[*].text"))
                .containsExactly("Applicants must be at least 21 years old.", "The loan amount is at most 150,000.");
    }

    @Test
    void getReturnsThePolicyWithItsVersionsAndParagraphs() throws IOException {
        String created = createFromText(lendingText()).body();
        String id = JsonPath.read(created, "$.id");

        HttpResponse<String> response = api().get(POLICIES + "/" + id).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonMapper.builder().build().readTree(response.body()))
                .isEqualTo(JsonMapper.builder().build().readTree(created));
    }

    @Test
    void everyResponseValidatesAgainstTheOpenApiDocument() throws IOException {
        HttpResponse<String> created = createFromText(lendingText());
        String id = JsonPath.read(created.body(), "$.id");
        HttpResponse<String> fetched = api().get(POLICIES + "/" + id).cookie(session).send();

        assertThat(contract.violations("POST", POLICIES, 201, created.body())).isEmpty();
        assertThat(contract.violations("GET", POLICIES + "/{id}", 200, fetched.body())).isEmpty();
    }

    @Test
    void theContractCheckRejectsAWrongShape() {
        assertThat(contract.violations("GET", POLICIES + "/{id}", 200, "{\"id\": 7, \"versions\": \"none\"}")).isNotEmpty();
    }

    @Test
    void missingTitleReturns422WithItsPointer() {
        HttpResponse<String> response = api().post(POLICIES).web().cookie(session)
                .json("{\"language\":\"he\",\"text\":\"one paragraph\"}").send();

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("POLICY_INVALID");
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/title");
    }

    @Test
    void anUnknownPropertyIsA400() {
        HttpResponse<String> response = api().post(POLICIES).web().cookie(session)
                .json("{\"title\":\"t\",\"language\":\"he\",\"text\":\"x\",\"sandboxId\":\"00000000-0000-4000-8000-000000000000\"}")
                .send();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("REQUEST_INVALID");
    }

    @Test
    void malformedIdReturns400BeforeAnyLookup() {
        HttpResponse<String> response = api().get(POLICIES + "/not-a-uuid").cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/id");
    }

    @Test
    void unknownIdReturns404() {
        HttpResponse<String> response = api().get(POLICIES + "/0f4c1c9e-0000-4000-8000-00000000abcd").cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void theDocumentDescribesBothRoutes() {
        assertThat(contract.documents("POST", POLICIES)).isTrue();
        assertThat(contract.documents("GET", POLICIES + "/{id}")).isTrue();
    }

    private HttpResponse<String> createFromText(String text) {
        String body = JsonMapper.builder().build().writeValueAsString(
                java.util.Map.of("title", "מדיניות אשראי צרכני", "language", "he", "text", text));
        return api().post(POLICIES).web().cookie(session).json(body).send();
    }

    private static String lendingText() throws IOException {
        return Files.readString(Fixtures.path("policies/consumer-lending/policy.he.md"));
    }
}
