package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.ai.ProviderDescription;
import com.liorshaya.policypilot.ai.TokenBudget;
import com.liorshaya.policypilot.web.response.BudgetResponse;
import com.liorshaya.policypilot.web.response.ProviderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/system/provider} (Brief FR-21; Document 2, API Surface): the provider the active profile runs on,
 * the model each role asks and the embedding model with its dimension, for the UI header. {@code GET
 * /api/v1/system/budget} (Document 2, added 2026-09-28 for Register phase 4): whether the day's token budget is spent
 * and when it resumes, the banner of Document 5. Neither calls a model or reads anything of the sandbox; like every
 * {@code /api/**} route they need the session cookie (Document 5).
 */
@RestController
public class SystemController {

    private final ProviderDescription provider;
    private final TokenBudget budget;

    public SystemController(ProviderDescription provider, TokenBudget budget) {
        this.provider = provider;
        this.budget = budget;
    }

    @Operation(summary = "The active model provider, its models and the embedding dimension")
    @ApiResponse(responseCode = "200", description = "The names the active profile sets, never a key or an address",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProviderResponse.class)))
    @GetMapping(ApiPaths.SYSTEM_PROVIDER)
    public ProviderResponse provider() {
        return ProviderResponse.of(provider);
    }

    @Operation(summary = "Whether the day's token budget is spent, and when it resumes")
    @ApiResponse(responseCode = "200", description = "Whether today's ledger has stopped, and the next midnight UTC",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = BudgetResponse.class)))
    @GetMapping(ApiPaths.SYSTEM_BUDGET)
    public BudgetResponse budget() {
        return BudgetResponse.of(budget);
    }
}
