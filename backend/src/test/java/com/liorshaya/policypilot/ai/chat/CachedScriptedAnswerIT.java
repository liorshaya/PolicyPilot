package com.liorshaya.policypilot.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.liorshaya.policypilot.support.RecordedEmbeddingGateway;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.RecordedGateway.Streamed;
import com.liorshaya.policypilot.support.Requirement;
import java.nio.file.Path;
import java.time.Duration;
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
        assertThat(second.toolCalls()).isEqualTo(first.toolCalls());
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
}
