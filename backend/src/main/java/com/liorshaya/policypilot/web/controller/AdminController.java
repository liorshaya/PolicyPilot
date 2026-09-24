package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.demo.service.ResetJob;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorEnvelope;
import com.liorshaya.policypilot.web.response.ResetResponse;
import com.liorshaya.policypilot.web.security.AccessCodeVerifier;
import com.liorshaya.policypilot.web.security.SandboxSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/admin/reset}: the presenter's reset (Document 2, API Surface; Document 5, Availability, Nightly
 * reset). Behind the session cookie like every route, and the admin code in {@link #HEADER}, compared in constant
 * time; without an admin code configured it refuses everyone. Each refusal counts in {@code security.admin.refused}.
 * The code is 16 characters from the environment, and the route shares the per-IP limit of every other route, so no
 * lockout of its own is needed.
 */
@RestController
public class AdminController {

    /** The header that carries {@code POLICYPILOT_ADMIN_CODE} (Document 2, decided 2026-09-27, day 15). */
    public static final String HEADER = "X-PolicyPilot-Admin-Code";

    private final @Nullable AccessCodeVerifier adminCode;
    private final ResetJob reset;
    private final SecurityEvents events;

    public AdminController(PolicyPilotProperties properties, ResetJob reset, SecurityEvents events) {
        String configured = properties.adminCode();
        this.adminCode = configured == null || configured.isBlank() ? null : new AccessCodeVerifier(configured);
        this.reset = reset;
        this.events = events;
    }

    @Operation(summary = "Delete the stale sandboxes and re-seed the protected demo data now")
    @ApiResponse(responseCode = "200", description = "What the reset did",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResetResponse.class)))
    @ApiResponse(responseCode = "403", description = "No admin code, a wrong one, or none configured",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(ApiPaths.ADMIN_RESET)
    public ResetResponse reset(@RequestHeader(value = HEADER, required = false) @Nullable String code,
            @AuthenticationPrincipal SandboxSession session) {
        String refused = adminCode == null ? "none-configured"
                : code == null || code.isBlank() ? "missing"
                : adminCode.matches(code) ? null : "wrong";
        if (refused != null) {
            events.adminRefused(refused, session.sandboxId());
            throw new ApiException(ErrorCode.ADMIN_CODE_INVALID);
        }
        return ResetResponse.of(reset.reset(ResetJob.Trigger.MANUAL, session.sandboxId().toString()));
    }
}
