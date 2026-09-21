package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.rag.service.RetrievalService;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import com.liorshaya.policypilot.web.error.ErrorEnvelope;
import com.liorshaya.policypilot.web.request.RetrievalRequest;
import com.liorshaya.policypilot.web.response.RetrievalResponse;
import com.liorshaya.policypilot.web.security.SandboxSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/rulesets/{id}/versions/{no}/retrieval} (Document 2, API Surface): the chunks hybrid retrieval
 * returns for a question on a version, and whether the not-covered threshold stops it. Read-only and scoped to the
 * session's sandbox; it embeds the question, so the rate limits count it as a model-calling route (Document 5).
 */
@RestController
public class RetrievalController {

    private final RetrievalService retrieval;

    public RetrievalController(RetrievalService retrieval) {
        this.retrieval = retrieval;
    }

    @Operation(summary = "Retrieve the chunks of a version for a question, or the fixed not-covered sentence")
    @ApiResponse(responseCode = "200", description = "The fused chunks and their citations, or the not-covered sentence",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RetrievalResponse.class)))
    @ApiResponse(responseCode = "400", description = "The question is empty, too long or has a control character",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "404", description = "No such rule set version in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The version is not published, or its embedding is not READY",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "503", description = "The embedding provider is unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(value = ApiPaths.RULESET_VERSION_RETRIEVAL, consumes = MediaType.APPLICATION_JSON_VALUE)
    public RetrievalResponse retrieve(@PathVariable UUID id, @PathVariable int no,
            @RequestBody RetrievalRequest body, @AuthenticationPrincipal SandboxSession session) {
        if (no < 1) {
            throw new ApiException(ErrorCode.REQUEST_INVALID,
                    List.of(new ErrorDetail("/versions", "is not a version number")));
        }
        String question = body.normalizedQuestion();
        try {
            return retrieval.retrieve(id, no, session.sandboxId(), question)
                    .map(RetrievalResponse::of)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        } catch (VersionStatusException e) {
            throw new ApiException(ErrorCode.VERSION_STATUS_CONFLICT);
        } catch (LlmUnavailableException e) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE);
        }
    }
}
