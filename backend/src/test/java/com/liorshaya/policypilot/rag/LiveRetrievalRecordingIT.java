package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.EmbeddingGateway;
import com.liorshaya.policypilot.ai.adapter.SpringAiEmbeddingGateway;
import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.rag.service.Retrieval;
import com.liorshaya.policypilot.rag.service.RetrievalService;
import com.liorshaya.policypilot.rag.service.RetrievedChunk;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.LiveProvider;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.liorshaya.policypilot.support.RecordedEmbeddingGateway;
import com.liorshaya.policypilot.support.Reviews;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The first live retrieval pass (Work Plan day 8: "one live embedding pass over the 30 committed questions, to catch
 * Hebrew misses early"). Each of the 13 policies the questions run on is published with its expected rule set through
 * the real pipeline and embedded by the real provider; every question goes through the real retrieval. It is tagged
 * {@code live}, so CI never runs it; the command is in fixtures/eval/recordings/README.md. The provider is
 * {@code -Dprovider} (Document 4: a column each): {@code openai} needs a real {@code OPENAI_API_KEY}, {@code ollama} a
 * local Ollama at {@code OLLAMA_BASE_URL} with the profile's models pulled.
 *
 * <p>Every vector is written to {@code fixtures/eval/recordings/<provider>/embedding/<model>/}, the model being the
 * profile's embedding model, so {@code RecordedRetrievalIT} replays them offline and the provider's answer and change
 * passes retrieve on them. The pass is written to {@code docs/eval/retrieval-first-pass.md} for OpenAI, the day 8
 * pass, and to {@code docs/eval/retrieval-first-pass-<provider>.md} for any other: recall at 8 against each question's
 * {@code expectedChunks}, whether the Threshold stopped it, and the misses.
 */
@Tag("live")
@SpringBootTest(properties = "spring.ai.openai.api-key=${OPENAI_API_KEY:not-a-real-key}")
@ActiveProfiles(resolver = LiveProvider.class)
@Import(LiveRetrievalRecordingIT.Recording.class)
class LiveRetrievalRecordingIT extends PostgresContainerSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String QUESTIONS = "questions";

    @Autowired
    private PolicyService policies;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private RetrievalService retrieval;

    @Autowired
    private RecordingGateway recorder;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void everyQuestionIsAskedOfItsPolicyAndEveryVectorIsRecorded() {
        JsonNode questions = Fixtures.json("eval/questions.json").required("questions");
        Map<String, Target> targets = new LinkedHashMap<>();
        for (JsonNode question : questions) {
            targets.computeIfAbsent(question.required("policy").asString(), policy -> publish(policy, question));
        }
        recorder.corpus(QUESTIONS);
        List<Row> rows = new ArrayList<>();
        for (JsonNode question : questions) {
            Target target = targets.get(question.required("policy").asString());
            Retrieval result = retrieval.retrieve(target.rulesetId(), 1, target.sandboxId(),
                    question.required("question").asString()).orElseThrow();
            rows.add(new Row(question, result));
        }
        recorder.write();
        writeReport(rows, recorder.model());

        assertThat(rows).hasSize(30);
    }

    /** The day 8 report keeps its name for OpenAI; another provider's pass is written beside it, not over it. */
    private static Path report(String provider) {
        return Path.of("..", "docs", "eval",
                "openai".equals(provider) ? "retrieval-first-pass.md" : "retrieval-first-pass-" + provider + ".md");
    }

    /** Publishes one policy's expected rule set in a sandbox of its own and waits for the provider's vectors. */
    private Target publish(String policy, JsonNode question) {
        recorder.corpus(policy);
        String textPath = question.required("policyText").asString();
        PolicyLanguage language = textPath.endsWith(".en.md") ? PolicyLanguage.EN : PolicyLanguage.HE;
        UUID sandbox = UUID.randomUUID();
        PolicyView view = policies.create(sandbox, policy, language, read(Fixtures.path(textPath)));
        UUID policyVersion = policies.version(view.id(), 1, sandbox).orElseThrow().id();
        VersionView draft = rulesets.createDraft(sandbox, policyVersion,
                Fixtures.json(question.required("ruleset").asString()), ValidationContext.ANALYST_EDIT, Set.of());
        UUID version = rulesets.publish(Reviews.reviewed(rulesets, draft, sandbox).rulesetId(), 1, sandbox)
                .orElseThrow().versionId();
        awaitReady(version);
        return new Target(sandbox, draft.rulesetId());
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

    private static void writeReport(List<Row> rows, String model) {
        StringBuilder report = new StringBuilder("""
                # Retrieval, first live pass

                Work Plan day 8: one live embedding pass over the 30 questions of `fixtures/eval/questions.json`, each
                asked of its own policy, published with its expected rule set and embedded by `%s`.
                Written by `LiveRetrievalRecordingIT`; the vectors are in `fixtures/eval/recordings/%s/embedding/`.
                Recall at 8 counts the question's `expectedChunks` among the chunks kept; a refusal question is right
                to be stopped by the Threshold, but day 9's answer prompt may also refuse it.

                | Question | Language | Refusal | Stopped | Best cosine | Recall at 8 | Missed | Kept |
                | --- | --- | --- | --- | --- | --- | --- | --- |
                """.formatted(model, LiveProvider.name()));
        int expected = 0;
        int found = 0;
        for (Row row : rows) {
            List<String> kept = row.result().chunks().stream().map(RetrievedChunk::id).toList();
            List<String> wanted = new ArrayList<>();
            row.question().required("expectedChunks").forEach(id -> wanted.add(id.asString()));
            List<String> missed = wanted.stream().filter(id -> !kept.contains(id)).toList();
            expected += wanted.size();
            found += wanted.size() - missed.size();
            report.append("| ").append(row.question().required("id").asString())
                    .append(" | ").append(row.question().required("language").asString())
                    .append(" | ").append(row.question().required("refusal").asBoolean() ? "yes" : "no")
                    .append(" | ").append(row.result().covered() ? "no" : "yes")
                    .append(" | ").append(String.format(Locale.ROOT, "%.3f", row.result().bestCosine()))
                    .append(" | ").append(wanted.isEmpty() ? "n/a" : (wanted.size() - missed.size()) + "/" + wanted.size())
                    .append(" | ").append(String.join(", ", missed))
                    .append(" | ").append(String.join(", ", kept)).append(" |\n");
        }
        report.append("\nOverall recall at 8: ").append(found).append(" of ").append(expected)
                .append(" expected chunks.\n");
        Path file = report(LiveProvider.name());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, report.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println(report);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Target(UUID sandboxId, UUID rulesetId) {}

    private record Row(JsonNode question, Retrieval result) {}

    /**
     * The real gateway, with every text and vector it returns kept under the corpus being embedded. The seeded demo
     * version is embedded at startup, before the test names a corpus; its texts are the consumer-lending corpus. The
     * corpora are written to the folder of the active provider's embedding model.
     */
    static final class RecordingGateway implements EmbeddingGateway {

        private final EmbeddingGateway provider;
        private final Path directory;
        private final String model;
        private final Map<String, Map<String, float[]>> corpora = new LinkedHashMap<>();
        private volatile String corpus = "consumer-lending";

        RecordingGateway(EmbeddingGateway provider, Path directory, String model) {
            this.provider = provider;
            this.directory = directory;
            this.model = model;
        }

        String model() {
            return model;
        }

        void corpus(String name) {
            this.corpus = name;
        }

        @Override
        public float[] embed(String text) {
            return embedAll(List.of(text)).getFirst();
        }

        @Override
        public synchronized List<float[]> embedAll(List<String> texts) {
            List<float[]> vectors = provider.embedAll(texts);
            Map<String, float[]> recorded = corpora.computeIfAbsent(corpus, ignored -> new LinkedHashMap<>());
            for (int i = 0; i < texts.size(); i++) {
                recorded.put(texts.get(i), vectors.get(i));
            }
            return vectors;
        }

        @Override
        public int dimension() {
            return provider.dimension();
        }

        synchronized void write() {
            try {
                Files.createDirectories(directory);
                for (Map.Entry<String, Map<String, float[]>> entry : corpora.entrySet()) {
                    ObjectNode file = JSON.createObjectNode();
                    file.put("provider", LiveProvider.name()).put("model", model)
                            .put("dimension", provider.dimension()).put("corpus", entry.getKey());
                    ArrayNode embeddings = file.putArray("embeddings");
                    entry.getValue().forEach((text, vector) -> embeddings.add(RecordedEmbeddingGateway.entry(text, vector)));
                    Files.writeString(directory.resolve(entry.getKey() + ".json"), file.toPrettyString() + "\n");
                }
            } catch (IOException e) {
                throw new UncheckedIOException("could not write the recordings", e);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recording {

        @Bean
        @Primary
        RecordingGateway recordingGateway(SpringAiEmbeddingGateway provider, Environment environment) {
            return new RecordingGateway(provider, LiveProvider.embeddings(environment),
                    LiveProvider.embeddingModel(environment));
        }
    }
}
