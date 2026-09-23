package com.liorshaya.policypilot.rag.service;

import com.liorshaya.policypilot.ai.EmbeddingGateway;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.rag.repository.ChunkRepository;
import com.liorshaya.policypilot.rag.repository.ScoredChunk;
import com.liorshaya.policypilot.ruleset.service.EmbeddingSource;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Hybrid retrieval (Document 4, Retrieval Pipeline): the question embedded as it is, the vector top 20 and the lexical
 * top 20 of one version, fused with RRF, the top 8 kept with every rule the question names, and citations checked
 * against the version. A question below the Threshold gets the fixed sentence of the rule set's language instead of
 * chunks, and nothing here can call a model. The version is resolved through {@code ruleset}, which is where the
 * sandbox is checked, so a query never names a version its caller cannot see.
 */
@Service
public class RetrievalService {

    private static final Logger LOG = LoggerFactory.getLogger(RetrievalService.class);
    /** Document 4, Fusion: "the vector top 20 and the lexical top 20". */
    static final int CANDIDATES = 20;

    private final RulesetService rulesets;
    private final ChunkRepository chunks;
    private final EmbeddingGateway gateway;
    private final MeterRegistry meters;
    private final NotCoveredThreshold threshold;
    private final int topK;
    private final NotCoveredSentences sentences = NotCoveredSentences.load();

    public RetrievalService(RulesetService rulesets, ChunkRepository chunks, EmbeddingGateway gateway,
            PolicyPilotProperties properties, MeterRegistry meters) {
        this.rulesets = rulesets;
        this.chunks = chunks;
        this.gateway = gateway;
        this.meters = meters;
        this.threshold = new NotCoveredThreshold(properties.rag().minScore());
        this.topK = properties.rag().topK();
    }

    /**
     * Retrieves for a question on a version; empty when the sandbox cannot see the version, and a status conflict
     * when the version's embedding is not {@code READY}.
     */
    public Optional<Retrieval> retrieve(UUID rulesetId, int versionNo, UUID sandboxId, String question) {
        return rulesets.corpus(rulesetId, versionNo, sandboxId).map(corpus -> retrieve(corpus, question));
    }

    /**
     * The ids of the version's rules nearest a text, nearest first (Document 4, Prompt 5, Candidate selection): the
     * seeds of a change request's candidates. The caller has the corpus, so the sandbox has already been checked.
     */
    public List<String> rulesNearest(EmbeddingSource corpus, String text, int limit) {
        return chunks.rulesNearest(corpus.versionId(), gateway.embed(text), limit).stream().map(ScoredChunk::refId)
                .toList();
    }

    private Retrieval retrieve(EmbeddingSource corpus, String question) {
        UUID version = corpus.versionId();
        QuestionSignals signals = QuestionSignals.of(question, corpus.ruleSet());
        List<ScoredChunk> vector = chunks.vectorTop(version, gateway.embed(question), CANDIDATES);
        double bestCosine = vector.isEmpty() ? 0 : vector.getFirst().score();
        if (!threshold.covers(bestCosine, signals)) {
            meters.counter("policypilot.rag.not_covered").increment();
            LOG.atInfo().setMessage("rag.not_covered").addKeyValue("version", version)
                    .addKeyValue("bestCosine", bestCosine).log();
            return new Retrieval(false, bestCosine, List.of(), List.of(), sentences.of(corpus.ruleSet().language()));
        }
        List<ScoredChunk> lexical = chunks.lexicalTop(version, LexicalText.normalize(question), signals.strongTerms(),
                CANDIDATES);
        Map<String, ScoredChunk> found = new HashMap<>();
        lexical.forEach(chunk -> found.put(id(chunk), chunk));
        vector.forEach(chunk -> found.put(id(chunk), chunk));
        Map<String, Double> cosines = vector.stream()
                .collect(Collectors.toMap(RetrievalService::id, ScoredChunk::score));
        Set<String> named = signals.ruleIds().stream().map(ruleId -> Chunk.Kind.RULE.prefix() + ":" + ruleId)
                .collect(Collectors.toSet());
        List<RetrievedChunk> kept = RrfFusion.fuse(ids(vector), ids(lexical), named, topK).stream()
                .flatMap(fused -> Optional.ofNullable(found.get(fused.id()))
                        .or(() -> chunks.find(version, Chunk.Kind.RULE.name(), fused.id().substring(2)))
                        .map(chunk -> new RetrievedChunk(fused.id(), Chunk.Kind.valueOf(chunk.kind()), chunk.refId(),
                                chunk.text(), fused.score(), cosines.get(fused.id()), fused.vectorRank(),
                                fused.lexicalRank()))
                        .stream())
                .toList();
        List<Citation> citations = new CitationBuilder(corpus.ruleSet(), corpus.paragraphs())
                .cite(kept.stream().map(RetrievedChunk::id).toList());
        return new Retrieval(true, bestCosine, kept, citations, null);
    }

    private static List<String> ids(List<ScoredChunk> chunks) {
        return chunks.stream().map(RetrievalService::id).toList();
    }

    private static String id(ScoredChunk chunk) {
        return Chunk.Kind.valueOf(chunk.kind()).prefix() + ":" + chunk.refId();
    }
}
