package com.liorshaya.policypilot.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.service.chat.OutcomeWords;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.liorshaya.policypilot.support.RecordedEmbeddingGateway;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.Requirement;
import java.nio.file.Path;
import java.util.ArrayList;
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

/**
 * Demo step 3 on the recorded answers (Document 6, AI Layer Testing: every test after the first live run is offline;
 * Work Plan day 9: "recorded answers"). The seeded lending version is retrieved on the recorded vectors and every
 * answer is the one {@link LiveAnswerRecordingIT} recorded, replayed with its tool calls through the real tools and
 * engine. The expectations are the labeled set's: each question's {@code expectedMarkers} cited, its
 * {@code expectedTool} called and its {@code answerContains} said, a word that names an outcome in any of its forms;
 * the rate question gets the fixed sentence without the model.
 */
@Requirement({"FR-13", "FR-14", "FR-15"})
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-real")
@ActiveProfiles("openai")
@Import(RecordedAnswerIT.Recorded.class)
class RecordedAnswerIT {

    /** A labeled word that names an outcome is said by any form of it (Document 4, Words that name an outcome). */
    private static final OutcomeWords WORDS = OutcomeWords.load();

    private static final String NOT_COVERED_HE =
            "המסמכים אינם עוסקים בשאלה הזו; אפשר לשאול על כלל, על סעיף או על מספר בקשה.";

    /** A database of its own: another context's embedding job must never meet the recorded vectors. */
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

    private ScriptedQuestions questions;

    @BeforeEach
    void decideTheFixtureSetInASandboxOfItsOwn() {
        model.reset();
        questions = new ScriptedQuestions(chat, rulesets, decisions, jdbc);
    }

    // Q-01, "why was application 17 referred?": getDecision, then [[d:17]] and [[p:7]], and the guarantor it lacks
    @Test
    void theReferralQuestionFetchesTheDecisionAndCitesItAndItsParagraph() {
        assertThat(missedLabels("Q-01")).isEmpty();
        assertThat(model.asked()).hasSize(1);
    }

    // Q-02, "would application 17 be approved with a guarantor?": simulate, then [[sim:...]] and [[r:R-900]]
    @Test
    void theGuarantorQuestionIsSimulatedAndCitesTheSimulation() {
        assertThat(missedLabels("Q-02")).isEmpty();
        assertThat(model.asked()).hasSize(1);
    }

    // Q-03, "what is the maximum loan term?": no tool, [[p:2]], and 84
    @Test
    void theTermQuestionCitesParagraphTwo() {
        assertThat(missedLabels("Q-03")).isEmpty();
        assertThat(model.asked()).hasSize(1);
    }

    // Q-04, the rate question the policy does not answer: the Threshold stops it. Expected: Document 4's fixed Hebrew
    // sentence, nothing cited, and the model never asked
    @Test
    void theRateQuestionGetsTheFixedSentenceWithoutTheModel() {
        JsonNode labeled = ScriptedQuestions.labeled("Q-04");

        ScriptedQuestions.Asked asked = questions.ask(labeled.required("question").asString());

        assertThat(labeled.required("refusal").asBoolean()).isTrue();
        assertThat(asked.text()).isEqualTo(NOT_COVERED_HE);
        assertThat(asked.cited()).isEmpty();
        assertThat(model.asked()).isEmpty();
    }

    /**
     * Asks a labeled question and lists what its answer misses of the labels: an expected marker not cited (a
     * {@code *} matching any id after its prefix), the expected tool not called or a tool called where none is, and
     * an expected word not said. Empty when the answer is as labeled.
     */
    private List<String> missedLabels(String id) {
        JsonNode labeled = ScriptedQuestions.labeled(id);
        ScriptedQuestions.Asked asked = questions.ask(labeled.required("question").asString());
        List<String> missed = new ArrayList<>();
        for (JsonNode marker : labeled.required("expectedMarkers")) {
            String expected = marker.asString().substring(2, marker.asString().length() - 2);
            String prefix = expected.substring(0, expected.length() - 1);
            boolean cited = expected.endsWith("*") ? asked.cited().stream().anyMatch(ref -> ref.startsWith(prefix))
                    : asked.cited().contains(expected);
            if (!cited) {
                missed.add("marker " + marker.asString() + " not among " + asked.cited());
            }
        }
        JsonNode tool = labeled.required("expectedTool");
        boolean toolAsLabeled = tool.isNull() ? "[]".equals(asked.toolCalls())
                : asked.toolCalls().contains("\"tool\": \"" + tool.asString() + "\"");
        if (!toolAsLabeled) {
            missed.add("tool " + tool + " but called " + asked.toolCalls());
        }
        for (JsonNode word : labeled.required("answerContains")) {
            if (!WORDS.says(asked.text(), word.asString())) {
                missed.add("word " + word.asString() + " not in " + asked.text());
            }
        }
        return missed;
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
