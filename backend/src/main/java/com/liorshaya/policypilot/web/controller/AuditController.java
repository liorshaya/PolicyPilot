package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.audit.service.AuditCsv;
import com.liorshaya.policypilot.change.service.AuditTrail;
import com.liorshaya.policypilot.change.service.TrailEntry;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorEnvelope;
import com.liorshaya.policypilot.web.response.AuditEntriesResponse;
import com.liorshaya.policypilot.web.security.SandboxSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The audit routes of Document 2, API Surface: {@code GET /api/v1/audit}, every entry the sandbox can see, newest
 * first, or with {@code versionId} the entries of one version it can see, and {@code GET /api/v1/audit/export}, the
 * same as JSON or CSV. An entry about a change request carries its number; neither route ever shows an entry about
 * another sandbox's change request (Document 5).
 */
@RestController
public class AuditController {

    private final AuditTrail trail;

    public AuditController(AuditTrail trail) {
        this.trail = trail;
    }

    @Operation(summary = "The audit entries the sandbox can see, or those of one rule set version, newest first")
    @ApiResponse(responseCode = "200", description = "The entries this sandbox may read",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AuditEntriesResponse.class)))
    @ApiResponse(responseCode = "400", description = "A versionId that is not an id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "404", description = "No such rule set version in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @GetMapping(ApiPaths.AUDIT)
    public AuditEntriesResponse audit(@RequestParam(required = false) @Nullable UUID versionId,
            @AuthenticationPrincipal SandboxSession session) {
        return AuditEntriesResponse.of(entries(versionId, session));
    }

    @Operation(summary = "Export the audit log as JSON or CSV (Accept header)")
    @ApiResponse(responseCode = "200",
            description = "The entries of the version, or of every version the sandbox can see, newest first",
            content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = AuditEntriesResponse.class)),
                    @Content(mediaType = "text/csv", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "404", description = "No such rule set version in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @GetMapping(value = ApiPaths.AUDIT_EXPORT, produces = {MediaType.APPLICATION_JSON_VALUE, Exports.TEXT_CSV})
    public ResponseEntity<Object> export(@RequestParam(required = false) @Nullable UUID versionId,
            @AuthenticationPrincipal SandboxSession session,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) @Nullable String accept) {
        List<TrailEntry> entries = entries(versionId, session);
        boolean csv = Exports.wantsCsv(accept);
        return Exports.attachment(versionId == null ? "audit" : "audit-" + versionId, csv,
                csv ? AuditCsv.of(entries.stream().map(TrailEntry::entry).toList())
                        : AuditEntriesResponse.of(entries));
    }

    /** Every entry the sandbox can see, or those of one version it can see (404 otherwise). */
    private List<TrailEntry> entries(@Nullable UUID versionId, SandboxSession session) {
        if (versionId == null) {
            return trail.all(session.sandboxId());
        }
        return trail.ofVersion(versionId, session.sandboxId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
