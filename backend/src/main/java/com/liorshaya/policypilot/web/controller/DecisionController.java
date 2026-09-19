package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.decision.service.CaseInvalidException;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.decision.service.DecisionView;
import com.liorshaya.policypilot.rules.json.RuleSetFormatException;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.ruleset.service.PublishedVersion;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import com.liorshaya.policypilot.web.error.ErrorEnvelope;
import com.liorshaya.policypilot.web.request.DecideRequest;
import com.liorshaya.policypilot.web.request.SimulateRequest;
import com.liorshaya.policypilot.web.response.AggregatesResponse;
import com.liorshaya.policypilot.web.response.BatchResponse;
import com.liorshaya.policypilot.web.response.DecisionResponses;
import com.liorshaya.policypilot.web.security.RateLimits;
import com.liorshaya.policypilot.web.security.SandboxSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The decision routes of Document 2, API Surface: {@code POST .../decide}, {@code GET /decisions/{id}},
 * {@code GET .../stats} and {@code POST .../simulate} (Brief FR-8, FR-9, FR-10, FR-14). Only a published version
 * decides (FR-7); the sandbox comes from the session cookie only (Document 5, Authorization (sandbox)).
 */
@RestController
public class DecisionController {

    private final RulesetService rulesets;
    private final DecisionService decisions;
    private final RateLimits limits;
    private final SecurityEvents events;
    private final RuleSetMapper mapper = new RuleSetMapper();

    public DecisionController(RulesetService rulesets, DecisionService decisions, RateLimits limits,
            SecurityEvents events) {
        this.rulesets = rulesets;
        this.decisions = decisions;
        this.limits = limits;
        this.events = events;
    }

    @Operation(summary = "Decide one case, a list of cases or a seeded fixture set against a published version")
    @ApiResponse(responseCode = "200", description = "One decision with its trace, or a batch with its aggregates",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object")))
    @ApiResponse(responseCode = "404", description = "No such rule set version in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The version is not published",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "422", description = "A case fails case validation; nothing is stored",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(value = ApiPaths.RULESET_VERSION_DECIDE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object decide(@PathVariable UUID id, @PathVariable int no,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    description = "Exactly one of case, cases (at most 500) or fixtureSet",
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "object")))
                    @RequestBody String body,
            @AuthenticationPrincipal SandboxSession session, HttpServletRequest request) {
        DecideRequest decide = DecideRequest.of(read(body, ApiPaths.RULESET_VERSION_DECIDE));
        if (decide.isBatch()) {
            requireBatchAllowance(session, request);
        }
        PublishedVersion version = published(id, no, session);
        try {
            if (decide.fixtureSet() != null) {
                if (!decisions.hasFixtureSet(decide.fixtureSet())) {
                    throw new ApiException(ErrorCode.REQUEST_INVALID,
                            List.of(new ErrorDetail("/fixtureSet", "is not a seeded fixture set")));
                }
                return BatchResponse.of(
                        decisions.decideFixtureSet(version, session.sandboxId(), decide.fixtureSet()));
            }
            if (decide.cases() != null) {
                return BatchResponse.of(decisions.decideAll(version, session.sandboxId(), decide.cases()));
            }
            return DecisionResponses.of(decisions.decide(version, session.sandboxId(), decide.singleCase()));
        } catch (CaseInvalidException e) {
            throw caseInvalid(ApiPaths.RULESET_VERSION_DECIDE, e);
        }
    }

