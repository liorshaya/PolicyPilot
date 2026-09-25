package com.liorshaya.policypilot.ai;

/**
 * The model provider the active profile runs on, as {@code GET /system/provider} shows it in the UI header (Brief
 * FR-21; Document 2, API Surface): the provider's name, the model each role asks, and the embedding model with its
 * dimension. Names only, never a key or an address (Document 2, Security and Demo Protections).
 */
public record ProviderDescription(String provider, String strongModel, String fastModel, String embeddingModel,
        int embeddingDimension) {}
