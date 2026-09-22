package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.RecordedModel;
import com.liorshaya.policypilot.support.Requirement;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code POST /decisions/{id}/explain} as the web app calls it (Document 2, API Surface; Document 4, Prompt 3; Work
 * Plan day 10). Case 17 is decided through the API against the seeded version, so the trace is the engine's; the model
 * is the recorded gateway, scripted per test.
 */
@Requirement("FR-11")
@Import(RecordedModel.class)
@Isolated
class ExplainControllerIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String EXPLAIN = "/api/v1/decisions/{id}/explain";
    /**
     * An officer's explanation of case 17: R-330 with paragraph 7, which the trace supports, and R-900 with paragraph
     * 9, which was skipped (fixtures/policies/consumer-lending/sample-decision.json).
     */
    private static final String ANSWER = """
            {"summary": "הבקשה הופנתה לבדיקה ידנית לפי R-330: אירוע אשראי אחד ב-24 החודשים האחרונים ואין ערב.",
             "factors": [
               {"ruleId": "R-330", "paragraph": 7, "statement": "אירוע אשראי אחד ב-24 החודשים האחרונים מחייב ערב."},
               {"ruleId": "R-900", "paragraph": 9, "statement": "בקשה העומדת בכל התנאים מאושרת."}],
             "conditions": [], "notApplied": [], "language": "he"}
            """;

    @Autowired
    private RecordedGateway model;

    private String session;
    private OpenApiContract contract;

    @BeforeEach
    void logIn() {
        model.reset();
        session = api().login();
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
    }

    /** Case 17 decided in this session's sandbox; the id of its stored decision. */
    private String caseSeventeen() {
        List<String> ids = JsonPath.read(api().get("/api/v1/rulesets").cookie(session).send().body(),
                "$.rulesets[?(@.protected == true)].id");
        ObjectNode body = JSON.createObjectNode();
        body.set("case", Fixtures.json("policies/consumer-lending/cases-200.json").required("cases").valueStream()
                .filter(fixture -> fixture.path("id").asInt() == 17).findFirst().orElseThrow().required("input"));
        HttpResponse<String> decided = api().post("/api/v1/rulesets/" + ids.getFirst() + "/versions/1/decide").web()
                .cookie(session).json(body.toString()).send();
        assertThat(decided.statusCode()).isEqualTo(200);
        return JsonPath.read(decided.body(), "$.id");
    }

    private HttpResponse<String> explain(String decisionId, String body) {
        return api().post("/api/v1/decisions/" + decisionId + "/explain").web().cookie(session).json(body).send();
    }

    // Work Plan day 10, Done when: "Explain on case 17 cites R-330 and its paragraph"; Document 4: a skipped rule is
    // filtered. Expected: the documented 200, R-330 with paragraph 7, and R-900 (skipped) gone
    @Test
    void explainingCaseSeventeenMatchesTheDocumented200AndCitesOnlyWhatFired() {
        String decision = caseSeventeen();
        model.willAnswer(ANSWER);

        HttpResponse<String> response = explain(decision, "{}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", EXPLAIN, 200, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.decisionId")).isEqualTo(decision);
        assertThat((String) JsonPath.read(response.body(), "$.audience")).isEqualTo("officer");
        assertThat((String) JsonPath.read(response.body(), "$.language")).isEqualTo("he");
        assertThat((List<String>) JsonPath.read(response.body(), "$.factors[*].ruleId")).containsExactly("R-330");
        assertThat((Integer) JsonPath.read(response.body(), "$.factors[0].paragraph")).isEqualTo(7);
    }

    // Document 4, Prompt 3: "The rule set itself is not sent" and the decision object carries no row id, so the cache
    // is shared across sandboxes. Expected: the stored decision's id nowhere in the prompt; the applicant audience in
    // it
    @Test
    void theModelSeesTheTraceWithoutTheDecisionsIdAndTheAudienceAsked() {
        String decision = caseSeventeen();
        model.willAnswer(ANSWER);

        explain(decision, "{\"audience\": \"applicant\"}");

        String prompt = model.lastUserPrompt();
        assertThat(prompt).doesNotContain(decision).contains("audience=\"applicant\"").contains("\"R-330\"");
    }

    // Document 2, explain row: "404 for a decision of another sandbox". Expected: 404, and no model is asked
    @Test
    void anotherSandboxesDecisionMatchesTheDocumented404() {
        String decision = caseSeventeen();
        session = api().login();

        HttpResponse<String> response = explain(decision, "{}");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(contract.violations("post", EXPLAIN, 404, response.body())).isEmpty();
        assertThat(model.asked()).isEmpty();
    }

    // Document 2, explain row: the audience is officer or applicant. Expected: 400 at /audience
    @Test
    void anAudienceThatIsNotOneOfTheTwoMatchesTheDocumented400() {
        String decision = caseSeventeen();

        HttpResponse<String> response = explain(decision, "{\"audience\": \"judge\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(contract.violations("post", EXPLAIN, 400, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/audience");
    }

    // Document 2, explain row: the provider failed. Expected: 503 PROVIDER_UNAVAILABLE
    @Test
    void aProviderThatIsDownMatchesTheDocumented503() {
        String decision = caseSeventeen();
        model.willFail(new LlmUnavailableException(LlmUnavailableException.Reason.TIMEOUT, "the provider timed out"));

        HttpResponse<String> response = explain(decision, "{}");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(contract.violations("post", EXPLAIN, 503, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("PROVIDER_UNAVAILABLE");
    }

    // Document 2, explain row: an answer that is not an explanation reaches nobody. Expected: 503, nothing of it shown
    @Test
    void anAnswerThatIsNotAnExplanationIsNeverShown() {
        String decision = caseSeventeen();
        model.willAnswer("R-330 decided it, trust me.");

        HttpResponse<String> response = explain(decision, "{}");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).doesNotContain("trust me");
    }
}
