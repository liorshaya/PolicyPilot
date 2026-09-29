package com.liorshaya.policypilot.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.service.chat.ToolCallReport;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.liorshaya.policypilot.support.RecordedEmbeddingGateway;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.RecordedGateway.Streamed;
import com.liorshaya.policypilot.support.RecordedGateway.ToolCall;
import com.liorshaya.policypilot.support.Requirement;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Demo step 3 served from the cache (Document 4, Serving the scripted questions from the cache; Document 6, Cached chat
 * first token), on the recorded answers of {@link RecordedAnswerIT}: a scripted answer that met its label is served
 * again without the model once its tool calls return in the caller's sandbox what they returned when it was written,
 * and answered live when they do not. The cache lives in the database of this class alone, and every test holds
 * whether or not an earlier one kept the question first: what a test asserts is what its own asks cost.
 */
@Requirement({"FR-13", "FR-14", "FR-15", "NFR-6"})
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-real")
@ActiveProfiles("openai")
@Import(CachedScriptedAnswerIT.Recorded.class)
class CachedScriptedAnswerIT {

    /** A database of its own: another class's recorded answers must never be served here, nor these there. */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresContainerSupport.PGVECTOR_IMAGE);

    static {
        POSTGRES.start();
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private ChatService chat;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private DecisionService decisions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordedGateway model;

    @BeforeEach
    void forgetWhatTheModelWasAsked() {
        model.reset();
    }

    // Document 4: a kept answer is served "in the caller's sandbox" once its tool results are equal; a visitor's new
    // sandbox that has decided the 200 cases is the demo's case. Expected: the second sandbox's answer is the first's,
    // text and citations, and asking it cost no model call
    @Test
    void aKeptAnswerIsServedInAnotherSandboxThatDecidedTheSameCasesWithoutTheModel() {
        String referral = question("Q-01");
        ScriptedQuestions.Asked first = decided().ask(referral);
        int asked = model.asked().size();

        ScriptedQuestions.Asked second = decided().ask(referral);

        assertThat(model.asked()).hasSize(asked);
        assertThat(asked).isLessThanOrEqualTo(1);
        assertThat(second.text()).isEqualTo(first.text());
        assertThat(second.cited()).isEqualTo(first.cited()).contains("d:17", "p:7");
        // the replay ran the tools again, so the stored calls are the same but for their own timing (Document 2, V13)
        assertThat(withoutTiming(second.toolCalls())).isEqualTo(withoutTiming(first.toolCalls()));
        assertThat(jdbc.sql("select count(*) from model_call where prompt_name = 'answer' and cache_hit")
                .query(Long.class).single()).isPositive();
    }

    // Document 4: "any difference (a sandbox that has not decided application 17 ...) discards the replay, and the
    // question is answered live with a fresh turn". Expected: the model asked once more, getDecision refused as not
    // found, and d:17 not cited
    @Test
    void aSandboxThatHasNotDecidedApplication17IsAnsweredLive() {
        String referral = question("Q-01");
        decided().ask(referral);
        int asked = model.asked().size();

        ScriptedQuestions.Asked undecided = ScriptedQuestions.undecided(chat, rulesets, jdbc).ask(referral);

        assertThat(model.asked()).hasSize(asked + 1);
        assertThat(model.toolResults().getLast()).startsWith("<tool_result error=\"not_found\">");
        assertThat(undecided.cited()).doesNotContain("d:17");
    }

    // Document 2, the tool event (2026-09-28, Register phase 4): a served answer's tool calls run again in the
    // caller's sandbox and are reported as they end, before its first token. Expected: no model call, one tool event
    // per call the served answer stored, all of them before the first token, and getDecision of application 17
    // referred by R-330, as the Python reference decides it (Document 3's worked example)
    @Test
    void aServedAnswerReportsItsToolCallsBeforeItsFirstToken() {
        String referral = question("Q-01");
        decided().ask(referral);
        int asked = model.asked().size();

        ScriptedQuestions.Asked served = decided().ask(referral);

        assertThat(model.asked()).hasSize(asked);
        assertThat(served.tools()).hasSize(JSON.readTree(served.toolCalls()).size()).isNotEmpty();
        assertThat(served.events().lastIndexOf("tool")).isLessThan(served.events().indexOf("token"));
        assertThat(served.tools()).anySatisfy(call -> {
            assertThat(call.tool()).isEqualTo("getDecision");
            assertThat(call.applicationNumber()).isEqualTo(17);
            assertThat(call.decided()).isEqualTo(new ToolCallReport.Decided("refer", "R-330", List.of()));
        });
    }

    // Document 4: a replay whose calls return something else is discarded and the question answered live with a
    // fresh turn. Expected: the tool events are the live turn's alone, one per call its answer stored, each refused
    // as not_found in a sandbox that decided nothing
    @Test
    void aDiscardedReplayReportsOnlyTheLiveTurnsCalls() {
        String referral = question("Q-01");
        decided().ask(referral);

        ScriptedQuestions.Asked undecided = ScriptedQuestions.undecided(chat, rulesets, jdbc).ask(referral);

        assertThat(undecided.tools()).hasSize(JSON.readTree(undecided.toolCalls()).size()).isNotEmpty();
        assertThat(undecided.tools()).allSatisfy(call -> assertThat(call.refused()).isEqualTo("not_found"));
    }

    // Document 4: "A live answer is kept only when it ... meets its label". Q-03's label asks for 84; an answer
    // without it is not kept. Expected: the same question asked again goes to the model again
    @Test
    void anAnswerThatMissesItsLabelIsNotKept() {
        String term = question("Q-03");
        model.willStream(Streamed.text("התקופה המקסימלית היא שבע שנים.[[p:2]]"),
                Streamed.text("התקופה המקסימלית היא שבע שנים.[[p:2]]"));
        ScriptedQuestions questions = decided();

        questions.ask(term);
        questions.ask(term);

        assertThat(model.asked()).hasSize(2);
    }

    // Document 4, Words that name an outcome: Q-02's live answers said "אושרה" where its label says "מאושר", and the
    // cache kept none. Q-02 comes after Q-07, a labeled question the cache never keeps, so the entry is this test's
    // alone. Expected: the answer that says "אושרה" is kept, and the same conversation in another sandbox gets it
    // without the model
    @Test
    void anAnswerThatSaysAnotherFormOfItsOutcomeIsKept() {
        String opening = question("Q-07");
        Streamed rule = Streamed.text("הכלל R-320 מפנה לבדיקת חתם כשיחס החוב להכנסה בין 35% ל-40%.[[r:R-320]]");
        Streamed approved = Streamed.after("כן. בסימולציה עם ערב, בקשה 17 אושרה לפי R-900."
                + "[[sim:d17:has_guarantor=true]] [[r:R-900]] [[p:9]]",
                new ToolCall("getDecision", "{\"applicationNumber\":17}"),
                new ToolCall("simulate", "{\"applicationNumber\":17,\"overrides\":{\"has_guarantor\":true}}"));
        // the fourth is there for a cache that does not keep the answer: the second Q-02 then reaches the model
        model.willStream(rule, approved, rule, approved);

        ScriptedQuestions.Asked first = decided().askInOrder(opening, question("Q-02"));
        ScriptedQuestions.Asked second = decided().askInOrder(opening, question("Q-02"));

        assertThat(model.asked()).hasSize(3);
        assertThat(first.text()).contains("אושרה");
        assertThat(second.text()).isEqualTo(first.text());
        assertThat(second.cited()).isEqualTo(first.cited()).contains("sim:d17:has_guarantor=true", "r:R-900");
    }

    // Document 6, Performance and Load: "The gateway is not called for the second, and its first token event arrives
    // within 500 ms of the request". Expected: no model call and a first token under 500 ms
    @Test
    void aServedAnswerSendsItsFirstTokenWithin500Milliseconds() {
        String guarantor = question("Q-02");
        ScriptedQuestions questions = decided();
        questions.ask(guarantor);
        int asked = model.asked().size();

        ScriptedQuestions.Asked served = questions.ask(guarantor);

        System.out.println("performance: cached chat first token " + served.firstToken().toMillis() + " ms");
        assertThat(model.asked()).hasSize(asked);
        assertThat(served.firstToken()).isLessThan(Duration.ofMillis(500));
    }

    private ScriptedQuestions decided() {
        return new ScriptedQuestions(chat, rulesets, decisions, jdbc);
    }

    private static String question(String id) {
        return ScriptedQuestions.labeled(id).required("question").asString();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorded {

        @Bean
        @Primary
        RecordedGateway recordedAnswers() {
            return RecordedGateway.replaying(Path.of("..", "fixtures", "eval", "recordings", "openai"));
        }

        @Bean
        @Primary
        RecordedEmbeddingGateway recordedEmbeddingGateway(PolicyPilotProperties properties) {
            return RecordedEmbeddingGateway.replaying(properties.embedding().dimension());
        }
    }

    /** The stored tool calls with the microseconds each took taken out, since a replay's are its own. */
    private static JsonNode withoutTiming(String toolCalls) {
        JsonNode calls = JSON.readTree(toolCalls);
        calls.forEach(call -> ((ObjectNode) call).remove("micros"));
        return calls;
    }
}
