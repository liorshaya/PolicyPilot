package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.rag.service.EmbeddingJob;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.FakeEmbeddingGateway;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The embedding job on publish (Document 2, RAG pipeline, Embedding; Work Plan day 8: "the embedding job with the
 * fake embedding gateway and every embedding_status transition"). Each test publishes a version of its own, on a
 * policy of its own whose tenth paragraph carries the text the fake keys its behavior on, so no two tests share a
 * version. The statuses are read from the database, where the API reads them.
 */
@Requirement("FR-12")
class EmbeddingJobIT extends ApiIntegrationTest {

    /** The lending corpus of ChunkerTest: 9 paragraphs and 20 rules, plus the test's own tenth paragraph. */
    private static final int CHUNKS = 30;
    private static final Duration PATIENCE = Duration.ofSeconds(15);

    @Autowired
    private PolicyService policies;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private EmbeddingJob job;

    @Autowired
    private FakeEmbeddingGateway gateway;

    @Autowired
    private JdbcClient jdbc;

    // Document 2: publishing sets PENDING, rag moves it to EMBEDDING, then READY with its chunks. Expected: READY and
    // one chunk per paragraph and per rule, p:1 to p:10 and the 20 rule ids
    @Test
    void publishingEmbedsTheVersionAndEndsReady() {
        UUID version = publish("פסקה לבדיקת הטמעה " + UUID.randomUUID());

        awaitStatus(version, "READY");

        assertThat(chunkIds(version)).hasSize(CHUNKS)
                .contains("PARAGRAPH:1", "PARAGRAPH:10", "RULE:R-010", "RULE:R-900");
    }

    // Document 2: chunk.embedding holds the vector the gateway returned for that chunk's text. Expected: the axis the
    // test registered for its own paragraph, read back from the database
    @Test
    void eachChunkStoresTheVectorTheGatewayReturnedForItsText() {
        String paragraph = "פסקה עם וקטור משלה " + UUID.randomUUID();
        gateway.register(paragraph, gateway.axis(7));

        UUID version = publish(paragraph);
        awaitStatus(version, "READY");

        String stored = jdbc.sql("select embedding::text from chunk where ruleset_version_id = :v and ref_id = '10'")
                .param("v", version).query(String.class).single();
        assertThat(stored).startsWith("[0,0,0,0,0,0,0,1,0,");
    }

    // Document 4, Lexical index: tsv holds the normalized text, so R-320 is one token r320. Expected: the rule chunk
    // of R-320, and only it, matches the tsquery 'r320'
    @Test
    void theLexicalIndexHoldsTheNormalizedText() {
        UUID version = publish("פסקה לבדיקת האינדקס " + UUID.randomUUID());
        awaitStatus(version, "READY");

        List<String> r320 = jdbc.sql("""
                select ref_id from chunk where ruleset_version_id = :v and tsv @@ to_tsquery('simple', 'r320')""")
                .param("v", version).query(String.class).list();
        assertThat(r320).containsExactly("R-320");
    }

    // Document 2: embedding is asynchronous, so publish returns first. Expected: publish returned while the gateway
    // still holds the call, the version is EMBEDDING meanwhile, and READY once the call is let go
    @Test
    void publishReturnsWhileTheVersionIsStillEmbedding() {
        String key = UUID.randomUUID().toString();

        UUID version = publish("פסקה שממתינה " + FakeEmbeddingGateway.WAIT + key);
        awaitStatus(version, "EMBEDDING");
        String whileHeld = status(version);
        gateway.release(key);
        awaitStatus(version, "READY");

        assertThat(whileHeld).isEqualTo("EMBEDDING");
        assertThat(chunkIds(version)).hasSize(CHUNKS);
    }

    // Document 2: FAILED with none. Expected: FAILED and no chunk stored for the version
    @Test
    void aGatewayFailureEndsFailedWithNoChunks() {
        UUID version = publish("פסקה שנכשלת " + FakeEmbeddingGateway.FAIL + " " + UUID.randomUUID());

        awaitStatus(version, "FAILED");

        assertThat(chunkIds(version)).isEmpty();
    }

    // Document 2: embedding_status is null on a DRAFT, and only published versions are embedded. Expected: the job
    // leaves a DRAFT without a status and without chunks, and never asks the gateway for its text
    @Test
    void aDraftIsNeverEmbedded() {
        String paragraph = "טיוטה שלא מוטמעת " + UUID.randomUUID();
        UUID version = draft(UUID.randomUUID(), paragraph).versionId();

        job.embed(version);

        assertThat(status(version)).isNull();
        assertThat(chunkIds(version)).isEmpty();
        assertThat(gateway.embedded(paragraph)).isFalse();
    }

    // Document 2: at startup rag takes PENDING, FAILED and EMBEDDING versions again. Expected: all three READY, with
    // their chunks replaced rather than added to
    @Test
    void startupTakesPendingFailedAndInterruptedVersionsAgain() {
        List<UUID> versions = List.of(publish("א " + UUID.randomUUID()), publish("ב " + UUID.randomUUID()),
                publish("ג " + UUID.randomUUID()));
        versions.forEach(version -> awaitStatus(version, "READY"));
        setStatus(versions.get(0), "PENDING");
        setStatus(versions.get(1), "FAILED");
        setStatus(versions.get(2), "EMBEDDING");

        job.resume();

        versions.forEach(version -> awaitStatus(version, "READY"));
        versions.forEach(version -> assertThat(chunkIds(version)).hasSize(CHUNKS));
    }

    // Document 2: a READY version is done; the job takes only a PENDING one. Expected: embedding a READY version
    // again changes nothing and asks the gateway nothing
    @Test
    void aReadyVersionIsNotEmbeddedAgain() {
        String paragraph = "פסקה מוכנה " + UUID.randomUUID();
        UUID version = publish(paragraph);
        awaitStatus(version, "READY");

        job.embed(version);

        assertThat(status(version)).isEqualTo("READY");
        assertThat(gateway.timesEmbedded(paragraph)).isEqualTo(1);
    }

    // Work Plan day 8, Done when: "publishing version 1 embeds its paragraphs and rules". Expected: the seeded
    // protected version 1 READY with its 29 chunks, 9 paragraphs and 20 rules
    @Test
    void theSeededVersionOneIsEmbedded() {
        UUID version = rulesets.version(rulesets.protectedRulesets().getFirst().id(), 1, UUID.randomUUID())
                .orElseThrow().versionId();

        awaitStatus(version, "READY");

        assertThat(chunkIds(version)).hasSize(29);
    }

    /** A published version of the lending rule set, on a policy of its own whose tenth paragraph is given. */
    private UUID publish(String tenthParagraph) {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = draft(sandbox, tenthParagraph);
        return rulesets.publish(draft.rulesetId(), 1, sandbox).orElseThrow().versionId();
    }

    private VersionView draft(UUID sandbox, String tenthParagraph) {
        PolicyView policy = policies.create(sandbox, "Lending", PolicyLanguage.HE,
                Fixtures.lendingPolicyText().strip() + "\n\n" + tenthParagraph);
        UUID policyVersion = policies.version(policy.id(), 1, sandbox).orElseThrow().id();
        return rulesets.createDraft(sandbox, policyVersion, Fixtures.lendingV1(), ValidationContext.ANALYST_EDIT,
                Set.of());
    }

    private @Nullable String status(UUID version) {
        return jdbc.sql("select embedding_status from ruleset_version where id = :id").param("id", version)
                .query(String.class).optional().orElse(null);
    }

    private void setStatus(UUID version, String status) {
        jdbc.sql("update ruleset_version set embedding_status = :s where id = :id")
                .param("s", status).param("id", version).update();
    }

    private List<String> chunkIds(UUID version) {
        return jdbc.sql("select kind || ':' || ref_id from chunk where ruleset_version_id = :v")
                .param("v", version).query(String.class).list();
    }

    /** Waits for the asynchronous job, polling the status the API reads; fails after {@link #PATIENCE}. */
    private void awaitStatus(UUID version, String expected) {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        while (!expected.equals(status(version))) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("version " + version + " is " + status(version) + ", not " + expected);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
