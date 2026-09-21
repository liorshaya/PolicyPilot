package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.rag.service.Retrieval;
import com.liorshaya.policypilot.rag.service.RetrievalService;
import com.liorshaya.policypilot.rag.service.RetrievedChunk;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.FakeEmbeddingGateway;
import com.liorshaya.policypilot.support.Requirement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Hybrid retrieval on a fixed corpus (Document 4, Retrieval Pipeline; Document 6, Integration: "hybrid retrieval
 * ranking on a fixed corpus"): the lending version and a tenth paragraph of the test's own, in PostgreSQL with
 * pgvector. The fake gateway gives every text the first unit axis unless the test chose another, so a cosine is 1 on
 * the same axis and 0 on another, and each expectation below follows by hand.
 */
@Requirement({"FR-13", "FR-15"})
class HybridRetrievalIT extends ApiIntegrationTest {

    @Autowired
    private PolicyService policies;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private RetrievalService retrieval;

    @Autowired
    private FakeEmbeddingGateway gateway;

    @Autowired
    private JdbcClient jdbc;

    private RagFixtures fixtures;

    @BeforeEach
    void fixtures() {
        fixtures = new RagFixtures(policies, rulesets, jdbc);
    }

    // Document 4, Query and Fusion: the question and the tenth paragraph share axis 3, every other chunk is on axis 0,
    // and no word of the question is in the corpus, so only the vector list speaks. Expected: p:10 first with cosine 1,
    // covered, and the paragraph cited
    @Test
    void theQuestionRetrievesTheParagraphItsVectorPointsAt() {
        String paragraph = "פסקה " + UUID.randomUUID();
        String question = "שאלה" + Long.toHexString(System.nanoTime());
        gateway.register(paragraph, gateway.axis(3)).register(question, gateway.axis(3));
        RagFixtures.Published version = fixtures.ready(paragraph);

        Retrieval result = retrieve(version, question);

        assertThat(result.covered()).isTrue();
        assertThat(result.bestCosine()).isCloseTo(1.0, within(1e-6));
        assertThat(result.chunks().getFirst().id()).isEqualTo("p:10");
        assertThat(result.chunks()).hasSize(8);
        assertThat(result.citations().getFirst().id()).isEqualTo("p:10");
    }

    // Document 4, Lexical index and Query: a question's words joined by OR, so one word the passage shares is enough;
    // with AND, the other words would match nothing. Expected: p:10, the only chunk holding the word, first in the
    // lexical list
    @Test
    void theLexicalQueryMatchesAnyWordOfTheQuestion() {
        String word = "קולמוס" + Long.toHexString(System.nanoTime());
        String question = word + " ועוד מילים שאינן במסמך";
        RagFixtures.Published version = fixtures.ready("פסקה על " + word);

        Retrieval result = retrieve(version, question);

        assertThat(result.chunks()).filteredOn(chunk -> chunk.id().equals("p:10"))
                .extracting(RetrievedChunk::lexicalRank).containsExactly(1);
    }

    // Document 4, Query: numbers count twice. The question holds a word only p:10 has and the number 84, which p:2
    // (the loan term) has. ts_rank without length normalization gives each single match the same value, so p:2 scores
    // twice p:10; without the second query the two would tie and the chunk id would put p:10 first. Expected: p:2
    // ranked above p:10 in the lexical list
    @Test
    void aNumberInTheQuestionCountsTwice() {
        String word = "זית" + Long.toHexString(System.nanoTime());
        RagFixtures.Published version = fixtures.ready(word);

        Retrieval result = retrieve(version, word + " 84");

        assertThat(lexicalRank(result, "p:2")).isLessThan(lexicalRank(result, "p:10"));
    }

    // Document 4, Fusion: the rule chunk of a rule id named in the question is always kept; the lexical index finds it
    // as r320. Expected: r:R-320 retrieved, first in the lexical list, and covered although no cosine reaches 0.35
    @Test
    void aQuestionNamingARuleIdRetrievesThatRule() {
        String question = "מה עושה הכלל R-320? " + UUID.randomUUID();
        gateway.register(question, gateway.axis(9));
        RagFixtures.Published version = fixtures.ready("פסקה " + UUID.randomUUID());

        Retrieval result = retrieve(version, question);

        assertThat(result.covered()).isTrue();
        assertThat(result.chunks()).filteredOn(chunk -> chunk.id().equals("r:R-320"))
                .extracting(RetrievedChunk::lexicalRank).containsExactly(1);
    }

    // Document 4, Fusion: "always including the rule chunk for any rule id mentioned verbatim in the question", even
    // when more rules are named than the top 8 holds. Expected: all nine named rules returned
    @Test
    void everyNamedRuleIsReturnedEvenPastTheTopEight() {
        String question = "R-010 R-020 R-100 R-110 R-115 R-116 R-120 R-130 R-140 " + UUID.randomUUID();
        gateway.register(question, gateway.axis(9));
        RagFixtures.Published version = fixtures.ready("פסקה " + UUID.randomUUID());

        Retrieval result = retrieve(version, question);

        assertThat(result.chunks()).extracting(RetrievedChunk::id).containsExactlyInAnyOrder("r:R-010", "r:R-020",
                "r:R-100", "r:R-110", "r:R-115", "r:R-116", "r:R-120", "r:R-130", "r:R-140");
    }

    // Document 4, Threshold: cosine 0 everywhere and nothing named. Expected: not covered, no chunk, and the Hebrew
    // sentence, because the lending rule set is Hebrew
    @Test
    void anOffCorpusQuestionIsNotCovered() {
        String question = "האם יש הנחה לחיילים משוחררים? " + UUID.randomUUID();
        gateway.register(question, gateway.axis(9));
        RagFixtures.Published version = fixtures.ready("פסקה " + UUID.randomUUID());

        Retrieval result = retrieve(version, question);

        assertThat(result.covered()).isFalse();
        assertThat(result.chunks()).isEmpty();
        assertThat(result.notCovered())
                .isEqualTo("המסמכים אינם עוסקים בשאלה הזו; אפשר לשאול על כלל, על סעיף או על מספר בקשה.");
    }

    // Document 4, Scoping: "the chat never mixes versions or sandboxes". Two versions whose tenth paragraphs share the
    // question's axis. Expected: retrieving on the first never returns the second's paragraph
    @Test
    void retrievalStaysInsideItsVersion() {
        String mine = "הפסקה שלי " + UUID.randomUUID();
        String theirs = "הפסקה שלהם " + UUID.randomUUID();
        String question = "שאלה " + UUID.randomUUID();
        gateway.register(mine, gateway.axis(5)).register(theirs, gateway.axis(5)).register(question, gateway.axis(5));
        RagFixtures.Published version = fixtures.ready(mine);
        fixtures.ready(theirs);

        Retrieval result = retrieve(version, question);

        assertThat(result.chunks()).extracting(RetrievedChunk::text).contains(mine).doesNotContain(theirs);
    }

    // Document 5, Authorization (sandbox): a version is read only by its sandbox or, when protected, by every one.
    // Expected: another sandbox gets nothing, as if the version did not exist; the seeded version answers any sandbox
    @Test
    void aVersionIsRetrievableOnlyWhereItIsVisible() {
        RagFixtures.Published version = fixtures.ready("פסקה " + UUID.randomUUID());
        UUID seeded = rulesets.protectedRulesets().getFirst().id();
        fixtures.awaitStatus(rulesets.version(seeded, 1, UUID.randomUUID()).orElseThrow().versionId(), "READY");

        assertThat(retrieval.retrieve(version.rulesetId(), 1, UUID.randomUUID(), "שאלה")).isEmpty();
        assertThat(retrieval.retrieve(seeded, 1, UUID.randomUUID(), "שאלה")).isPresent();
    }

    // Document 4, Embedding: questions on a version that is not READY are refused; a DRAFT has no corpus at all.
    // Expected: a status conflict for both
    @Test
    void aVersionThatIsNotReadyIsRefused() {
        String key = UUID.randomUUID().toString();
        RagFixtures.Published embedding = fixtures.publish("פסקה " + FakeEmbeddingGateway.WAIT + key);
        fixtures.awaitStatus(embedding.versionId(), "EMBEDDING");
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox, "טיוטה " + UUID.randomUUID());

        try {
            assertThatThrownBy(() -> retrieval.retrieve(embedding.rulesetId(), 1, embedding.sandboxId(), "שאלה"))
                    .isInstanceOf(VersionStatusException.class);
            assertThatThrownBy(() -> retrieval.retrieve(draft.rulesetId(), 1, sandbox, "שאלה"))
                    .isInstanceOf(VersionStatusException.class);
        } finally {
            gateway.release(key);
        }
    }

    // Document 5, SQL injection: "a question containing '; DROP TABLE decision; --' returns a normal not-covered
    // answer". Expected: not covered, and the decision table still answers
    @Test
    void anSqlInjectionQuestionGetsAnOrdinaryNotCoveredAnswer() {
        String question = "'; DROP TABLE decision; --";
        gateway.register(question, gateway.axis(9));
        RagFixtures.Published version = fixtures.ready("פסקה " + UUID.randomUUID());

        Retrieval result = retrieve(version, question);

        assertThat(result.covered()).isFalse();
        assertThat(jdbc.sql("select count(*) from decision").query(Long.class).single()).isNotNegative();
    }

    private static int lexicalRank(Retrieval result, String id) {
        return result.chunks().stream().filter(chunk -> chunk.id().equals(id)).findFirst()
                .map(RetrievedChunk::lexicalRank).orElseThrow(() -> new AssertionError(id + " was not retrieved"));
    }

    private Retrieval retrieve(RagFixtures.Published version, String question) {
        return retrieval.retrieve(version.rulesetId(), 1, version.sandboxId(), question).orElseThrow();
    }
}
