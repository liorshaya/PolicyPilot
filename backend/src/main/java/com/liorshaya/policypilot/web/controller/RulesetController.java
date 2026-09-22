package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.rules.json.RuleSetFormatException;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.ruleset.service.FindingsUnresolvedException;
import com.liorshaya.policypilot.ruleset.service.RulesetInvalidException;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import com.liorshaya.policypilot.web.error.ErrorEnvelope;
import com.liorshaya.policypilot.web.request.AcknowledgeFindingRequest;
import com.liorshaya.policypilot.web.response.RulesetsResponse;
import com.liorshaya.policypilot.web.response.VersionResponse;
import com.liorshaya.policypilot.web.security.SandboxSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * The rule set routes of Document 2, API Surface: {@code GET /api/v1/rulesets}, {@code GET
 * /api/v1/rulesets/{id}/versions/{no}}, {@code PUT .../rules}, {@code POST .../publish}, {@code POST .../review} and
 * {@code POST .../findings/{findingId}/acknowledge} (Brief FR-5, FR-6, FR-7; Document 2, Flow 1). The sandbox comes
 * from the session cookie only (Document 5, Authorization (sandbox)).
 */
@RestController
public class RulesetController {

    /** A finding id as the review numbers them (Document 2, ruleset_version.review_json). */
    private static final Pattern FINDING_ID = Pattern.compile("F-[1-9][0-9]{0,2}");

    private final RulesetService rulesets;
    private final DraftReviewer reviewer;
    private final SecurityEvents events;
    private final RuleSetMapper mapper = new RuleSetMapper();

    public RulesetController(RulesetService rulesets, DraftReviewer reviewer, SecurityEvents events) {
        this.rulesets = rulesets;
        this.reviewer = reviewer;
        this.events = events;
    }

    @Operation(summary = "The rule sets this session can see: the seeded ones and its own")
    @GetMapping(ApiPaths.RULESETS)
    public RulesetsResponse list(@AuthenticationPrincipal SandboxSession session) {
        return RulesetsResponse.of(rulesets.visible(session.sandboxId()));
    }

