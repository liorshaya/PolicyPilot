package com.liorshaya.policypilot.rag.service;

import com.liorshaya.policypilot.ai.EmbeddingGateway;
import com.liorshaya.policypilot.rag.repository.ChunkRepository;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Document 2, Storage: "the dimension is part of the profile and checked at startup". A gateway whose vectors the
 * {@code chunk} column cannot hold stops the application before it embeds anything, instead of failing on every
 * publish; switching profiles on an existing database needs a re-embed, which the README documents.
 */
@Component
public class EmbeddingDimensionCheck implements SmartInitializingSingleton {

    private final ChunkRepository chunks;
    private final EmbeddingGateway gateway;

    public EmbeddingDimensionCheck(ChunkRepository chunks, EmbeddingGateway gateway) {
        this.chunks = chunks;
        this.gateway = gateway;
    }

    @Override
    public void afterSingletonsInstantiated() {
        verify(chunks.embeddingDimension(), gateway.dimension());
    }

    /** Refuses to start when the column and the gateway disagree, naming both. */
    public static void verify(int column, int gateway) {
        if (column != gateway) {
            throw new IllegalStateException("chunk.embedding is vector(" + column + ") but the embedding gateway of the"
                    + " active profile returns " + gateway + " dimensions; re-embed the database for this profile");
        }
    }
}
