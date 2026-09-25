package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.ai.ProviderDescription;

/**
 * What {@code GET /system/provider} returns (Document 2, API Surface): the names the active profile sets, shown in the
 * UI header. Names only, never a key or an address.
 */
public record ProviderResponse(@JsonProperty(required = true) String provider,
        @JsonProperty(required = true) ChatModels chatModels,
        @JsonProperty(required = true) String embeddingModel,
        @JsonProperty(required = true) int embeddingDimension) {

    /** The model each role asks: the strong one authors, reviews and changes; the fast one explains and answers. */
    public record ChatModels(@JsonProperty(required = true) String strong,
            @JsonProperty(required = true) String fast) {}

    public static ProviderResponse of(ProviderDescription provider) {
        return new ProviderResponse(provider.provider(),
                new ChatModels(provider.strongModel(), provider.fastModel()),
                provider.embeddingModel(), provider.embeddingDimension());
    }
}
