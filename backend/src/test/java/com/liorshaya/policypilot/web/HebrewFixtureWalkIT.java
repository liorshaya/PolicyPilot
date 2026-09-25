package com.liorshaya.policypilot.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.FakeEmbeddingGateway;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.RecordedGateway.Streamed;
import com.liorshaya.policypilot.support.RecordedModel;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.Seeded;
import com.liorshaya.policypilot.support.ServerSentEvents;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The Hebrew fixtures through every route the web app calls, in the order the demo calls them (NFR-5; Work Plan day 16,
 * the Hebrew and RTL pass: "Hebrew fixtures through every endpoint once more ... the Hebrew fixture walk as one
 * integration test"; Document 6's matrix, NFR-5: "Hebrew fixtures through every endpoint"). Each step sends the
 * committed Hebrew and checks that it comes back exactly as it went in: the policy and its paragraphs, the rules the
 * model wrote from them, an analyst's edit and note, a decision's reason and labels and its CSV export, an explanation,
 * a what-if, the retrieved paragraphs, a streamed answer, the fixed not-covered sentence, the change request, its
 * approval's note, the diff and the audit log with its export. The model is scripted, so what it wrote is known; the
 * API, the engine, the validator and the database are real.
 */
@Requirement("NFR-5")
@Import(RecordedModel.class)
@Isolated
class HebrewFixtureWalkIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String BYTE_ORDER_MARK = Character.toString(0xFEFF);

    private static final String TITLE = "מדיניות אשראי צרכני - מעבר בעברית";
    private static final String EDITED_LABEL = "דחייה: גיל נמוך מ-21 שנה";
    private static final String ACKNOWLEDGED = "נבדק מול ועדת האשראי: גמלאים מוחרגים מ-R-110";
    private static final String APPROVED = "אושר בוועדת האשראי";
    /** Document 4, Threshold: the Hebrew sentence of a question the documents do not cover. */
    private static final String NOT_COVERED_HE =
            "המסמכים אינם עוסקים בשאלה הזו; אפשר לשאול על כלל, על סעיף או על מספר בקשה.";
    private static final String ANSWER = "תקופת ההחזר המקסימלית היא 84 חודשים.[[p:2]]";
    private static final String REVIEW = """
            {"findings": [
              {"kind": "ambiguity", "severity": "warning", "ruleIds": ["R-170"], "paragraphIndexes": [4],
               "message": "הכנסה יציבה אינה מוגדרת", "suggestion": "להוסיף סימון לבדיקה ידנית", "confidence": 0.8},
              {"kind": "conflict", "severity": "error", "ruleIds": ["R-110"], "paragraphIndexes": [1, 8],
               "message": "סעיף 1 מגביל את הגיל ל-70 וסעיף 8 מתיר גמלאים עד 75",
               "suggestion": "להחריג גמלאים מ-R-110", "confidence": 0.9}],
             "coverage": {"4": ["R-170"]}}
            """;
    private static final String EXPLANATION = """
            {"summary": "הבקשה הופנתה לבדיקה ידנית לפי R-330: אירוע אשראי אחד ב-24 החודשים האחרונים ואין ערב.",
             "factors": [
               {"ruleId": "R-330", "paragraph": 7, "statement": "אירוע אשראי אחד ב-24 החודשים האחרונים מחייב ערב."}],
             "conditions": [], "notApplied": [], "language": "he"}
            """;

    @Autowired
    private RecordedGateway model;

    @Autowired
    private FakeEmbeddingGateway embeddings;

    @Autowired
    private JdbcClient jdbc;

    private String session;

    @BeforeEach
    void signIn() {
        model.reset();
        session = api().login();
    }

    @Test
    void theHebrewLendingFixturesComeBackUnchangedThroughEveryRoute() {
        // POST /policies with the text pasted, then GET /policies and GET /policies/{id}: the paragraphs split as they
        // were written (Document 2: "returns 201 with the paragraph split")
        HttpResponse<String> pasted = api().post("/api/v1/policies").web().cookie(session).json(JSON.createObjectNode()
                .put("title", TITLE).put("language", "he").put("text", Fixtures.lendingPolicyText()).toString()).send();
        assertThat(pasted.statusCode()).isEqualTo(201);
        String policyId = JsonPath.read(pasted.body(), "$.id");
        assertThat(paragraphs(api().get("/api/v1/policies/" + policyId).cookie(session).send().body()))
                .isEqualTo(Fixtures.lendingParagraphs());
        assertThat(JsonPath.<List<String>>read(api().get("/api/v1/policies").cookie(session).send().body(),
                "$.policies[*].title")).contains(TITLE);

        // POST /policies with the same text as a Markdown file and as a text file
        for (String file : List.of("lending.md", "lending.txt")) {
            HttpResponse<String> uploaded = api().post("/api/v1/policies").web().cookie(session)
                    .multipart(TITLE, "he", file, Fixtures.lendingPolicyText().getBytes(StandardCharsets.UTF_8)).send();
            assertThat(uploaded.statusCode()).as(file).isEqualTo(201);
            assertThat(paragraphs(uploaded.body())).as(file).isEqualTo(Fixtures.lendingParagraphs());
        }

        // POST /policies/{id}/rulesets: the draft carries the Hebrew labels and quotes the model wrote, and the review
        // its Hebrew messages
        ObjectNode written = modelShaped();
        model.willAnswer(written.toString());
        model.willAnswer(REVIEW);
        JsonNode draft = ServerSentEvents.parse(api().post("/api/v1/policies/" + policyId + "/rulesets").web()
                .cookie(session).json("{}").send().body()).first("draft");
        assertThat(labels(draft.required("ruleSet"))).isEqualTo(labels(written));
        assertThat(quotes(draft.required("ruleSet"))).isEqualTo(quotes(written));
        assertThat(draft.required("review").required("findings").valueStream()
                .map(finding -> finding.required("message").asString()).toList())
                .containsExactly("הכנסה יציבה אינה מוגדרת", "סעיף 1 מגביל את הגיל ל-70 וסעיף 8 מתיר גמלאים עד 75");
        String rulesetId = draft.required("rulesetId").asString();
        String draftPath = "/api/v1/rulesets/" + rulesetId + "/versions/1";

        // PUT .../rules with an analyst's Hebrew label, POST .../review again, then an error acknowledged with a note
        ObjectNode edited = (ObjectNode) draft.required("ruleSet").deepCopy();
        ruleOf(edited, "R-100").put("label", EDITED_LABEL);
        assertThat(api().method("PUT", draftPath + "/rules").web().cookie(session).json(edited.toString()).send()
                .statusCode()).isEqualTo(200);
        assertThat(ruleOf(JSON.readTree(api().get(draftPath).cookie(session).send().body()).required("ruleSet"),
                "R-100").required("label").asString()).isEqualTo(EDITED_LABEL);
        model.willAnswer(REVIEW);
        assertThat(api().post(draftPath + "/review").web().cookie(session).send().statusCode()).isEqualTo(200);
        HttpResponse<String> acknowledged = api().post(draftPath + "/findings/F-2/acknowledge").web().cookie(session)
                .json(JSON.createObjectNode().put("note", ACKNOWLEDGED).toString()).send();
        assertThat(acknowledged.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(acknowledged.body(),
                "$.review.findings[?(@.id == 'F-2')].acknowledgement.note")).containsExactly(ACKNOWLEDGED);

        // POST .../publish, then GET /audit: the publish entry keeps the note with the review it acknowledged
        HttpResponse<String> published = api().post(draftPath + "/publish").web().cookie(session).send();
        assertThat(published.statusCode()).isEqualTo(200);
        String draftVersionId = JsonPath.read(published.body(), "$.versionId");
        assertThat(JsonPath.<List<String>>read(audit(draftVersionId),
                "$.entries[?(@.action == 'PUBLISH')].details.review.findings[?(@.id == 'F-2')].acknowledgement.note"))
                .containsExactly(ACKNOWLEDGED);

        // On the seeded version, as the demo runs it: case 17 decided, read back, exported as CSV, explained and
        // simulated with a guarantor; its reason and labels are the reference's (sample-decision.json)
        String seeded = seededRulesetId();
        String seededPath = "/api/v1/rulesets/" + seeded + "/versions/1";
        JsonNode sample = Fixtures.json("policies/consumer-lending/sample-decision.json");
        HttpResponse<String> decided = api().post(seededPath + "/decide").web().cookie(session)
                .json(JSON.createObjectNode().set("case", case17()).toString()).send();
        assertThat(decided.statusCode()).isEqualTo(200);
        JsonNode decision = JSON.readTree(decided.body());
        assertThat(decision.required("reason").asString()).isEqualTo(sample.required("reason").asString());
        assertThat(traceLabels(decision)).isEqualTo(traceLabels(sample));
        String decisionId = decision.required("id").asString();
        assertThat(traceLabels(JSON.readTree(api().get("/api/v1/decisions/" + decisionId).cookie(session).send()
                .body()))).isEqualTo(traceLabels(sample));
        String csv = api().get("/api/v1/decisions/" + decisionId + "/export").cookie(session)
                .header("Accept", "text/csv").send().body();
        assertThat(csv).startsWith(BYTE_ORDER_MARK).contains(traceLabels(sample).getFirst());
        model.willAnswer(EXPLANATION);
        HttpResponse<String> explained = api().post("/api/v1/decisions/" + decisionId + "/explain").web()
                .cookie(session).json("{}").send();
        assertThat(explained.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(explained.body()).required("summary").asString())
                .isEqualTo(JSON.readTree(EXPLANATION).required("summary").asString());
        JsonNode simulated = JSON.readTree(api().post(seededPath + "/simulate").web().cookie(session)
                .json("{\"decisionId\": \"" + decisionId + "\", \"overrides\": {\"has_guarantor\": true}}").send()
                .body());
        assertThat(simulated.required("outcome").asString()).isEqualTo("approve");
        ObjectNode lending = Fixtures.lendingV1();
        assertThat(traceLabels(simulated)).isEqualTo(simulated.required("trace").valueStream()
                .map(step -> ruleOf(lending, step.required("ruleId").asString()).required("label").asString())
                .toList());

        // POST .../retrieval and the chat: every retrieved paragraph is one of the policy's, a streamed answer is the
        // one the model wrote, and a question the documents do not cover gets Document 4's Hebrew sentence
        awaitSeededVersionReady();
        JsonNode retrieved = JSON.readTree(api().post(seededPath + "/retrieval").web().cookie(session)
                .json(JSON.createObjectNode().put("question", "מהי תקופת ההחזר המקסימלית להלוואה?").toString())
                .send().body());
        assertThat(retrieved.required("covered").asBoolean()).isTrue();
        assertThat(retrieved.required("chunks").valueStream().filter(chunk -> "PARAGRAPH".equals(
                chunk.required("kind").asString())).map(chunk -> chunk.required("text").asString()).toList())
                .isNotEmpty().allSatisfy(text -> assertThat(Fixtures.lendingParagraphs()).contains(text));
        String offCorpus = "האם יש הנחה לחיילים משוחררים? " + UUID.randomUUID();
        embeddings.register(offCorpus, embeddings.axis(9));
        assertThat(JSON.readTree(api().post(seededPath + "/retrieval").web().cookie(session)
                .json(JSON.createObjectNode().put("question", offCorpus).toString()).send().body())
                .required("notCovered").asString()).isEqualTo(NOT_COVERED_HE);
        HttpResponse<String> chat = api().post("/api/v1/chat/sessions").web().cookie(session)
                .json("{\"rulesetId\": \"" + seeded + "\", \"versionNo\": 1}").send();
        assertThat(JsonPath.<String>read(chat.body(), "$.language")).isEqualTo("he");
        String messages = "/api/v1/chat/sessions/" + JsonPath.read(chat.body(), "$.id") + "/messages";
        model.willStream(Streamed.text(ANSWER));
        String answered = api().post(messages).web().cookie(session).json(JSON.createObjectNode()
                .put("question", "מה אורך תקופת ההחזר הארוכה ביותר? " + UUID.randomUUID()).toString()).send().body();
        assertThat(tokens(answered)).isEqualTo(ANSWER);
        assertThat(tokens(api().post(messages).web().cookie(session)
                .json(JSON.createObjectNode().put("question", offCorpus).toString()).send().body()))
                .isEqualTo(NOT_COVERED_HE);

        // POST .../changes with CR-1, its approval with a Hebrew note, then the diff, the audit log and its export
        model.willAnswer(ChangeRequests.scriptedPatches().toString());
        JsonNode proposal = ServerSentEvents.parse(api().post(seededPath + "/changes").web().cookie(session)
                .json(JSON.createObjectNode().put("text", ChangeRequests.scripted()).toString()).send().body())
                .first("proposal");
        assertThat(proposal.required("summary").asString()).isEqualTo(ChangeRequests.SCRIPTED_SUMMARY);
        HttpResponse<String> approved = api().post("/api/v1/changes/" + proposal.required("id").asString()
                + "/approve").web().cookie(session).json(JSON.createObjectNode().put("note", APPROVED).toString())
                .send();
        assertThat(approved.statusCode()).isEqualTo(200);
        String copyId = JsonPath.read(approved.body(), "$.result.rulesetId");
        String versionTwo = JsonPath.read(approved.body(), "$.result.versionId");
        String entries = audit(versionTwo);
        String approval = "$.entries[?(@.action == 'CHANGE_APPROVED')].details";
        assertThat(JsonPath.<List<String>>read(entries, approval + ".requestText"))
                .containsExactly(ChangeRequests.scripted());
        assertThat(JsonPath.<List<String>>read(entries, approval + ".note")).containsExactly(APPROVED);
        String replacement = replacementLabel("R-170");
        assertThat(JsonPath.<List<String>>read(api().get("/api/v1/rulesets/" + copyId + "/versions/1/diff/2")
                .cookie(session).send().body(), "$.rules.modified[?(@.id == 'R-170')].to.label"))
                .containsExactly(replacement);
        assertThat(api().get("/api/v1/audit/export?versionId=" + versionTwo).cookie(session)
                .header("Accept", "text/csv").send().body()).startsWith(BYTE_ORDER_MARK).contains(APPROVED);
    }

    /** The committed lending rule set as a model writes it: the rules an analyst added are not the model's. */
    private static ObjectNode modelShaped() {
        ObjectNode document = Fixtures.lendingV1();
        ArrayNode rules = JSON.createArrayNode();
        document.get("rules").valueStream()
                .filter(rule -> !"analyst".equals(rule.path("provenance").path("kind").asString("")))
                .forEach(rules::add);
        document.set("rules", rules);
        return document;
    }

    private static ObjectNode ruleOf(JsonNode ruleSet, String id) {
        return (ObjectNode) ruleSet.required("rules").valueStream()
                .filter(rule -> id.equals(rule.required("id").asString())).findFirst().orElseThrow();
    }

    private static List<String> labels(JsonNode ruleSet) {
        return ruleSet.required("rules").valueStream().map(rule -> rule.required("label").asString()).toList();
    }

    private static List<String> quotes(JsonNode ruleSet) {
        return ruleSet.required("rules").valueStream()
                .map(rule -> rule.required("provenance").path("quote").asString("")).toList();
    }

    private static List<String> traceLabels(JsonNode decision) {
        return decision.required("trace").valueStream().map(step -> step.required("label").asString()).toList();
    }

    private static List<String> paragraphs(String policy) {
        return JsonPath.read(policy, "$.versions[0].paragraphs[*].text");
    }

    /** The label CR-1 gives a rule it replaces (fixtures/policies/consumer-lending/change-request-1.json). */
    private static String replacementLabel(String ruleId) {
        return Fixtures.json("policies/consumer-lending/change-request-1.json").required("expected")
                .required("patches").valueStream().filter(patch -> ruleId.equals(patch.path("ruleId").asString("")))
                .findFirst().orElseThrow().required("rule").required("label").asString();
    }

    private static JsonNode case17() {
        return Fixtures.json("policies/consumer-lending/cases-200.json").required("cases").valueStream()
                .filter(fixture -> fixture.required("id").asInt() == 17).findFirst().orElseThrow().required("input");
    }

    private String audit(String versionId) {
        return api().get("/api/v1/audit?versionId=" + versionId).cookie(session).send().body();
    }

    private String seededRulesetId() {
        List<String> ids = JsonPath.read(api().get("/api/v1/rulesets").cookie(session).send().body(),
                Seeded.LENDING_RULESET_ID);
        return ids.getFirst();
    }

    /** The tokens of a chat stream joined, the answer as the web app shows it before its markers become chips. */
    private static String tokens(String stream) {
        List<String> pieces = new ArrayList<>();
        ServerSentEvents.parse(stream).all("token").forEach(token -> pieces.add(token.required("text").asString()));
        return String.join("", pieces);
    }

    /** The seeded version is embedded after startup; retrieval, the chat and a change wait for it (Document 2). */
    private void awaitSeededVersionReady() {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        String sql = "select v.embedding_status from ruleset_version v join ruleset r on r.id = v.ruleset_id "
                + "where r.protected and r.domain = :domain and v.version_no = 1";
        while (!"READY".equals(jdbc.sql(sql).param("domain", Seeded.LENDING).query(String.class).single())) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("the seeded version was not embedded within 15 s");
            }
            Thread.onSpinWait();
        }
    }
}
