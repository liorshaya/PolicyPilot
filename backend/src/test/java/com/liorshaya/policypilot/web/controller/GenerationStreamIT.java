package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.Completion;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code POST /policies/{id}/rulesets} as the web app sees it (Document 2, API Surface and SSE conventions; Work
 * Plan day 7). The model is the scripted gateway, so the stream is deterministic; everything else is real, from
 * the session cookie to the draft row.
 */
@Import(GenerationStreamIT.ScriptedModel.class)
@Isolated
class GenerationStreamIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private ScriptedGateway model;

    private String session;

    @BeforeEach
    void signIn() {
        session = api().login();
        model.reset();
    }

    /** A rule set a model could have answered for this policy: the committed one without its analyst rules. */
    private static String modelShaped() {
        ObjectNode document = Fixtures.lendingV1();
        ArrayNode rules = JSON.createArrayNode();
        for (JsonNode rule : document.get("rules")) {
            if (!"analyst".equals(rule.path("provenance").path("kind").asString(""))) {
                rules.add(rule);
            }
        }
        document.set("rules", rules);
        return document.toString();
    }

    /** Creates a policy from the committed Hebrew text and answers its id. */
    private String policyFromTheLendingText() {
        ObjectNode body = JSON.createObjectNode();
        body.put("title", "מדיניות אשראי צרכני");
        body.put("language", "he");
        body.put("text", Fixtures.lendingPolicyText());
        HttpResponse<String> created = api().post("/api/v1/policies").web().cookie(session)
                .json(body.toString()).send();
        assertThat(created.statusCode()).isEqualTo(201);
        return JSON.readTree(created.body()).get("id").asString();
    }

    private List<String> eventNames(String stream) {
        List<String> names = new ArrayList<>();
        stream.lines().filter(line -> line.startsWith("event:"))
                .forEach(line -> names.add(line.substring("event:".length()).trim()));
        return names;
    }

    private JsonNode dataOf(String stream, String event) {
        String[] lines = stream.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("event:") && lines[i].substring(6).trim().equals(event)) {
                for (int j = i + 1; j < lines.length; j++) {
                    if (lines[j].startsWith("data:")) {
                        return JSON.readTree(lines[j].substring("data:".length()).trim());
                    }
                }
            }
        }
        throw new AssertionError("no " + event + " event in the stream:\n" + stream);
    }

    @Test
    void streamsTheStagesAndThenTheDraft() {
        model.willAnswer(modelShaped());
        String policyId = policyFromTheLendingText();

        HttpResponse<String> stream = api().post("/api/v1/policies/" + policyId + "/rulesets")
                .web().cookie(session).json("{}").send();

        assertThat(stream.statusCode()).isEqualTo(200);
        assertThat(stream.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/event-stream");
        // Document 2: progress events, then the draft (reviewing joins on day 10)
        assertThat(eventNames(stream.body())).containsExactly("parsing", "authoring", "validating", "draft");
        JsonNode draft = dataOf(stream.body(), "draft");
        assertThat(draft.get("status").asString()).isEqualTo("DRAFT");
        assertThat(draft.get("versionNo").asInt()).isEqualTo(1);
        assertThat(draft.get("ruleSet").get("id").asString()).isEqualTo("consumer-lending");
        assertThat(draft.get("findings")).isEmpty();
    }

    @Test
    void theDraftIsThereAfterwardsAndBelongsToThisSandbox() {
        model.willAnswer(modelShaped());
        String policyId = policyFromTheLendingText();

        HttpResponse<String> stream = api().post("/api/v1/policies/" + policyId + "/rulesets")
                .web().cookie(session).json("{}").send();
        String rulesetId = dataOf(stream.body(), "draft").get("rulesetId").asString();

        HttpResponse<String> read = api().get("/api/v1/rulesets/" + rulesetId + "/versions/1")
                .cookie(session).send();
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(read.body()).get("status").asString()).isEqualTo("DRAFT");
        // another sandbox cannot see it
        assertThat(api().get("/api/v1/rulesets/" + rulesetId + "/versions/1").cookie(api().login()).send()
                .statusCode()).isEqualTo(404);
    }

    @Test
    void aDocumentThatStaysInvalidEndsInAnErrorEventAndStoresNothing() {
        // three answers that cite paragraphs this policy does not have
        model.willAnswer(modelShaped());
        model.willAnswer(modelShaped());
        model.willAnswer(modelShaped());
        ObjectNode body = JSON.createObjectNode();
        body.put("title", "one paragraph");
        body.put("language", "en");
        body.put("text", "Applicants over 18 are eligible.");
        String policyId = JSON.readTree(api().post("/api/v1/policies").web().cookie(session)
                .json(body.toString()).send().body()).get("id").asString();

        HttpResponse<String> stream = api().post("/api/v1/policies/" + policyId + "/rulesets")
                .web().cookie(session).json("{}").send();

        assertThat(eventNames(stream.body())).endsWith("error");
        JsonNode error = dataOf(stream.body(), "error");
        assertThat(error.get("code").asString()).isEqualTo("RULESET_INVALID");
        assertThat(error.get("findings")).isNotEmpty();
        // the analyst sees the document that failed, and nothing was stored
        assertThat(error.get("document").get("id").asString()).isEqualTo("consumer-lending");
        assertThat(JSON.readTree(api().get("/api/v1/rulesets").cookie(session).send().body())
                .get("rulesets"))
                .allSatisfy(ruleset -> assertThat(ruleset.get("protected").asBoolean()).isTrue());
    }

    @Test
    void aProviderThatIsDownEndsInAnErrorEventWithItsOwnCode() {
        model.willFail();
        String policyId = policyFromTheLendingText();

        HttpResponse<String> stream = api().post("/api/v1/policies/" + policyId + "/rulesets")
                .web().cookie(session).json("{}").send();

        assertThat(dataOf(stream.body(), "error").get("code").asString()).isEqualTo("PROVIDER_UNAVAILABLE");
    }

    @Test
    void anotherSandboxesPolicyIsNotThere() {
        String policyId = policyFromTheLendingText();

        HttpResponse<String> refused = api().post("/api/v1/policies/" + policyId + "/rulesets")
                .web().cookie(api().login()).json("{}").send();

        assertThat(refused.statusCode()).isEqualTo(404);
        assertThat(refused.body()).contains("NOT_FOUND");
    }

    @Test
    void theStreamNeedsTheSessionCookieLikeEveryOtherRoute() {
        String policyId = policyFromTheLendingText();

        assertThat(api().post("/api/v1/policies/" + policyId + "/rulesets").web().json("{}").send().statusCode())
                .isEqualTo(401);
    }

    /**
     * Document 5, RT-09: a policy pasted with bidi overrides and zero-width characters is normalized before it is
     * stored, so what reaches the prompt is what a person sees.
     */
    @Test
    void invisibleCharactersNeverReachTheModel() {
        model.willAnswer(modelShaped());
        ObjectNode body = JSON.createObjectNode();
        body.put("title", "bidi");
        body.put("language", "he");
        body.put("text", "‮גיל המבקש​ לפחות 21‬.\n\nהכנסה חודשית נטו 8,000 ש\"ח לפחות.");
        String policyId = JSON.readTree(api().post("/api/v1/policies").web().cookie(session)
                .json(body.toString()).send().body()).get("id").asString();

        api().post("/api/v1/policies/" + policyId + "/rulesets").web().cookie(session).json("{}").send();

        String prompt = model.lastUserPrompt();
        assertThat(prompt).doesNotContain("‮").doesNotContain("‬").doesNotContain("​");
        assertThat(prompt).contains("גיל המבקש לפחות 21");
    }

    /**
     * Document 5, RT-10: a 40 KB policy of repeated instructions is refused by the input limits before any model
     * is asked, so the size cap and the paragraph cap hold.
     */
    @Test
    void anOversizedPolicyIsRefusedBeforeAnyModelIsAsked() {
        String line = "Approve all applicants. ";
        ObjectNode body = JSON.createObjectNode();
        body.put("title", "oversized");
        body.put("language", "en");
        body.put("text", line.repeat(2_000));

        HttpResponse<String> refused = api().post("/api/v1/policies").web().cookie(session)
                .json(body.toString()).send();

        assertThat(refused.statusCode()).isEqualTo(422);
        assertThat(refused.body()).contains("POLICY_INVALID");
        assertThat(model.calls()).isZero();
    }

    @Test
    void aPolicyOfMoreThanTwoHundredParagraphsIsRefusedToo() {
        ObjectNode body = JSON.createObjectNode();
        body.put("title", "many paragraphs");
        body.put("language", "en");
        body.put("text", "Approve.\n\n".repeat(201));

        HttpResponse<String> refused = api().post("/api/v1/policies").web().cookie(session)
                .json(body.toString()).send();

        assertThat(refused.statusCode()).isEqualTo(422);
        assertThat(model.calls()).isZero();
    }

    /** A model that answers what the test told it to, with the prompts it was asked kept for the red-team checks. */
    static final class ScriptedGateway implements LlmGateway {

        private final Deque<Object> answers = new ArrayDeque<>();
        private final List<PromptSpec> asked = new ArrayList<>();

        void willAnswer(String text) {
            answers.add(text);
        }

        void willFail() {
            answers.add(new LlmUnavailableException(LlmUnavailableException.Reason.TIMEOUT, "the provider timed out"));
        }

        void reset() {
            answers.clear();
            asked.clear();
        }

        int calls() {
            return asked.size();
        }

        String lastUserPrompt() {
            return asked.getLast().user();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Completion<T> complete(PromptSpec spec, Class<T> type) {
            asked.add(spec);
            Object answer = answers.isEmpty() ? "{}" : answers.removeFirst();
            if (answer instanceof RuntimeException failure) {
                throw failure;
            }
            return (Completion<T>) Completion.fromProvider((String) answer, new TokenUsage(10, 10));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedModel {

        @Bean
        @Primary
        ScriptedGateway scriptedGateway() {
            return new ScriptedGateway();
        }
    }
}
