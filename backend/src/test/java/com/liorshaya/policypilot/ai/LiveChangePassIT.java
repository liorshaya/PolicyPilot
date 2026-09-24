package com.liorshaya.policypilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.adapter.SpringAiEmbeddingGateway;
import com.liorshaya.policypilot.ai.change.ChangeAnalysis;
import com.liorshaya.policypilot.ai.prompt.DslCheatSheet;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.ai.service.Candidates;
import com.liorshaya.policypilot.ai.service.ChangeBase;
import com.liorshaya.policypilot.ai.service.ChangeService;
import com.liorshaya.policypilot.ai.service.Proposal;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.LiveRecordingGateway;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.liorshaya.policypilot.support.RecordedEmbeddingGateway;
import com.liorshaya.policypilot.support.Reviews;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The live change pass behind change correctness (Document 4; Work Plan day 13, the six requests): every labeled
 * request of {@code fixtures/eval/changes.json} proposed through the real change use case, on its policy published
 * with its labeled rule set, with the candidates the real candidate selection finds on the provider's vectors. Every
 * answer and every repair is written under the active version of the change prompt, in
 * {@code fixtures/eval/recordings/openai/change/<version>/}, which is what {@code EvalRunnerIT} scores change
 * correctness from, offline and for nothing.
 *
 * <p>Only what has no recording is paid for. A request whose first prompt is recorded is not asked again, so a re-run
 * pays only for what is missing (the scripted request's {@code change/v1} answer, recorded on day 12 by
 * {@link LiveChangeRecordingIT}, was left alone that way); and a policy whose vectors a pass recorded is embedded
 * from them, so the provider embeds only a policy no pass has published, whose vectors are then written beside the
 * others. Before a request is asked, the version the application stored must render the very prompt the runner
 * renders from the labeled files, or the recording would never be replayed.
 *
 * <p>It is tagged {@code live}, so CI never runs it, and it needs a real {@code OPENAI_API_KEY}. {@code live.budget}
 * caps what the pass may spend, the day's budget when it is not given; a call that could cross it is not asked:
 *
 * <pre>{@code
 * OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test=LiveChangePassIT -Dlive.tag= -Dlive.budget=35000 \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
 * }</pre>
 */
@Tag("live")
@SpringBootTest(properties = {"spring.ai.openai.api-key=${OPENAI_API_KEY}",
        "policypilot.ai.daily-token-budget=400000"})
