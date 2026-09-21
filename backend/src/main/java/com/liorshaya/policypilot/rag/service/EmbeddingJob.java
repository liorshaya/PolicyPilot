package com.liorshaya.policypilot.rag.service;

import com.liorshaya.policypilot.ai.EmbeddingGateway;
import com.liorshaya.policypilot.rag.repository.ChunkRepository;
import com.liorshaya.policypilot.rag.repository.ChunkRow;
import com.liorshaya.policypilot.ruleset.service.EmbeddingSource;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionPublished;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The embedding job on publish (Document 2, RAG pipeline, Embedding): a version announced as published after its
 * commit is chunked, embedded in one gateway call and stored, off the request thread, and its status moves
 * {@code PENDING}, {@code EMBEDDING}, then {@code READY} or {@code FAILED}. At startup the versions left to embed are
 * taken again, which is also how the seeded version 1 and a version published before V7 are embedded. The work waits
 * on a provider, not on a CPU, so each version gets a virtual thread of the job's own; stopping the application
 * interrupts them, which ends their runs {@code FAILED}, and the next start takes those again.
 */
@Service
public class EmbeddingJob {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingJob.class);

    private final RulesetService rulesets;
    private final ChunkRepository chunks;
    private final EmbeddingGateway gateway;
    private final Clock clock;
    private final Chunker chunker = new Chunker();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public EmbeddingJob(RulesetService rulesets, ChunkRepository chunks, EmbeddingGateway gateway, Clock clock) {
        this.rulesets = rulesets;
        this.chunks = chunks;
        this.gateway = gateway;
        this.clock = clock;
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    @TransactionalEventListener
    public void onPublished(VersionPublished published) {
        executor.execute(() -> embed(published.versionId()));
    }

    /** Takes every version left to embed again, each on its own task. */
    @EventListener(ApplicationReadyEvent.class)
    public void resume() {
        rulesets.resumeEmbeddings().forEach(versionId -> executor.execute(() -> embed(versionId)));
    }

    /**
     * Embeds one version now, if it is {@code PENDING}; anything else, a DRAFT included, is left as it is. A provider
     * failure leaves the version {@code FAILED} with no chunks, because the chunks are written only after every vector
     * has come back.
     */
    public void embed(UUID versionId) {
        Optional<EmbeddingSource> claimed = rulesets.startEmbedding(versionId);
        if (claimed.isEmpty()) {
            return;
        }
        EmbeddingSource source = claimed.get();
        try {
            List<Chunk> corpus = chunker.chunk(source.paragraphs(), source.ruleSet());
            List<float[]> vectors = gateway.embedAll(corpus.stream().map(Chunk::text).toList());
            chunks.replace(versionId, rows(corpus, vectors), clock.instant());
            rulesets.finishEmbedding(versionId, true);
            LOG.atInfo().setMessage("rag.embedded").addKeyValue("version", versionId)
                    .addKeyValue("chunks", corpus.size()).log();
        } catch (RuntimeException e) {
            rulesets.finishEmbedding(versionId, false);
            LOG.atWarn().setMessage("rag.embedding.failed").addKeyValue("version", versionId)
                    .addKeyValue("error", e.getClass().getSimpleName()).setCause(e).log();
        }
    }

    private static List<ChunkRow> rows(List<Chunk> corpus, List<float[]> vectors) {
        List<ChunkRow> rows = new ArrayList<>(corpus.size());
        for (int i = 0; i < corpus.size(); i++) {
            Chunk chunk = corpus.get(i);
            rows.add(new ChunkRow(chunk.kind().name(), chunk.refId(), chunk.text(), LexicalText.normalize(chunk.text()),
                    vectors.get(i)));
        }
        return rows;
    }
}
