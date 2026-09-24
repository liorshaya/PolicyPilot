package com.liorshaya.policypilot.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.ChatTool;
import com.liorshaya.policypilot.ai.Completion;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.adapter.SpringAiLlmGateway;
import com.liorshaya.policypilot.common.Hashes;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.liorshaya.policypilot.support.RecordedEmbeddingGateway;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The live answers to the three scripted questions (Work Plan day 9: "the three scripted questions recorded"). The
 * questions go through the real chat use case, the real retrieval on the recorded vectors, the real tools and engine,
 * and the real provider; every answer is written to {@code fixtures/eval/recordings/openai/answer/<version>/}, the
 * active version's folder, with the tool calls the model made, so {@link RecordedAnswerIT} replays it offline. It is
 * tagged {@code live}, so CI never runs it, and needs a real {@code OPENAI_API_KEY}; the command is in
 * fixtures/eval/recordings/README.md.
 */
@Tag("live")
@SpringBootTest(properties = {"spring.ai.openai.api-key=${OPENAI_API_KEY}",
        "policypilot.ai.daily-token-budget=400000"})
@ActiveProfiles("openai")
@Import(LiveAnswerRecordingIT.Recording.class)
class LiveAnswerRecordingIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path RECORDINGS = Path.of("..", "fixtures", "eval", "recordings", "openai");

    /** A database of its own, like {@link RecordedAnswerIT}'s: the recorded vectors answer only recorded texts. */
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
    private RecordingModel model;

    @Test
    void theThreeScriptedQuestionsAreAnsweredAndRecorded() {
        ScriptedQuestions questions = new ScriptedQuestions(chat, rulesets, decisions, jdbc);

        for (String id : ScriptedQuestions.SCRIPTED) {
            String question = ScriptedQuestions.labeled(id).required("question").asString();
            long started = System.nanoTime();
            ScriptedQuestions.Asked asked = questions.ask(question);
            System.out.printf("%s in %d ms, cited %s, tools %s%n%s%n", id, (System.nanoTime() - started) / 1_000_000,
                    asked.cited(), asked.toolCalls(), asked.text());
        }

        assertThat(model.written()).hasSize(ScriptedQuestions.SCRIPTED.size());
    }

    /**
     * The real gateway, with every streamed answer written as the provider sent it: the tool calls in the order the
     * model made them, and the raw text before the marker resolver, which the replay runs through the resolver again.
     * The answers go under the given provider's recordings, in {@code <prompt>/<version>/}.
     */
    static final class RecordingModel implements LlmGateway {

        private final LlmGateway provider;
        private final String model;
        private final Path recordings;
        private final List<Path> written = new ArrayList<>();

        RecordingModel(LlmGateway provider, String model, Path recordings) {
            this.provider = provider;
            this.model = model;
            this.recordings = recordings;
        }

        List<Path> written() {
            return List.copyOf(written);
        }

        @Override
        public <T> Completion<T> complete(PromptSpec spec, Class<T> type) {
            return provider.complete(spec, type);
        }

        @Override
        public void forget(PromptSpec spec) {
            provider.forget(spec);
        }

        @Override
        public TokenUsage stream(PromptSpec spec, List<ChatTool> tools, Consumer<String> tokens) {
            ArrayNode steps = JSON.createArrayNode();
            StringBuilder text = new StringBuilder();
            List<ChatTool> recorded = tools.stream().map(tool -> (ChatTool) new RecordedTool(tool, steps)).toList();
            TokenUsage usage = provider.stream(spec, recorded, piece -> {
                text.append(piece);
                tokens.accept(piece);
            });
            write(spec, steps, text.toString(), usage);
            return usage;
        }

        private synchronized void write(PromptSpec spec, ArrayNode steps, String response, TokenUsage usage) {
            String hash = Hashes.sha256Hex(spec.system() + "\u001f" + spec.user());
            ObjectNode recording = JSON.createObjectNode();
            ObjectNode request = recording.putObject("request");
            request.put("prompt", spec.promptName());
            request.put("version", spec.promptVersion());
            request.put("model", model);
            request.put("inputHash", hash);
            request.put("system", spec.system());
            request.put("user", spec.user());
            recording.set("steps", steps);
            recording.put("response", response);
            recording.putObject("usage").put("inputTokens", usage.inputTokens())
                    .put("outputTokens", usage.outputTokens());
            try {
                Path directory = recordings.resolve(spec.promptName()).resolve(spec.promptVersion());
                Files.createDirectories(directory);
                Path file = directory.resolve(hash + ".json");
                Files.writeString(file, recording.toPrettyString() + "\n");
                written.add(file);
            } catch (IOException e) {
                throw new UncheckedIOException("could not write the recording", e);
            }
        }
    }

    /** A tool as the model sees it, with each call's arguments kept in the order the model made them. */
    private record RecordedTool(ChatTool tool, ArrayNode steps) implements ChatTool {

        @Override
        public String name() {
            return tool.name();
        }

        @Override
        public String description() {
            return tool.description();
        }

        @Override
        public String inputSchema() {
            return tool.inputSchema();
        }

        @Override
        public String call(String argumentsJson) {
            synchronized (steps) {
                steps.addObject().put("tool", tool.name()).put("arguments", argumentsJson);
            }
            return tool.call(argumentsJson);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recording {

        @Bean
        @Primary
        RecordingModel recordingModel(SpringAiLlmGateway provider, PolicyPilotProperties properties) {
            return new RecordingModel(provider, properties.ai().models().fast(), RECORDINGS);
        }

        @Bean
        @Primary
        RecordedEmbeddingGateway recordedEmbeddingGateway(PolicyPilotProperties properties) {
            return RecordedEmbeddingGateway.replaying(properties.embedding().dimension());
        }
    }
}
