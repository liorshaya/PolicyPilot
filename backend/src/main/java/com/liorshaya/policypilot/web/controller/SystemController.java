package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.ai.ProviderDescription;
import com.liorshaya.policypilot.web.response.ProviderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/system/provider} (Brief FR-21; Document 2, API Surface): the provider the active profile runs on,
 * the model each role asks and the embedding model with its dimension, for the UI header. It calls no model and reads
 * nothing of the sandbox; like every {@code /api/**} route it needs the session cookie (Document 5).
 */
@RestController
public class SystemController {

    private final ProviderDescription provider;

    public SystemController(ProviderDescription provider) {
        this.provider = provider;
    }

    @Operation(summary = "The active model provider, its models and the embedding dimension")
    @ApiResponse(responseCode = "200", description = "The names the active profile sets, never a key or an address",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProviderResponse.class)))
    @GetMapping(ApiPaths.SYSTEM_PROVIDER)
    public ProviderResponse provider() {
        return ProviderResponse.of(provider);
    }
}
