package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.liorshaya.policypilot.rag.service.RrfFusion;
import com.liorshaya.policypilot.rag.service.RrfFusion.Fused;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Reciprocal rank fusion as Document 4 defines it (Retrieval Pipeline, Fusion): a chunk scores the sum of
 * {@code 1 / (60 + rank)} over the lists it is in, ranks from 1; ties go to the better vector rank, then to the chunk
 * id; the top 8 are kept, and a rule the question names takes the place of the lowest-ranked chunk that is not
 * itself such a rule. Every expected score below is the fraction worked out by hand.
 */
class RrfFusionTest {

    // Document 4, Fusion, worked by hand. Vector: a b c d. Lexical: c a e.
    //   a = 1/61 + 1/62 = 0.032522...   c = 1/63 + 1/61 = 0.032266...   b = 1/62   e = 1/63   d = 1/64
    @Test
    void fusesTwoListsAsTheHandComputedExampleOrdersThem() {
        List<Fused> fused = RrfFusion.fuse(List.of("a", "b", "c", "d"), List.of("c", "a", "e"), Set.of(), 8);

        assertThat(fused).extracting(Fused::id).containsExactly("a", "c", "b", "e", "d");
        assertThat(fused.get(0).score()).isCloseTo(1.0 / 61 + 1.0 / 62, within(1e-12));
        assertThat(fused.get(1).score()).isCloseTo(1.0 / 63 + 1.0 / 61, within(1e-12));
        assertThat(fused.get(2).score()).isCloseTo(1.0 / 62, within(1e-12));
        assertThat(fused.get(3).score()).isCloseTo(1.0 / 63, within(1e-12));
        assertThat(fused.get(4).score()).isCloseTo(1.0 / 64, within(1e-12));
    }

    // Document 4: "ties go to the better vector rank". Vector: y x. Lexical: x y. Both score 1/61 + 1/62, and the ids
    // alone would put x first. Expected: y, which the vector list ranked first
    @Test
    void anEqualScoreGoesToTheBetterVectorRank() {
        List<Fused> fused = RrfFusion.fuse(List.of("y", "x"), List.of("x", "y"), Set.of(), 8);

        assertThat(fused).extracting(Fused::id).containsExactly("y", "x");
    }

    // Document 4: ties go to the better vector rank, "then to the chunk id". Vector: q. Lexical: p. Both score 1/61,
    // and p has no vector rank, which is worse than any, although its id comes first. Two named rules neither list
    // found both score 0 and have no vector rank. Expected: q before p, and r:R-100 before r:R-900
    @Test
    void aVectorRankBeatsNoneAndThenTheChunkIdDecides() {
        assertThat(RrfFusion.fuse(List.of("q"), List.of("p"), Set.of(), 8)).extracting(Fused::id)
                .containsExactly("q", "p");
        assertThat(RrfFusion.fuse(List.of(), List.of(), Set.of("r:R-900", "r:R-100"), 8)).extracting(Fused::id)
                .containsExactly("r:R-100", "r:R-900");
    }

    // Document 4: "the top 8 fused chunks are kept". Expected: the first 8 of 12 vector-only chunks, in order
    @Test
    void keepsTheTopEight() {
        List<String> twelve = IntStream.rangeClosed(1, 12).mapToObj(i -> "c" + (char) ('a' + i)).toList();

        assertThat(RrfFusion.fuse(twelve, List.of(), Set.of(), 8)).extracting(Fused::id)
                .containsExactlyElementsOf(twelve.subList(0, 8));
    }

    // Document 4: a named rule "takes the place of the lowest-ranked chunk that is not itself such a rule". The rule is
    // tenth in the vector list. Expected: the first seven, then the rule, which scores 1/70
    @Test
    void aNamedRuleOutsideTheTopEightTakesThePlaceOfTheLowestRankedChunk() {
        List<String> vector = List.of("p:1", "p:2", "p:3", "p:4", "p:5", "p:6", "p:7", "p:8", "p:9", "r:R-320");

        List<Fused> fused = RrfFusion.fuse(vector, List.of(), Set.of("r:R-320"), 8);

        assertThat(fused).extracting(Fused::id)
                .containsExactly("p:1", "p:2", "p:3", "p:4", "p:5", "p:6", "p:7", "r:R-320");
        assertThat(fused.getLast().score()).isCloseTo(1.0 / 70, within(1e-12));
    }

    // Document 4: always including the rule chunk, even one neither list found. Expected: kept last, scoring 0
    @Test
    void aNamedRuleNeitherListFoundIsStillKept() {
        List<Fused> fused = RrfFusion.fuse(List.of("p:1", "p:2"), List.of("p:2"), Set.of("r:R-900"), 8);

        assertThat(fused).extracting(Fused::id).containsExactly("p:2", "p:1", "r:R-900");
        assertThat(fused.getLast().score()).isZero();
    }

    // Expected: a named rule already inside the top 8 changes nothing
    @Test
    void aNamedRuleAlreadyKeptChangesNothing() {
        List<Fused> fused = RrfFusion.fuse(List.of("r:R-320", "p:1"), List.of(), Set.of("r:R-320"), 8);

        assertThat(fused).extracting(Fused::id).containsExactly("r:R-320", "p:1");
    }
}
