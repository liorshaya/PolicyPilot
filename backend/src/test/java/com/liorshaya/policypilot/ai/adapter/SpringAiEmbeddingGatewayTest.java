package com.liorshaya.policypilot.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.ai.LlmUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * The embedding half of the adapter (Document 2, AI Layer Design, {@code EmbeddingGateway}): the provider's
 * vectors in the order of the texts, every one of the profile's dimension, and a provider failure as the named
 * failure the rest of the system knows. The provider is a hand-written {@link EmbeddingModel}.
 */
class SpringAiEmbeddingGatewayTest {

    private static final int DIMENSION = 3;

    // Spring AI's EmbeddingResponse carries an index per result, and the provider need not keep the input order.
    // Expected: the vector of each text in the position of that text
    @Test
    void embedAllReturnsTheVectorsInTheOrderOfTheTexts() {
        SpringAiEmbeddingGateway gateway = gateway(request -> new EmbeddingResponse(List.of(
                new Embedding(new float[] {0f, 1f, 0f}, 1),
                new Embedding(new float[] {1f, 0f, 0f}, 0))));

        List<float[]> vectors = gateway.embedAll(List.of("first", "second"));

        assertThat(vectors).containsExactly(new float[] {1f, 0f, 0f}, new float[] {0f, 1f, 0f});
    }

    // Document 2: the dimension is part of the profile. Expected: a vector of another size is a provider error, never
    // a row the column would refuse later
    @Test
    void aVectorOfAnotherDimensionIsAProviderError() {
        SpringAiEmbeddingGateway gateway = gateway(request -> new EmbeddingResponse(List.of(
                new Embedding(new float[] {1f, 0f}, 0))));

        assertThatThrownBy(() -> gateway.embed("text"))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("2")
                .hasMessageContaining("3")
                .extracting(e -> ((LlmUnavailableException) e).reason())
                .isEqualTo(LlmUnavailableException.Reason.PROVIDER_ERROR);
    }

    // Expected: a provider that answers fewer vectors than it was asked for is an error, not a shorter list
    @Test
    void aMissingVectorIsAProviderError() {
        SpringAiEmbeddingGateway gateway = gateway(request -> new EmbeddingResponse(List.of(
                new Embedding(new float[] {1f, 0f, 0f}, 0))));

        assertThatThrownBy(() -> gateway.embedAll(List.of("first", "second")))
                .isInstanceOf(LlmUnavailableException.class);
    }

    // Document 4, Guardrails: a provider that does not answer is a defined failure. Expected: PROVIDER_ERROR with the
    // provider's exception as its cause
    @Test
    void aProviderFailureIsAProviderError() {
        IllegalStateException failure = new IllegalStateException("429 from the provider");
        SpringAiEmbeddingGateway gateway = gateway(request -> {
            throw failure;
        });

        assertThatThrownBy(() -> gateway.embed("text"))
                .isInstanceOf(LlmUnavailableException.class)
                .hasCause(failure);
    }

    // Expected: the texts reach the provider as they were given, in one request
    @Test
    void theTextsReachTheProviderInOneRequest() {
        List<List<String>> requests = new ArrayList<>();
        SpringAiEmbeddingGateway gateway = gateway(request -> {
            requests.add(request.getInstructions());
            return new EmbeddingResponse(List.of(new Embedding(new float[] {1f, 0f, 0f}, 0),
                    new Embedding(new float[] {0f, 1f, 0f}, 1)));
        });

        gateway.embedAll(List.of("תקופת ההחזר", "R-320"));

        assertThat(requests).containsExactly(List.of("תקופת ההחזר", "R-320"));
    }

    // Expected: the dimension the gateway reports is the profile's, which the startup check compares with the column
    @Test
    void theDimensionIsTheProfiles() {
        assertThat(gateway(request -> new EmbeddingResponse(List.of())).dimension()).isEqualTo(DIMENSION);
    }

    private static SpringAiEmbeddingGateway gateway(Function<EmbeddingRequest, EmbeddingResponse> provider) {
        return new SpringAiEmbeddingGateway(new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                return provider.apply(request);
            }

            @Override
            public float[] embed(Document document) {
                throw new UnsupportedOperationException("the gateway embeds texts");
            }
        }, DIMENSION);
    }
}
