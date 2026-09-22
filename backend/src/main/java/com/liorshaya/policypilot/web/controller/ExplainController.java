package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.ai.LlmMalformedOutputException;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.service.ExplainService;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.decision.service.DecisionView;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorEnvelope;
import com.liorshaya.policypilot.web.request.ExplainRequest;
import com.liorshaya.policypilot.web.response.ExplanationResponse;
import com.liorshaya.policypilot.web.security.SandboxSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/decisions/{id}/explain} (Document 2, API Surface; Brief FR-11): a stored decision of the
 * caller's sandbox, explained from its trace alone for an officer or an applicant (Document 4, Prompt 3). The model
 * sees the engine's decision object without its row id, so the same trace shares one cached explanation.
 */
@RestController
public class ExplainController {

    private static final Logger log = LoggerFactory.getLogger(ExplainController.class);

    private final DecisionService decisions;
    private final RulesetService rulesets;
    private final ExplainService explainer;

    public ExplainController(DecisionService decisions, RulesetService rulesets, ExplainService explainer) {
        this.decisions = decisions;
        this.rulesets = rulesets;
        this.explainer = explainer;
    }

    @Operation(summary = "Explain a stored decision from its trace, for an officer or an applicant")
    @ApiResponse(responseCode = "200", description = "The explanation, every entry checked against the trace",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ExplanationResponse.class)))
    @ApiResponse(responseCode = "400", description = "The audience is not officer or applicant",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "404", description = "No such decision in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "503", description = "The model provider failed, or its answer was not an explanation",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(ApiPaths.DECISION_EXPLAIN)
    public ExplanationResponse explain(@PathVariable UUID id,
            @RequestBody(required = false) @Nullable ExplainRequest body,
            @AuthenticationPrincipal SandboxSession session) {
        ExplainService.Audience audience = (body == null ? new ExplainRequest(null) : body).reader();
        DecisionView decision = decisions.decision(id, session.sandboxId(),
                        versionId -> rulesets.publishedById(versionId, session.sandboxId()).orElseThrow())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String language = rulesets.publishedById(decision.versionId(), session.sandboxId()).orElseThrow()
                .compiled().ruleSet().language().json();
        try {
            return ExplanationResponse.of(id, audience, explainer.explain(decision.decision(), audience, language));
        } catch (LlmUnavailableException e) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE);
        } catch (LlmMalformedOutputException e) {
            // Document 2, explain row: the provider answered, but not with an explanation; nothing reaches the reader
            log.warn("explanation malformed: {}", e.getMessage());
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE);
        }
    }
}
