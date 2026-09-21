package com.liorshaya.policypilot.ai.adapter;

import com.liorshaya.policypilot.ai.EmbeddingGateway;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import java.util.Comparator;
import java.util.List;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The embedding half of the provider boundary (Document 2, AI Layer Design, {@code EmbeddingGateway}): the profile's
 * {@link EmbeddingModel}, {@code text-embedding-3-small} on OpenAI or {@code bge-m3} on Ollama, asked once per batch.
 * Every vector is the profile's dimension and in the order of the texts, or the call is a provider error: a vector
 * the {@code chunk} column cannot hold must never get as far as the database.
 */
@Component
public class SpringAiEmbeddingGateway implements EmbeddingGateway {

    private final EmbeddingModel model;
    private final int dimension;

    @Autowired
    public SpringAiEmbeddingGateway(EmbeddingModel model, PolicyPilotProperties properties) {
        this(model, properties.embedding().dimension());
    }

    SpringAiEmbeddingGateway(EmbeddingModel model, int dimension) {
        this.model = model;
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        EmbeddingResponse response;
        try {
            response = model.call(new EmbeddingRequest(texts, null));
        } catch (RuntimeException e) {
            throw new LlmUnavailableException(LlmUnavailableException.Reason.PROVIDER_ERROR,
                    "the embedding provider did not answer", e);
        }
        List<Embedding> results = response.getResults().stream()
                .sorted(Comparator.comparing(Embedding::getIndex))
                .toList();
        if (results.size() != texts.size()) {
            throw new LlmUnavailableException(LlmUnavailableException.Reason.PROVIDER_ERROR,
                    "the embedding provider returned " + results.size() + " vectors for " + texts.size() + " texts");
        }
        for (Embedding result : results) {
            if (result.getOutput().length != dimension) {
                throw new LlmUnavailableException(LlmUnavailableException.Reason.PROVIDER_ERROR,
                        "the embedding provider returned " + result.getOutput().length + " dimensions, the profile has "
                                + dimension);
            }
        }
        return results.stream().map(Embedding::getOutput).toList();
    }

    @Override
    public int dimension() {
        return dimension;
    }
}