    @Operation(summary = "A stored decision with its trace")
    @ApiResponse(responseCode = "200", description = "The decision object of Document 3",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object")))
    @ApiResponse(responseCode = "404", description = "No such decision in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @GetMapping(ApiPaths.DECISION)
    public ObjectNode decision(@PathVariable UUID id, @AuthenticationPrincipal SandboxSession session) {
        return DecisionResponses.of(stored(id, session));
    }

    @Operation(summary = "Outcome counts and the top deciding rules of this sandbox on this version")
    @ApiResponse(responseCode = "200", description = "The counts and the top deciding rules",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AggregatesResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such rule set version in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The version is not published",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @GetMapping(ApiPaths.RULESET_VERSION_STATS)
    public AggregatesResponse stats(@PathVariable UUID id, @PathVariable int no,
            @AuthenticationPrincipal SandboxSession session) {
        PublishedVersion version = published(id, no, session);
        return AggregatesResponse.of(decisions.stats(version.versionId(), session.sandboxId()));
    }

    @Operation(summary = "What-if: the same version on a stored decision's input, or a case, with overrides")
    @ApiResponse(responseCode = "200", description = "A decision object marked as a simulation; nothing is stored",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object")))
    @ApiResponse(responseCode = "404", description = "No such version or decision in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The version is not published",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "422", description = "The case or an override fails case validation",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(value = ApiPaths.RULESET_VERSION_SIMULATE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode simulate(@PathVariable UUID id, @PathVariable int no,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    description = "decisionId or case, plus the overrides to apply",
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "object")))
                    @RequestBody String body,
            @AuthenticationPrincipal SandboxSession session) {
        SimulateRequest simulate = SimulateRequest.of(read(body, ApiPaths.RULESET_VERSION_SIMULATE));
        PublishedVersion version = published(id, no, session);
        requireDeclaredFields(version, simulate.overrides());
        ObjectNode input = simulate.caseInput();
        DecisionView base = null;
        if (simulate.decisionId() != null) {
            base = stored(simulate.decisionId(), session);
            input = decisions.inputOf(base);
        }
        try {
            ObjectNode simulation = decisions.simulate(version, input, simulate.overrides());
            if (base != null) {
                simulation.put("basedOnDecisionId", base.id().toString());
            }
            return simulation;
        } catch (CaseInvalidException e) {
            throw caseInvalid(ApiPaths.RULESET_VERSION_SIMULATE, e);
        }
    }

    /** The published version, or the envelope: 404 when the sandbox cannot see it, 409 when it is not published. */
    private PublishedVersion published(UUID rulesetId, int versionNo, SandboxSession session) {
        try {
            return rulesets.published(rulesetId, versionNo(versionNo), session.sandboxId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        } catch (VersionStatusException e) {
            throw new ApiException(ErrorCode.VERSION_STATUS_CONFLICT);
        }
    }

    private DecisionView stored(UUID id, SandboxSession session) {
        return decisions.decision(id, session.sandboxId(),
                        versionId -> rulesets.publishedById(versionId, session.sandboxId()).orElseThrow())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /**
     * Overrides may only name fields the rule set declares (Document 2, simulate; Document 5, Tool argument
     * validation); the refusal names the object, never the field the caller invented (Document 5, Error responses).
     */
    private static void requireDeclaredFields(PublishedVersion version, ObjectNode overrides) {
        Set<String> declared = version.compiled().ruleSet().fields().stream()
                .map(Field::name)
                .collect(Collectors.toSet());
        if (!declared.containsAll(overrides.propertyNames())) {
            throw new ApiException(ErrorCode.REQUEST_INVALID,
                    List.of(new ErrorDetail("/overrides", "names a field the rule set does not declare")));
        }
    }

    /** A batch counts against the 5 per minute per sandbox of Document 5 (Batch decide). */
    private void requireBatchAllowance(SandboxSession session, HttpServletRequest request) {
        Optional<Duration> wait = limits.tryConsume(RateLimits.EndpointClass.BATCH, request.getRemoteAddr(),
                session.sandboxId());
        if (wait.isPresent()) {
            events.rateLimitHit(RateLimits.EndpointClass.BATCH.tag(), session.sandboxId().toString());
            throw ApiException.rateLimited(wait.get());
        }
    }

    private ApiException caseInvalid(String endpoint, CaseInvalidException e) {
        events.inputRejected("POST " + endpoint, ErrorCode.CASE_INVALID.name());
        return new ApiException(ErrorCode.CASE_INVALID, e.problems().stream()
                .map(problem -> new ErrorDetail(problem.path(), problem.code()))
                .toList());
    }

    /** The body is read with the Document 5 limits and exact decimals, so a case reaches the engine as written. */
    private JsonNode read(String body, String endpoint) {
        try {
            return mapper.readTree(body);
        } catch (RuleSetFormatException e) {
            events.inputRejected("POST " + endpoint, ErrorCode.REQUEST_INVALID.name());
            throw new ApiException(ErrorCode.REQUEST_INVALID,
                    List.of(new ErrorDetail(e.pointer(), "cannot be read as JSON")));
        }
    }

    /** Version numbers count from 1 (Document 5, Ids in paths). */
    private static int versionNo(int no) {
        if (no < 1) {
            throw new ApiException(ErrorCode.REQUEST_INVALID,
                    List.of(new ErrorDetail("/versions", "is not a version number")));
        }
        return no;
    }
}