@ActiveProfiles("openai")
@Import(LiveChangePassIT.Recording.class)
class LiveChangePassIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A database of its own: the recorded vectors answer only the texts a pass recorded. */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresContainerSupport.PGVECTOR_IMAGE);

    static {
        POSTGRES.start();
    }

    @Autowired
    private PolicyService policies;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private ChangeAnalysis analysis;

    @Autowired
    private LlmGateway gateway;

    @Autowired
    private PolicyPilotProperties properties;

    @Autowired
    private Vectors vectors;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void everyLabeledRequestIsProposedLiveAndRecorded() {
        LiveRecordingGateway model = new LiveRecordingGateway("openai", gateway, properties.ai().models().strong(),
                Long.getLong("live.budget", properties.ai().dailyTokenBudget()),
                LiveChangeRecordingIT.CHARACTERS_PER_TOKEN, LiveChangeRecordingIT.OUTPUT_ALLOWANCE);
        ChangeService service = new ChangeService(model, new PromptRegistry(PromptRegistry.PROMPTS, Map.of()),
                new DslCheatSheet());
        // the seeded version is embedded at startup, on the recorded vectors, before any corpus is recorded
        awaitEmbedded();
        Map<String, Boolean> recorded = new LinkedHashMap<>();
        for (JsonNode request : ChangeRequests.labeled()) {
            String id = request.required("id").asString();
            String text = request.required("text").asString();
            ChangeBase stored = publish(request);
            Candidates candidates = analysis.candidates(stored, text);
            ChangeBase labeled = ChangeRequests.base(request);
            PromptSpec first = service.specFor(labeled, text, candidates);
            assertThat(service.specFor(stored, text, candidates)).isEqualTo(first);
            System.out.printf("%s: seeds %s, candidates %s, the label's %s%n", id, candidates.seeds(),
                    candidates.ruleIds(), ChangeRequests.expectedCandidates(id));
            if (Files.exists(LiveRecordingGateway.fileOf("openai", first))) {
                System.out.println(id + ": recorded already, not asked again");
            } else {
                propose(service, id, labeled, text, candidates);
            }
            recorded.put(id, Files.exists(LiveRecordingGateway.fileOf("openai", first)));
        }

        System.out.println("spent " + model.spent());
        assertThat(recorded).containsOnlyKeys("CR-1", "CR-2", "CR-3", "CR-4", "CR-5", "CR-6")
                .doesNotContainValue(false);
    }

    /**
     * One request asked of the strong model on the labeled base, which the runner replays on, so its repairs are
     * recorded under the prompts the runner will send. An answer that is no JSON object even after the repairs is
     * recorded all the same, and the pass goes on to the next request.
     */
    private static void propose(ChangeService service, String id, ChangeBase base, String text,
            Candidates candidates) {
        try {
            Proposal proposal = service.propose(base, text, candidates, stage -> { });
            System.out.printf("%s: %d repairs, valid %b, refused %b%n%s%n", id, proposal.repairs(), proposal.valid(),
                    proposal.refused(), proposal.answer().toPrettyString());
        } catch (LlmMalformedOutputException e) {
            System.out.println(id + ": " + e.getMessage());
        }
    }

    /**
     * The request's policy, published with its labeled rule set in a sandbox of its own and titled by the rule set's
     * name as the runner titles it; embedded from the recordings, or by the provider and recorded when there are none.
     */
    private ChangeBase publish(JsonNode request) {
        String policy = request.required("policy").asString();
        String textPath = request.required("policyText").asString();
        JsonNode document = Fixtures.json(request.required("ruleset").asString());
        boolean unrecorded = !Files.exists(RecordedEmbeddingGateway.RECORDINGS.resolve(policy + ".json"));
        if (unrecorded) {
            vectors.record(policy);
        }
        UUID sandbox = UUID.randomUUID();
        PolicyView view = policies.create(sandbox, document.required("name").asString(),
                textPath.endsWith(".en.md") ? PolicyLanguage.EN : PolicyLanguage.HE, Fixtures.text(textPath));
        UUID policyVersion = policies.version(view.id(), 1, sandbox).orElseThrow().id();
        VersionView draft = rulesets.createDraft(sandbox, policyVersion, document, ValidationContext.ANALYST_EDIT,
                Set.of());
        UUID rulesetId = Reviews.reviewed(rulesets, draft, sandbox).rulesetId();
        rulesets.publish(rulesetId, 1, sandbox).orElseThrow();
        awaitEmbedded();
        if (unrecorded) {
            vectors.write();
        }
        return analysis.base(rulesetId, 1, sandbox).orElseThrow();
    }

    /** Waits until no version is left to embed: the seeded one at startup, then each one published here. */
    private void awaitEmbedded() {
        long deadline = System.nanoTime() + 120_000_000_000L;
        List<String> statuses;
        while (!(statuses = jdbc.sql("select embedding_status from ruleset_version where embedding_status is not null")
                .query(String.class).list()).stream().allMatch("READY"::equals)) {
            if (statuses.contains("FAILED") || System.nanoTime() > deadline) {
                throw new AssertionError("the versions' embeddings are " + statuses);
            }
            Thread.onSpinWait();
        }
    }

    /**
     * The vectors the passes recorded, and the provider for a policy none of them has: while a corpus is recorded
     * every text goes to the provider and is kept for that corpus's file, as the day 8 pass keeps one; at any other
     * time a text with no recording fails as it does offline, so nothing else is paid for or written.
     */
    static final class Vectors implements EmbeddingGateway {

        private final EmbeddingGateway provider;
        private final RecordedEmbeddingGateway recorded;
        private final Map<String, float[]> kept = new LinkedHashMap<>();
        private @Nullable String corpus;

        Vectors(EmbeddingGateway provider, RecordedEmbeddingGateway recorded) {
            this.provider = provider;
            this.recorded = recorded;
        }

        synchronized void record(String name) {
            corpus = name;
            kept.clear();
        }

        @Override
        public float[] embed(String text) {
            return embedAll(List.of(text)).getFirst();
        }

        @Override
        public synchronized List<float[]> embedAll(List<String> texts) {
            if (corpus == null) {
                return recorded.embedAll(texts);
            }
            List<float[]> answered = provider.embedAll(texts);
            for (int i = 0; i < texts.size(); i++) {
                kept.put(texts.get(i), answered.get(i));
            }
            return answered;
        }

        @Override
        public int dimension() {
            return provider.dimension();
        }

        /** Writes the corpus being recorded to its file and goes back to the recordings. */
        synchronized void write() {
            ObjectNode file = JSON.createObjectNode();
            file.put("provider", "openai").put("model", "text-embedding-3-small").put("dimension", provider.dimension())
                    .put("corpus", corpus);
            ArrayNode embeddings = file.putArray("embeddings");
            kept.forEach((text, vector) -> embeddings.add(RecordedEmbeddingGateway.entry(text, vector)));
            try {
                Files.writeString(RecordedEmbeddingGateway.RECORDINGS.resolve(corpus + ".json"),
                        file.toPrettyString() + "\n");
            } catch (IOException e) {
                throw new UncheckedIOException("could not write the recording", e);
            }
            corpus = null;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recording {

        @Bean
        @Primary
        Vectors vectors(SpringAiEmbeddingGateway provider, PolicyPilotProperties properties) {
            return new Vectors(provider, RecordedEmbeddingGateway.replaying(properties.embedding().dimension()));
        }
    }
}