    @Operation(summary = "A rule set version with its rules, findings and status")
    @ApiResponse(responseCode = "200", description = "The version with its rules, findings and status",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VersionResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such rule set version in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @GetMapping(ApiPaths.RULESET_VERSION)
    public VersionResponse version(@PathVariable UUID id, @PathVariable int no,
            @AuthenticationPrincipal SandboxSession session) {
        return answer("GET " + ApiPaths.RULESET_VERSION,
                () -> rulesets.version(id, versionNo(no), session.sandboxId()));
    }

    @Operation(summary = "Replace the rules of a DRAFT version; a write to a protected version forks a copy")
    @ApiResponse(responseCode = "200", description = "The version as it now stands, in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VersionResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such rule set version in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The version's status does not allow this",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "422", description = "The rule set fails the Document 3 validator",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PutMapping(value = ApiPaths.RULESET_VERSION_RULES, consumes = MediaType.APPLICATION_JSON_VALUE)
    public VersionResponse replaceRules(@PathVariable UUID id, @PathVariable int no,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    description = "The whole DSL document (Document 3)",
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "object")))
                    @RequestBody String body,
            @AuthenticationPrincipal SandboxSession session) {
        JsonNode document = read(body);
        return answer("PUT " + ApiPaths.RULESET_VERSION_RULES,
                () -> rulesets.replaceRules(id, versionNo(no), session.sandboxId(), document));
    }

    @Operation(summary = "Publish a DRAFT version: compile, snapshot its rules and write the audit entry")
    @ApiResponse(responseCode = "200", description = "The published version",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VersionResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such rule set version in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The version's status does not allow this",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "422",
            description = "The rule set fails the Document 3 validator, or its review does not allow publishing yet",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(ApiPaths.RULESET_VERSION_PUBLISH)
    public VersionResponse publish(@PathVariable UUID id, @PathVariable int no,
            @AuthenticationPrincipal SandboxSession session) {
        return answer("POST " + ApiPaths.RULESET_VERSION_PUBLISH,
                () -> rulesets.publish(id, versionNo(no), session.sandboxId()));
    }

    @Operation(summary = "Run the review again on a DRAFT, after an edit or a failed review")
    @ApiResponse(responseCode = "200", description = "The version with its new review",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = VersionResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such rule set version in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The version is not a DRAFT, or is protected",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "503", description = "The model provider failed; the review is left FAILED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(ApiPaths.RULESET_VERSION_REVIEW)
    public VersionResponse review(@PathVariable UUID id, @PathVariable int no,
            @AuthenticationPrincipal SandboxSession session) {
        return answer("POST " + ApiPaths.RULESET_VERSION_REVIEW, () -> {
            Optional<VersionView> version = rulesets.version(id, versionNo(no), session.sandboxId());
            if (version.isEmpty()) {
                return version;
            }
            if (version.get().isProtected() || !"DRAFT".equals(version.get().status().name())) {
                throw new VersionStatusException("only a DRAFT of the sandbox's own rule set is reviewed");
            }
            try {
                return Optional.of(reviewer.review(version.get(), session.sandboxId()));
            } catch (LlmUnavailableException e) {
                throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE);
            }
        });
    }

    @Operation(summary = "Acknowledge one review finding of a DRAFT: a gap with its resolution, an error with a note")
    @ApiResponse(responseCode = "200", description = "The version with the finding acknowledged",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = VersionResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such rule set version or finding in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The version's status does not allow this",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "400", description = "The resolution or the note the finding's kind needs is missing",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(value = ApiPaths.RULESET_VERSION_FINDING_ACKNOWLEDGE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public VersionResponse acknowledge(@PathVariable UUID id, @PathVariable int no, @PathVariable String findingId,
            @RequestBody(required = false) @Nullable AcknowledgeFindingRequest body,
            @AuthenticationPrincipal SandboxSession session) {
        if (!FINDING_ID.matcher(findingId).matches()) {
            throw new ApiException(ErrorCode.REQUEST_INVALID,
                    List.of(new ErrorDetail("/findingId", "is not a finding id")));
        }
        AcknowledgeFindingRequest request = body == null ? new AcknowledgeFindingRequest(null, null) : body;
        String endpoint = "POST " + ApiPaths.RULESET_VERSION_FINDING_ACKNOWLEDGE;
        return answer(endpoint, () -> {
            try {
                return rulesets.acknowledge(id, versionNo(no), session.sandboxId(), findingId,
                        request.gapResolution(), request.normalizedNote());
            } catch (IllegalArgumentException e) {
                events.inputRejected(endpoint, ErrorCode.REQUEST_INVALID.name());
                String path = e.getMessage().contains("resolution") ? "/resolution" : "/note";
                throw new ApiException(ErrorCode.REQUEST_INVALID, List.of(new ErrorDetail(path, e.getMessage())));
            }
        });
    }

    /** Maps the service's refusals to the envelope; an unknown or foreign id is 404 (Document 5, no existence oracle). */
    private VersionResponse answer(String endpoint, Supplier<Optional<VersionView>> call) {
        try {
            return call.get().map(VersionResponse::of).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        } catch (RulesetInvalidException e) {
            events.inputRejected(endpoint, ErrorCode.RULESET_INVALID.name());
            throw new ApiException(ErrorCode.RULESET_INVALID, e.problems().stream()
                    .map(problem -> new ErrorDetail(problem.path(), problem.code()))
                    .toList());
        } catch (FindingsUnresolvedException e) {
            throw new ApiException(ErrorCode.FINDINGS_UNRESOLVED, e.problems().stream()
                    .map(problem -> new ErrorDetail(problem.path(), problem.code()))
                    .toList());
        } catch (VersionStatusException e) {
            throw new ApiException(ErrorCode.VERSION_STATUS_CONFLICT);
        }
    }

    /** The body is the whole DSL document, read with the Document 5 limits and exact decimals. */
    private JsonNode read(String body) {
        try {
            return mapper.readTree(body);
        } catch (RuleSetFormatException e) {
            events.inputRejected("PUT " + ApiPaths.RULESET_VERSION_RULES, ErrorCode.REQUEST_INVALID.name());
            throw new ApiException(ErrorCode.REQUEST_INVALID,
                    List.of(new ErrorDetail(e.pointer(), "cannot be read as a rule set document")));
        }
    }

    /** Version numbers count from 1 (Document 5, Ids in paths: anything else is refused before any lookup). */
    private static int versionNo(int no) {
        if (no < 1) {
            throw new ApiException(ErrorCode.REQUEST_INVALID, List.of(new ErrorDetail("/versions", "is not a version number")));
        }
        return no;
    }
}
