package com.liorshaya.policypilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.rag.service.Retrieval;
import com.liorshaya.policypilot.rag.service.RetrievalService;
import com.liorshaya.policypilot.rag.service.RetrievedChunk;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.liorshaya.policypilot.support.RecordedEmbeddingGateway;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.Reviews;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 * The evaluation runner (Document 4, Runner: "a Spring Boot test profile ... computes the metrics and writes
 * {@code docs/eval/<date>-<prompt-versions>.md}"). One run, one report: the author and review passes are scored
 * from the recordings by {@link RecordedScoring}, and retrieval recall at 8 and refusal accuracy are measured
 * here, over the thirty questions of {@code fixtures/eval/questions.json} on the vectors the day 8 live pass
 * recorded. Each question is asked of its own policy, published in a sandbox of its own.
 *
 * <p>Document 4 defines the metric as "Questions whose expected chunk is among the 8 retrieved", so the unit is a
 * question and one expected chunk is enough: a question the retrieval answered at all counts. The two stricter
 * readings --- every expected chunk of a question, and every expected chunk of all of them --- are reported
 * beside it, because the wording carries a real choice and a report that showed only the kindest number would not
 * be worth reading. What settles the reading is that the answer's own use of the chunks is what citation accuracy
 * measures; if this metric demanded every chunk it would measure the same thing, only harder.
 */
@Requirement({"FR-12", "FR-15", "NFR-6"})
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-real")
@ActiveProfiles("openai")
@Import(EvalRunnerIT.Recorded.class)
class EvalRunnerIT {

    /** Document 4, Runner: {@code -Dprovider=openai} or {@code -Dprovider=ollama}, which picks the recordings. */
    private static final String PROVIDER = System.getProperty("provider", "openai");
    /** Whose vectors {@link RecordedEmbeddingGateway} replays; day 8 recorded one provider's and no other's. */
    private static final String EMBEDDINGS_RECORDED_FOR = "openai";
    private static final RuleSetMapper MAPPER = new RuleSetMapper();

    /** A database of its own: another context's embedding job must never meet this context's recorded gateway. */
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
    private RetrievalService retrieval;

    @Autowired
    private JdbcClient jdbc;

    // Document 4: "Questions whose expected chunk is among the 8 retrieved", at least 0.90 on the strong model
    @Test
    void everyQuestionIsAskedOfItsPolicyAndRecallAtEightIsScored() {
        JsonNode questions = Fixtures.json("eval/questions.json").required("questions");
        Map<String, Target> published = new LinkedHashMap<>();
        List<Asked> asked = new ArrayList<>();
        for (JsonNode question : questions) {
            Target target = published.computeIfAbsent(question.required("policy").asString(),
                    policy -> publish(policy, question));
            asked.add(new Asked(question, retrieval.retrieve(target.rulesetId(), 1, target.sandboxId(),
                    question.required("question").asString())));
        }

        assertThat(asked).hasSize(30);
        report(asked);
    }

    /** One question, and what retrieval did with it: the chunks kept, or nothing when the threshold stopped it. */
    private record Asked(JsonNode question, Optional<Retrieval> retrieved) {

        /** The Threshold stopped it: no chunk reached the server-side cosine, so no model call is made. */
        boolean stopped() {
            return retrieved.isEmpty() || !retrieved.get().covered();
        }

        boolean refusal() {
            return question.required("refusal").asBoolean();
        }

        Set<String> expected() {
            Set<String> ids = new LinkedHashSet<>();
            question.required("expectedChunks").forEach(chunk -> ids.add(chunk.asString()));
            return ids;
        }

        /** The chunks the answer would have seen: none at all when the Threshold stopped the question. */
        Set<String> kept() {
            Set<String> ids = new LinkedHashSet<>();
            if (!stopped()) {
                retrieved.ifPresent(result -> result.chunks().forEach(chunk -> ids.add(idOf(chunk))));
            }
            return ids;
        }

        /** The expected chunks this question kept, of the ones it declared. */
        int found() {
            Set<String> found = expected();
            found.retainAll(kept());
            return found.size();
        }
    }

    private void report(List<Asked> asked) {
        List<Asked> scored = asked.stream().filter(one -> !one.expected().isEmpty()).toList();
        int answered = (int) scored.stream().filter(one -> one.found() > 0).count();
        int fully = (int) scored.stream().filter(one -> one.found() == one.expected().size()).count();
        int chunks = scored.stream().mapToInt(Asked::found).sum();
        int chunksExpected = scored.stream().mapToInt(one -> one.expected().size()).sum();

        // Document 4: "Not-covered questions answered with the fixed sentence, and covered questions not refused".
        // What counts is the answer, not how it was reached: the Threshold stopping a question and the model
        // quoting the sentence are the same outcome to the person who asked, and a covered question that gets the
        // sentence anyway is a miss however it got there.
        Recordings answers = Recordings.of(PROVIDER, "answer", "v1");
        List<Asked> refusals = asked.stream().filter(Asked::refusal).toList();
        List<Asked> covered = asked.stream().filter(one -> !one.refusal()).toList();
        int refusedRight = (int) refusals.stream().filter(one -> refusedWithTheSentence(one, answers)).count();
        int coveredRight = (int) covered.stream().filter(one -> !refusedWithTheSentence(one, answers)).count();
        List<String> refusedCovered = covered.stream().filter(one -> refusedWithTheSentence(one, answers))
                .map(one -> one.question().required("id").asString()).toList();

        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("author", "v1");
        versions.put("review", "v1");
        versions.put("answer", "v1");
        versions.put("change", "v1");
        // the report is dated, and a run that names its date is the one that writes it; no assertion depends on it
        String date = System.getProperty("eval.date", "");
        EvalReport report = new EvalReport(date.isEmpty() ? LocalDate.EPOCH : LocalDate.parse(date), versions);
        RecordedScoring recorded = new RecordedScoring(PROVIDER);
        RecordedScoring.Authoring authoring = recorded.scoreAuthoring(report);
        RecordedScoring.Reviewing reviewing = recorded.scoreReviewing(report);
        RecordedScoring.Changing changing = recorded.scoreChanges(report);
        if (!changing.unrecorded().isEmpty()) {
            report.note("Change correctness counts every labeled request, and " + changing.unrecorded() + " have no "
                    + "recorded change/v1 answer yet, so they count as not correct until the live change pass records "
                    + "them.");
        }
        report.note("Retrieval and refusal are measured on the vectors of text-embedding-3-small, recorded by "
                + "the day 8 live pass; the rest of the column is the strong model's.");
        report.note("Two prompt versions this run argues for, both held until evaluation run 2 on day 15: "
                + "`author/v2` gives the model the field names instead of asking it to invent them, and "
                + "`answer/v2` tells it that a question about how many or about which rules is a tool call and "
                + "not a refusal. Each changes a rendered prompt, which is the response cache's key, so each "
                + "costs the demo a re-warm; neither buys a demo moment, and gate G2 is due today.");
        report.note("Author metrics cover " + authoring.policies() + " of the labeled policies, the ones whose "
                + "authoring is recorded; reviewer metrics cover " + reviewing.policies() + ".");
        if (!EMBEDDINGS_RECORDED_FOR.equals(PROVIDER)) {
            // the recorded gateway replays one provider's vectors; scoring them into another's column would be a
            // number about OpenAI printed under Ollama, so the column stays empty until that pass is recorded
            report.note("Retrieval and refusal are not scored for " + PROVIDER + ": the embedding recordings under "
                    + "fixtures/eval/recordings/ are " + EMBEDDINGS_RECORDED_FOR + "'s. They need a live pass of "
                    + "their own.");
            System.out.println(report.markdown());
            writeIfDated(report, date);
            return;
        }
        scoreCitations(report, asked);
        report.score(PROVIDER, "Retrieval recall at 8", Metric.Score.of(answered, scored.size()))
                .score(PROVIDER, "Refusal accuracy",
                        Metric.Score.of(refusedRight + coveredRight, refusals.size() + covered.size()))
                .note("Retrieval recall at 8 is counted as Document 4 words it: a question whose expected chunk is "
                        + "among the eight. Read strictly, as every expected chunk of a question, it is "
                        + fully + " of " + scored.size() + "; counted chunk by chunk it is " + chunksExpected
                        + " expected chunks of which " + chunks + " were kept.")
                .note("Refusal accuracy counts both directions, on the answer rather than on how it was reached: "
                        + refusedRight + " of " + refusals.size() + " not-covered questions answered with the fixed "
                        + "sentence, and " + coveredRight + " of " + covered.size() + " covered questions not "
                        + "refused" + (refusedCovered.isEmpty() ? ""
                                : "; the covered questions that were refused are " + String.join(", ", refusedCovered))
                        + ".");
        scored.stream().filter(one -> one.found() < one.expected().size()).forEach(one -> {
            Set<String> missed = one.expected();
            missed.removeAll(one.kept());
            report.mismatch(one.question().required("id").asString() + " ("
                    + one.question().required("policy").asString() + "): missed " + String.join(", ", missed)
                    + (one.stopped() ? ", stopped by the threshold" : "")
                    + (one.question().hasNonNull("expectedTool")
                            ? "; the question's own tool is " + one.question().required("expectedTool").asString()
                            : ""));
        });
        System.out.println(report.markdown());
        writeIfDated(report, date);
    }

    /** A run that names its date writes the report; one that does not scores and asserts, and leaves no file. */
    private static void writeIfDated(EvalReport report, String date) {
        if (!date.isEmpty()) {
            System.out.println("report written to " + report.write().normalize());
        }
    }

    /**
     * Document 4: "Citation accuracy --- answers whose markers are all valid and include the expected source",
     * over the answers a live pass recorded. A question with no recorded answer is not counted either way; the
     * report says how many were scored.
     */
    private void scoreCitations(EvalReport report, List<Asked> asked) {
        Recordings answers = Recordings.of(PROVIDER, "answer", "v1");
        if (answers.isEmpty()) {
            return;
        }
        int counted = 0;
        int scored = 0;
        for (Asked one : asked) {
            JsonNode question = one.question();
            if (question.required("expectedMarkers").isEmpty()) {
                continue;
            }
            scored++;
            Recordings recorded = answers.about(List.of(question.required("question").asString()));
            if (recorded.isEmpty()) {
                // the Threshold stopped the question, so there is no answer at all. A question that expects a
                // marker and got none has not cited its source, and counting it anywhere but against the metric
                // would be the runner excusing the one case it exists to catch
                report.mismatch(question.required("id").asString()
                        + " (citations): no answer was written; the retrieval threshold stopped the question");
                continue;
            }
            RuleSet version = MAPPER.toRuleSet(Fixtures.json(question.required("ruleset").asString()));
            CitationScoring.Scored line = CitationScoring.score(question,
                    recorded.calls().getFirst().required("response").asString(),
                    recorded.calls().getFirst().path("steps"), version, paragraphsOf(question));
            if (line.counted()) {
                counted++;
            } else {
                report.mismatch(line.questionId() + " (citations): " + line.note());
            }
        }
        report.score(PROVIDER, "Citation accuracy", Metric.Score.of(counted, scored));
        report.note("Citation accuracy is scored over every one of the " + scored + " questions that expect a "
                + "marker, including any the Threshold stopped before the model: an answer that was never written "
                + "did not cite its source. A marker counts as valid when the version can supply it: a paragraph "
                + "the policy has, a rule the version has, or a decision or simulation a tool returned.");
    }

    /**
     * Whether the person who asked got the fixed sentence: because the Threshold stopped the question before any
     * model call, or because the model itself answered with it.
     */
    private static boolean refusedWithTheSentence(Asked one, Recordings answers) {
        Recordings recorded = answers.about(List.of(one.question().required("question").asString()));
        return RefusalScoring.refused(one.stopped(),
                recorded.isEmpty() ? null : recorded.calls().getFirst().required("response").asString());
    }

    private static int paragraphsOf(JsonNode question) {
        return (int) read(question.required("policyText").asString()).lines()
                .map(String::strip).filter(line -> !line.isEmpty()).count();
    }

    /** Publishes one policy's expected rule set in a sandbox of its own and waits for the recorded vectors. */
    private Target publish(String policy, JsonNode question) {
        String textPath = question.required("policyText").asString();
        PolicyLanguage language = textPath.endsWith(".en.md") ? PolicyLanguage.EN : PolicyLanguage.HE;
        UUID sandbox = UUID.randomUUID();
        PolicyView view = policies.create(sandbox, policy, language, read(textPath));
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

    private record Target(UUID sandboxId, UUID rulesetId) {}

    private static String idOf(RetrievedChunk chunk) {
        return chunk.id();
    }

    private static String read(String relative) {
        try {
            return Files.readString(Fixtures.path(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorded {

        @Bean
        @Primary
        RecordedEmbeddingGateway recordedEmbeddingGateway(PolicyPilotProperties properties) {
            return RecordedEmbeddingGateway.replaying(properties.embedding().dimension());
        }
    }
}
