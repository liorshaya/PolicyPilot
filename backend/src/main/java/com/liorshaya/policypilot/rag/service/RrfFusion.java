package com.liorshaya.policypilot.rag.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Reciprocal rank fusion (Document 4, Retrieval Pipeline, Fusion): a chunk scores the sum of {@code 1 / (60 + rank)}
 * over the lists it is in, ranks from 1; ties go to the better vector rank, then to the chunk id. The top {@code k}
 * are kept, and every chunk in {@code kept} is among them, taking the place of the lowest-ranked chunk that is not
 * itself kept; one neither list found scores 0.
 */
public final class RrfFusion {

    /** Document 4: {@code k = 60}. */
    public static final int K = 60;

    private RrfFusion() {}

    /** One fused chunk: its score and its rank in each list, null where the list did not find it. */
    public record Fused(String id, double score, @Nullable Integer vectorRank, @Nullable Integer lexicalRank) {}

    public static List<Fused> fuse(List<String> vector, List<String> lexical, Set<String> kept, int top) {
        Map<String, Integer> vectorRanks = ranks(vector);
        Map<String, Integer> lexicalRanks = ranks(lexical);
        Set<String> ids = new LinkedHashSet<>(vector);
        ids.addAll(lexical);
        ids.addAll(kept);
        List<Fused> ordered = ids.stream()
                .map(id -> new Fused(id, score(vectorRanks.get(id)) + score(lexicalRanks.get(id)), vectorRanks.get(id),
                        lexicalRanks.get(id)))
                .sorted(Comparator.comparingDouble(Fused::score).reversed()
                        .thenComparingInt(fused -> fused.vectorRank() == null ? Integer.MAX_VALUE : fused.vectorRank())
                        .thenComparing(Fused::id))
                .toList();
        List<Fused> chosen = new ArrayList<>(ordered.stream().filter(fused -> kept.contains(fused.id())).toList());
        for (Fused fused : ordered) {
            if (chosen.size() >= top) {
                break;
            }
            if (!kept.contains(fused.id())) {
                chosen.add(fused);
            }
        }
        // the kept chunks were set aside first; the result reads in fused order
        return chosen.stream().sorted(Comparator.comparingInt(ordered::indexOf)).toList();
    }

    private static double score(@Nullable Integer rank) {
        return rank == null ? 0 : 1.0 / (K + rank);
    }

    private static Map<String, Integer> ranks(List<String> list) {
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            ranks.putIfAbsent(list.get(i), i + 1);
        }
        return ranks;
    }
}
