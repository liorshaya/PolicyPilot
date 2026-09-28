package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.RecordedModel;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.Seeded;
import com.liorshaya.policypilot.support.ServerSentEvents;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A change request as the web app sees it (Document 2, {@code POST /rulesets/{id}/versions/{no}/changes}; Document 4,
 * Prompt 5; Work Plan day 12): the model is the recorded gateway, scripted per test; the embedding, the analysis, the
 * validators and the database are real. The embedding fake gives every text one vector, so every rule chunk is as
 * near as the next and the two seeds are the first rule ids, R-010 and R-020. R-020's condition tests
 * {@code monthly_income}, so the candidates are Document 4's closure over ruleset.v1.json from there: R-170 and R-410
 * read the income, and R-200 and R-320 read the {@code debt_to_income} R-020 derives.
 */
@Requirement("FR-17")
@Import(RecordedModel.class)
@Isolated
class ChangeStreamIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> CANDIDATES = List.of("R-010", "R-020", "R-170", "R-200", "R-320", "R-410");

    @Autowired
    private RecordedGateway model;

    @Autowired
    private JdbcClient jdbc;

    private String session;
    private String seeded;

    @BeforeEach
    void signInAndWaitForTheSeededCorpus() {
        model.reset();
        session = api().login();
        List<String> ids = JsonPath.read(api().get("/api/v1/rulesets").cookie(session).send().body(),
                Seeded.LENDING_RULESET_ID);
        seeded = ids.getFirst();
        awaitSeededVersionReady();
    }

    // Work Plan day 12, done when: the scripted request proposes patches to R-170 and R-410 with pending provenance.
    // Expected: Document 2's events in order, the candidates above, and the fixture's two patches, each pending with
    // the stored request's id
    @Test
    void theScriptedRequestProposesR170AndR410Pending() {
        model.willAnswer(ChangeRequests.scriptedPatches().toString());

        HttpResponse<String> response = submit(ChangeRequests.scripted());

        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/event-stream"));
        ServerSentEvents events = ServerSentEvents.parse(response.body());
        assertThat(events.names()).containsExactly("analyzing", "proposing", "validating", "regression", "proposal");
        assertThat(events.first("analyzing").required("rules").asInt())
                .isEqualTo(Fixtures.lendingV1().required("rules").size());
        assertThat(strings(events.first("proposing").required("candidates"))).isEqualTo(CANDIDATES);
        assertThat(strings(events.first("proposing").required("fields"))).containsExactly("monthly_income");
        JsonNode proposal = events.first("proposal");
        String id = proposal.required("id").asString();
        assertThat(proposal.required("status").asString()).isEqualTo("PROPOSED");
        assertThat(proposal.required("summary").asString()).isEqualTo(ChangeRequests.SCRIPTED_SUMMARY);
        JsonNode patches = proposal.required("patches");
        assertThat(patches.valueStream().map(patch -> patch.required("op").asString())).containsExactly("replace",
                "replace");
        assertThat(patches.valueStream().map(patch -> patch.required("ruleId").asString())).containsExactly("R-170",
                "R-410");
        assertThat(patches.valueStream().map(patch -> patch.required("rule").required("provenance")))
                .allSatisfy(provenance -> {
                    assertThat(provenance.required("kind").asString()).isEqualTo("pending");
                    assertThat(provenance.required("changeRequestId").asString()).isEqualTo(id);
                });
        assertThat(strings(proposal.required("untouched"))).containsExactly("R-020", "R-200", "R-320");
        assertThat(strings(proposal.required("candidates"))).isEqualTo(CANDIDATES);
        assertThat(proposal.required("diff").required("rules").required("modified").valueStream()
                .map(rule -> rule.required("id").asString())).containsExactly("R-170", "R-410");
        assertThat(proposal.required("regression").required("decisions").asInt()).isZero();
    }

    // Document 3, Regression report: "exactly 12 of the 200 cases flip" (change-request-1.json: 12), over the
    // decisions this sandbox made on the base version, the latest per case. Expected: the fixture set decided twice
    // and still 200 decided again, twelve flips to reject, and the report stored with the request
    @Test
    void theRegressionOfTheScriptedChangeFlipsExactlyTwelve() {
        for (int run = 0; run < 2; run++) {
            HttpResponse<String> decided = api().post("/api/v1/rulesets/" + seeded + "/versions/1/decide").web()
                    .cookie(session).json("{\"fixtureSet\":\"cases-200\"}").send();
            assertThat(decided.statusCode()).isEqualTo(200);
        }
        model.willAnswer(ChangeRequests.scriptedPatches().toString());

        JsonNode proposal = ServerSentEvents.parse(submit(ChangeRequests.scripted()).body()).first("proposal");

        JsonNode regression = proposal.required("regression");
        int expected = Fixtures.json("policies/consumer-lending/change-request-1.json").required("expected")
                .required("regression").required("flips").asInt();
        assertThat(regression.required("decisions").asInt()).isEqualTo(200);
        assertThat(regression.required("flips")).hasSize(expected);
        assertThat(regression.required("flips").valueStream().map(flip -> flip.required("after").asString()))
                .containsOnly("reject");
        JsonNode stored = JSON.readTree(jdbc.sql("select regression_json::text from change_request where id = :id")
                .param("id", UUID.fromString(proposal.required("id").asString())).query(String.class).single());
        assertThat(stored.required("flips")).hasSize(expected);
    }

    // Document 3, Regression report (2026-09-28, Register phase 4): the base outcomes, before, and the flags that
    // moved. Expected: the Python reference over cases-200, 113 approved, 27 referred and 60 rejected, and eleven
    // decisions whose flags moved, every one by R-410 and six by R-420
    @Test
    void theRegressionCountsTheBaseOutcomesAndTheFlagsThatMoved() {
        HttpResponse<String> decided = api().post("/api/v1/rulesets/" + seeded + "/versions/1/decide").web()
                .cookie(session).json("{\"fixtureSet\":\"cases-200\"}").send();
        assertThat(decided.statusCode()).isEqualTo(200);
        model.willAnswer(ChangeRequests.scriptedPatches().toString());

        JsonNode regression = ServerSentEvents.parse(submit(ChangeRequests.scripted()).body()).first("proposal")
                .required("regression");

        assertThat(regression.required("before")).isEqualTo(JSON.readTree("""
                {"approve": 113, "refer": 27, "reject": 60}"""));
        assertThat(regression.required("flagsMoved")).isEqualTo(JSON.readTree("""
                {"decisions": 11, "byRule": {"R-410": 11, "R-420": 6}}"""));
    }

    // Document 2, change_request and audit_entry: the row holds the patches as validated with the request's id in
    // every pending provenance and the rationale with the candidates; the audit entry names the request on its base
    // version. Expected: all of that, the actor being the sandbox (Document 5, Why no user accounts)
    @Test
    void aStoredProposalIsAProposedRowWithItsAuditEntry() {
        model.willAnswer(ChangeRequests.scriptedPatches().toString());

        UUID id = UUID.fromString(ServerSentEvents.parse(submit(ChangeRequests.scripted()).body()).first("proposal")
                .required("id").asString());

        UUID sandbox = sandboxOf();
        UUID baseVersion = seededVersionId();
        var row = jdbc.sql("""
                select sandbox_id, base_version_id, request_text, status, patches_json::text, rationale_json::text,
                       decided_at, actor from change_request where id = :id""").param("id", id).query().singleRow();
        assertThat(row.get("sandbox_id")).isEqualTo(sandbox);
        assertThat(row.get("base_version_id")).isEqualTo(baseVersion);
        assertThat(row.get("request_text")).isEqualTo(ChangeRequests.scripted());
        assertThat(row.get("status")).isEqualTo("PROPOSED");
        assertThat(row.get("decided_at")).isNull();
        assertThat(row.get("actor")).isEqualTo(sandbox.toString());
        JsonNode stored = JSON.readTree((String) row.get("patches_json"));
        assertThat(stored.valueStream().map(patch -> patch.required("rule").required("provenance")
                .required("changeRequestId").asString())).containsExactly(id.toString(), id.toString());
        JsonNode rationale = JSON.readTree((String) row.get("rationale_json"));
        assertThat(rationale.required("summary").asString()).isEqualTo(ChangeRequests.SCRIPTED_SUMMARY);
        assertThat(strings(rationale.required("untouched"))).containsExactly("R-020", "R-200", "R-320");
        assertThat(strings(rationale.required("candidates").required("seeds"))).containsExactly("R-010", "R-020");
        assertThat(strings(rationale.required("candidates").required("ruleIds"))).isEqualTo(CANDIDATES);
        var entry = jdbc.sql("""
                select action, actor, ruleset_version_id, details_json::text from audit_entry
                where change_request_id = :id""").param("id", id).query().singleRow();
        assertThat(entry.get("action")).isEqualTo("CHANGE_PROPOSED");
        assertThat(entry.get("actor")).isEqualTo(sandbox.toString());
        assertThat(entry.get("ruleset_version_id")).isEqualTo(baseVersion);
        JsonNode details = JSON.readTree((String) entry.get("details_json"));
        assertThat(details.required("rulesetId").asString()).isEqualTo(seeded);
        assertThat(details.required("versionNo").asInt()).isEqualTo(1);
        assertThat(details.required("patches").asInt()).isEqualTo(2);
        // Document 2 (2026-09-28, Register phase 4): the entry holds the request, the rules the model was shown and the
        // tokens the answers to its prompts spent, the recorded answer's 1,000 in and 500 out
        assertThat(details.required("requestText").asString()).isEqualTo(ChangeRequests.scripted());
        assertThat(strings(details.required("candidates"))).isEqualTo(CANDIDATES);
        assertThat(details.required("tokens").asLong()).isEqualTo(1_500);
    }

    // RT-04: the planted text after the threshold request, and an answer that obeyed it. Expected: the refusal with
    // the proposal validator's codes and the answer as the analyst would see it, no repair, and nothing stored
    @Test
    void rt04IsRefusedWithoutARepairAndNothingIsStored() {
        model.willAnswer(ChangeRequests.rt04Answer().toString());

        ServerSentEvents events = ServerSentEvents.parse(submit(ChangeRequests.rt04()).body());

        assertThat(events.names()).containsExactly("analyzing", "proposing", "validating", "error");
        JsonNode error = events.first("error");
        assertThat(error.required("code").asString()).isEqualTo("RULESET_INVALID");
        List<String> codes = error.required("findings").valueStream()
                .map(finding -> finding.required("code").asString()).toList();
        assertThat(codes).contains("PATCH_REMOVES_UNMENTIONED", "PATCH_SETS_DEFAULTS")
                .allMatch(List.of("PATCH_REMOVES_UNMENTIONED", "PATCH_OUTSIDE_CANDIDATES", "PATCH_SETS_DEFAULTS")
                        ::contains);
        assertThat(error.required("document")).isEqualTo(ChangeRequests.rt04Answer());
        assertThat(model.asked()).hasSize(1);
        assertThat(storedFor(ChangeRequests.rt04())).isZero();
    }

    // Document 4, Repair Loop: at most two repairs, then the failure is reported with its findings. Expected: three
    // asks, the schema finding of the too-short summary, every answer forgotten from the cache, and nothing stored
    @Test
    void aProposalStillInvalidAfterTwoRepairsIsNotStored() {
        String invalid = "{\"summary\":\"x\",\"patches\":[],\"untouched\":[],\"notes\":\"\"}";
        String request = ChangeRequests.scripted() + " " + UUID.randomUUID();
        model.willAnswer(invalid);
        model.willAnswer(invalid);
        model.willAnswer(invalid);

        JsonNode error = ServerSentEvents.parse(submit(request).body()).first("error");

        assertThat(error.required("code").asString()).isEqualTo("RULESET_INVALID");
        assertThat(error.required("findings").valueStream().map(finding -> finding.required("path").asString()))
                .containsExactly("/summary");
        assertThat(model.asked()).hasSize(3);
        assertThat(model.forgotten()).hasSize(3);
        assertThat(storedFor(request)).isZero();
    }

    // Document 4: an answer that is not JSON is a validation failure; after the repairs, a defined error. Expected:
    // RULESET_INVALID with no findings and no document, and nothing stored
    @Test
    void anAnswerThatIsNeverJsonEndsWithRulesetInvalid() {
        String request = ChangeRequests.scripted() + " " + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            model.willAnswer("I cannot help with that.");
        }

        JsonNode error = ServerSentEvents.parse(submit(request).body()).first("error");

        assertThat(error.required("code").asString()).isEqualTo("RULESET_INVALID");
        assertThat(error.required("findings")).isEmpty();
        assertThat(error.required("document").isNull()).isTrue();
        assertThat(storedFor(request)).isZero();
    }

    // Document 4, Guardrails: a provider that fails is a defined failure. Expected: PROVIDER_UNAVAILABLE after the
    // proposing event, and nothing stored
    @Test
    void aProviderFailureEndsTheStreamWithAnError() {
        String request = ChangeRequests.scripted() + " " + UUID.randomUUID();
        model.willFail(new LlmUnavailableException(LlmUnavailableException.Reason.TIMEOUT, "no answer"));

        ServerSentEvents events = ServerSentEvents.parse(submit(request).body());

        assertThat(events.names()).containsExactly("analyzing", "proposing", "error");
        assertThat(events.first("error").required("code").asString()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(storedFor(request)).isZero();
    }

    // Document 2, the change stream (2026-09-28, Register phase 4): every event after analyzing carries ended, the
    // stage that just finished, its milliseconds and the tokens the answers to its prompts spent. Expected: the stages
    // in Document 2's order, the recorded answer's 1,000 in and 500 out on proposing, and null where no prompt was
    // answered
    @Test
    void everyEventAfterAnalyzingSaysWhichStageEndedWithItsTimeAndTokens() {
        model.willAnswer(ChangeRequests.scriptedPatches().toString());

        ServerSentEvents events = ServerSentEvents.parse(submit(ChangeRequests.scripted()).body());

        assertThat(events.first("analyzing").required("ended").isNull()).isTrue();
        List<JsonNode> ended = Stream.of("proposing", "validating", "regression", "proposal")
                .map(name -> events.first(name).required("ended")).toList();
        assertThat(ended.stream().map(stage -> stage.required("stage").asString()))
                .containsExactly("analyzing", "proposing", "validating", "regression");
        assertThat(ended).allSatisfy(stage -> assertThat(stage.required("ms").asLong()).isNotNegative());
        assertThat(ended.stream().map(stage -> stage.required("tokens").isNull()))
                .containsExactly(true, false, true, true);
        assertThat(ended.get(1).required("tokens").asInt()).isEqualTo(1_500);
    }

    // Document 4, Repair Loop: a repair is asked while validating. Expected: the repaired answer's 1,500 tokens are
    // the validating stage's, on the regression event
    @Test
    void aRepairIsSpentInTheValidatingStage() {
        model.willAnswer("{\"summary\":\"x\",\"patches\":[],\"untouched\":[],\"notes\":\"\"}");
        model.willAnswer(ChangeRequests.scriptedPatches().toString());

        ServerSentEvents events = ServerSentEvents.parse(submit(ChangeRequests.scripted()).body());

        JsonNode validating = events.first("regression").required("ended");
        assertThat(validating.required("stage").asString()).isEqualTo("validating");
        assertThat(validating.required("tokens").asInt()).isEqualTo(1_500);
        assertThat(model.asked()).hasSize(2);
    }

    // Document 2 (2026-09-28): a proposal's audit entry holds the tokens the answers to its prompts spent, a repair's
    // too. Expected: the first answer's 1,500 and the repaired answer's 1,500
    @Test
    void aProposalsAuditEntryCountsEveryAnswerItTook() {
        model.willAnswer("{\"summary\":\"x\",\"patches\":[],\"untouched\":[],\"notes\":\"\"}");
        model.willAnswer(ChangeRequests.scriptedPatches().toString());

        UUID id = UUID.fromString(ServerSentEvents.parse(submit(ChangeRequests.scripted()).body()).first("proposal")
                .required("id").asString());

        JsonNode details = JSON.readTree(jdbc.sql(
                "select details_json::text from audit_entry where change_request_id = :id")
                .param("id", id).query(String.class).single());
        assertThat(details.required("tokens").asLong()).isEqualTo(3_000);
        assertThat(model.asked()).hasSize(2);
    }

    // Expected: the error event says which stage it ended too; a provider that fails while proposing ends proposing,
    // with no answer and so no tokens
    @Test
    void anErrorSaysWhichStageItEnded() {
        model.willFail(new LlmUnavailableException(LlmUnavailableException.Reason.TIMEOUT, "no answer"));

        JsonNode ended = ServerSentEvents.parse(submit(ChangeRequests.scripted() + " " + UUID.randomUUID()).body())
                .first("error").required("ended");

        assertThat(ended.required("stage").asString()).isEqualTo("proposing");
        assertThat(ended.required("ms").asLong()).isNotNegative();
        assertThat(ended.required("tokens").isNull()).isTrue();
    }

    // Document 2 (2026-09-28): a call the day's token budget stops ends the stream with BUDGET_EXHAUSTED, the budget's
    // own code, not the provider's. Expected: that code, and nothing stored
    @Test
    void aSpentBudgetEndsTheStreamWithBudgetExhausted() {
        String request = ChangeRequests.scripted() + " " + UUID.randomUUID();
        model.willFail(new LlmUnavailableException(LlmUnavailableException.Reason.BUDGET_EXHAUSTED, "spent"));

        ServerSentEvents events = ServerSentEvents.parse(submit(request).body());

        assertThat(events.first("error").required("code").asString()).isEqualTo("BUDGET_EXHAUSTED");
        assertThat(storedFor(request)).isZero();
    }

    // Document 2, change_request (2026-09-28, Register phase 4): a request's number in its sandbox, from 1, which the
    // UI writes CR-0001. Expected: 1, then 2 on the proposal and in its row, and 1 for another sandbox's first request
    @Test
    void aStoredRequestIsNumberedInItsSandboxFromOne() {
        for (int i = 0; i < 3; i++) {
            model.willAnswer(ChangeRequests.scriptedPatches().toString());
        }

        JsonNode first = ServerSentEvents.parse(submit(ChangeRequests.scripted()).body()).first("proposal");
        JsonNode second = ServerSentEvents.parse(submit(ChangeRequests.scripted()).body()).first("proposal");
        JsonNode another = ServerSentEvents.parse(submit(ChangeRequests.scripted(), api().login()).body())
                .first("proposal");

        assertThat(first.required("number").asInt()).isEqualTo(1);
        assertThat(second.required("number").asInt()).isEqualTo(2);
        assertThat(jdbc.sql("select number from change_request where id = :id")
                .param("id", UUID.fromString(second.required("id").asString())).query(Integer.class).single())
                .isEqualTo(2);
        assertThat(another.required("number").asInt()).isEqualTo(1);
    }

    // Document 5, Input limits: a change request is normalized like a chat message, and a bidi override is a format
    // character, stripped. Expected: the request stored and prompted without it
    @Test
    void aBidiOverrideIsStrippedBeforeTheRequestIsStoredOrPrompted() {
        String override = Character.toString(0x202E);
        model.willAnswer(ChangeRequests.scriptedPatches().toString());

        String id = ServerSentEvents.parse(submit(override + ChangeRequests.scripted()).body()).first("proposal")
                .required("id").asString();

        assertThat(jdbc.sql("select request_text from change_request where id = :id").param("id", UUID.fromString(id))
                .query(String.class).single()).isEqualTo(ChangeRequests.scripted());
        assertThat(model.lastUserPrompt()).doesNotContain(override);
    }

    private HttpResponse<String> submit(String text) {
        return submit(text, session);
    }

    private HttpResponse<String> submit(String text, String cookie) {
        HttpResponse<String> response = api().post("/api/v1/rulesets/" + seeded + "/versions/1/changes").web()
                .cookie(cookie).json(JSON.createObjectNode().put("text", text).toString()).send();
        assertThat(response.statusCode()).isEqualTo(200);
        return response;
    }

    /** How many change requests with this text this test's sandbox has stored. */
    private long storedFor(String request) {
        return jdbc.sql("select count(*) from change_request where sandbox_id = :sandbox and request_text = :text")
                .param("sandbox", sandboxOf()).param("text", request).query(Long.class).single();
    }

    /** The sandbox of this test's session, read from a chat session it opens on the seeded version. */
    private UUID sandboxOf() {
        String chat = JsonPath.read(api().post("/api/v1/chat/sessions").web().cookie(session)
                .json("{\"rulesetId\":\"" + seeded + "\",\"versionNo\":1}").send().body(), "$.id");
        return jdbc.sql("select sandbox_id from chat_session where id = :id").param("id", UUID.fromString(chat))
                .query(UUID.class).single();
    }

    private UUID seededVersionId() {
        return jdbc.sql("select v.id from ruleset_version v join ruleset r on r.id = v.ruleset_id where r.id = :id")
                .param("id", UUID.fromString(seeded)).query(UUID.class).single();
    }

    private void awaitSeededVersionReady() {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!"READY".equals(jdbc.sql("""
                select v.embedding_status from ruleset_version v join ruleset r on r.id = v.ruleset_id
                where r.protected and r.domain = :domain and v.version_no = 1""").param("domain", Seeded.LENDING)
                .query(String.class).single())) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the seeded version never became READY");
            }
            Thread.onSpinWait();
        }
    }

    private static List<String> strings(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }
}
