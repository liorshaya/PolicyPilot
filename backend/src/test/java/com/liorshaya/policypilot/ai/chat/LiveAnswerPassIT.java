package com.liorshaya.policypilot.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.adapter.SpringAiLlmGateway;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.ruleset.service.PublishedVersion;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.liorshaya.policypilot.support.RecordedEmbeddingGateway;
import com.liorshaya.policypilot.support.Reviews;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.service.chat.ChatCitation;
import com.liorshaya.policypilot.ai.service.chat.ChatEvents;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
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
 * The live answer pass of evaluation run 1 (Work Plan day 11): every question of
 * {@code fixtures/eval/questions.json} asked of its own policy, through the real chat use case, the real retrieval
 * on the recorded vectors, the real tools and engine, and the real provider. Every answer is written to
 * {@code fixtures/eval/recordings/openai/answer/v1/}, which is what {@code EvalRunnerIT} then scores citation
 * accuracy from, offline and for nothing.
 *
 * <p>Only the answers are paid for. Retrieval runs on the vectors day 8 recorded, so the embedding pass is not
 * repeated, and a question the Threshold stops costs nothing at all because it never reaches the model. It is
 * tagged {@code live}, so CI never runs it, and it needs a real {@code OPENAI_API_KEY}.
 *
 * <p>Run it with:
 * {@code ./mvnw verify -Dit.test=LiveAnswerPassIT -Dgroups=live -DskipUTs=true}
 */
@Tag("live")
@SpringBootTest(properties = {"spring.ai.openai.api-key=${OPENAI_API_KEY}",
        "policypilot.ai.daily-token-budget=400000"})
@ActiveProfiles("openai")
@Import(LiveAnswerPassIT.Recording.class)
class LiveAnswerPassIT {

    /** A database of its own: the recorded vectors answer only the texts that pass recorded them. */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresContainerSupport.PGVECTOR_IMAGE);

    static {
        POSTGRES.start();
    }

    @Autowired
    private ChatService chat;

    @Autowired
    private PolicyService policies;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private DecisionService decisions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private LiveAnswerRecordingIT.RecordingModel model;

    /** What the pass spent, printed at the end: the number the day's token budget is read against. */
    private int spentIn;
    private int spentOut;

    @Test
    void everyLabeledQuestionIsAnsweredLiveAndRecorded() {
        JsonNode questions = Fixtures.json("eval/questions.json").required("questions");
        Map<String, Target> published = new LinkedHashMap<>();
        int asked = 0;
        int stopped = 0;
        long started = System.nanoTime();
        for (JsonNode question : questions) {
            Target target = published.computeIfAbsent(question.required("policy").asString(),
                    policy -> publish(policy, question));
            String id = question.required("id").asString();
            long began = System.nanoTime();
            String text = ask(target, question.required("question").asString());
            asked++;
            boolean refused = text.startsWith(NOT_COVERED_HE) || text.startsWith(NOT_COVERED_EN);
            if (refused) {
                stopped++;
            }
            System.out.printf("%-5s %-28s %5d ms  %s%n", id, question.required("policy").asString(),
                    (System.nanoTime() - began) / 1_000_000, text.replace('\n', ' '));
        }

        System.out.printf("%d questions in %d s; %d answered with the fixed sentence; %d recordings written%n",
                asked, (System.nanoTime() - started) / 1_000_000_000L, stopped, model.written().size());
        System.out.printf("spent %d input + %d output = %d tokens of the day's 400,000%n",
                spentIn, spentOut, spentIn + spentOut);
        assertThat(asked).isEqualTo(questions.size());
        assertThat(model.written()).isNotEmpty();
    }

    private static final String NOT_COVERED_HE = "המסמכים אינם עוסקים";
    private static final String NOT_COVERED_EN = "The documents do not";

    /** One question in a session of its own, so no history enters the prompt and the hash is the question's. */
    private String ask(Target target, String question) {
        ChatSessionView session = chat.open(target.rulesetId(), 1, target.sandboxId()).orElseThrow();
        ChatService.Prepared prepared = chat.prepare(session.id(), target.sandboxId()).orElseThrow();
        Answer answer = new Answer();
        chat.answer(prepared, question, answer);
        spentIn += answer.inputTokens;
        spentOut += answer.outputTokens;
        return answer.text.toString();
    }

    /**
     * The policy of one question, published in a sandbox of its own. The lending policy's sandbox also decides the
     * 200 fixture cases, because {@code getDecision} and {@code simulate} answer Q-01 and Q-02 from them.
     */
    private Target publish(String policy, JsonNode question) {
        String textPath = question.required("policyText").asString();
        PolicyLanguage language = textPath.endsWith(".en.md") ? PolicyLanguage.EN : PolicyLanguage.HE;
        UUID sandbox = UUID.randomUUID();
        PolicyView view = policies.create(sandbox, policy, language, read(textPath));
        UUID policyVersion = policies.version(view.id(), 1, sandbox).orElseThrow().id();
        VersionView draft = rulesets.createDraft(sandbox, policyVersion,
                Fixtures.json(question.required("ruleset").asString()), ValidationContext.ANALYST_EDIT, Set.of());
        UUID rulesetId = Reviews.reviewed(rulesets, draft, sandbox).rulesetId();
        UUID versionId = rulesets.publish(rulesetId, 1, sandbox).orElseThrow().versionId();
        awaitReady(versionId);
        if ("consumer-lending".equals(policy)) {
            PublishedVersion published = rulesets.published(rulesetId, 1, sandbox).orElseThrow();
            decisions.decideFixtureSet(published, sandbox, "cases-200");
        }
        return new Target(sandbox, rulesetId);
    }

    private void awaitReady(UUID version) {
        long deadline = System.nanoTime() + 120_000_000_000L;
        String status;
        while (!"READY".equals(status = jdbc.sql("select embedding_status from ruleset_version where id = :id")
                .param("id", version).query(String.class).single())) {
            if ("FAILED".equals(status) || System.nanoTime() > deadline) {
                throw new AssertionError("version " + version + " is " + status);
            }
            Thread.onSpinWait();
        }
    }

    private record Target(UUID sandboxId, UUID rulesetId) {}

    /** Collects the streamed answer and what it spent; the runner scores the citations from the recordings. */
    private static final class Answer implements ChatEvents {

        private final StringBuilder text = new StringBuilder();
        private final List<String> cited = new ArrayList<>();
        private int inputTokens;
        private int outputTokens;

        @Override
        public void token(String piece) {
            text.append(piece);
        }

        @Override
        public void citations(List<ChatCitation> citations) {
            citations.forEach(citation -> cited.add(citation.id()));
        }

        @Override
        public void usage(TokenUsage usage, int toolCalls) {
            inputTokens = usage.inputTokens();
            outputTokens = usage.outputTokens();
        }

        @Override
        public void done(UUID messageId) {
            // the message is stored; nothing more is needed here
        }
    }

    private static String read(String relative) {
        try {
            return Files.readString(Fixtures.path(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recording {

        @Bean
        @Primary
        LiveAnswerRecordingIT.RecordingModel recordingModel(SpringAiLlmGateway provider,
                PolicyPilotProperties properties) {
            return new LiveAnswerRecordingIT.RecordingModel(provider, properties.ai().models().fast());
        }

        @Bean
        @Primary
        RecordedEmbeddingGateway recordedEmbeddingGateway(PolicyPilotProperties properties) {
            return RecordedEmbeddingGateway.replaying(properties.embedding().dimension());
        }
    }
}
