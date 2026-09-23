package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.ai.LlmMalformedOutputException;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.service.Candidates;
import com.liorshaya.policypilot.ai.service.ChangeBase;
import com.liorshaya.policypilot.ai.service.Proposal;
import com.liorshaya.policypilot.change.service.ChangeDecision;
import com.liorshaya.policypilot.change.service.ChangeProgress;
import com.liorshaya.policypilot.change.service.ChangeRequestService;
import com.liorshaya.policypilot.change.service.Submitted;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorEnvelope;
import com.liorshaya.policypilot.web.request.DecideChangeRequest;
import com.liorshaya.policypilot.web.request.SubmitChangeRequest;
import com.liorshaya.policypilot.web.response.ChangeDecisionResponse;
import com.liorshaya.policypilot.web.response.ChangeEventPayloads;
import com.liorshaya.policypilot.web.response.StreamFailure;
import com.liorshaya.policypilot.web.response.VersionResponse.FindingResponse;
import com.liorshaya.policypilot.web.security.SandboxSession;
import com.liorshaya.policypilot.web.security.StreamRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code POST /api/v1/rulesets/{id}/versions/{no}/changes}: a change request in natural language (Document 2, API
 * Surface; Work Plan days 12 and 13), refused before any stream opens when the text is not a valid short text (400),
 * the sandbox cannot see the version (404), or the version is not PUBLISHED or not embedded (409). The answer is a
 * stream, {@code analyzing}, {@code proposing} with the candidate rules and fields, {@code validating},
 * {@code regression}, then {@code proposal} with the stored PROPOSED request, its diff and its regression report; or
 * {@code error} with the code, the findings and the model's last answer, and then nothing is stored.
 *
 * <p>{@code POST /api/v1/changes/{id}/approve} and {@code .../reject}: a person's decision on a stored proposal,
 * with an optional note (Document 2, API Surface). An approval publishes the next version in one transaction, into
 * the sandbox's own copy of the rule set when the base is protected; a rejection publishes nothing.
 */
@RestController
public class ChangeController {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeController.class);

    private final ChangeRequestService changes;
    private final StreamRegistry streams;
    private final ExecutorService generations;
    private final Clock clock;

    public ChangeController(ChangeRequestService changes, StreamRegistry streams, ExecutorService generations,
            Clock clock) {
        this.changes = changes;
        this.streams = streams;
        this.generations = generations;
        this.clock = clock;
    }

    @Operation(summary = "Submit a change request in natural language, answered as a stream of events")
    @ApiResponse(responseCode = "200",
            description = "An event stream: analyzing, proposing, validating, regression, then proposal or error")
    @ApiResponse(responseCode = "400", description = "The text is empty, too long or has a control character",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "404", description = "No such rule set version in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The version is not published, or not embedded yet",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(value = ApiPaths.RULESET_VERSION_CHANGES, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter submit(@PathVariable UUID id, @PathVariable int no, @RequestBody SubmitChangeRequest body,
            @AuthenticationPrincipal SandboxSession session) {
        String text = body.normalizedText();
        ChangeBase base;
        try {
            base = changes.base(id, no, session.sandboxId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        } catch (VersionStatusException e) {
            throw new ApiException(ErrorCode.VERSION_STATUS_CONFLICT);
        }
        StreamRegistry.Lease lease = streams.open(session.sandboxId(), clock.instant())
                .orElseThrow(() -> new ApiException(ErrorCode.RATE_LIMITED));
        SseEmitter emitter = new SseEmitter(StreamRegistry.MAX_LIFETIME.toMillis());
        emitter.onCompletion(lease::close);
        emitter.onTimeout(() -> {
            lease.close();
            emitter.complete();
        });
        generations.execute(() -> run(emitter, lease, base, text, session.sandboxId()));
        return emitter;
    }

    @Operation(summary = "Approve a proposed change: publish the next version with its audit entry")
    @ApiResponse(responseCode = "200", description = "The request, APPROVED, with the version the approval published",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ChangeDecisionResponse.class)))
    @ApiResponse(responseCode = "400", description = "The note is too long or has a control character",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "404", description = "No such change request in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409",
            description = "The request is not PROPOSED, or its base is no longer the latest version of its rule set",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(ApiPaths.CHANGE_APPROVE)
    public ChangeDecisionResponse approve(@PathVariable UUID id,
            @RequestBody(required = false) @Nullable DecideChangeRequest body,
            @AuthenticationPrincipal SandboxSession session) {
        String note = noteOf(body);
        return decided(() -> changes.approve(id, session.sandboxId(), note));
    }

    @Operation(summary = "Reject a proposed change: nothing is published")
    @ApiResponse(responseCode = "200", description = "The request, REJECTED",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ChangeDecisionResponse.class)))
    @ApiResponse(responseCode = "400", description = "The note is too long or has a control character",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "404", description = "No such change request in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The request is not PROPOSED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(ApiPaths.CHANGE_REJECT)
    public ChangeDecisionResponse reject(@PathVariable UUID id,
            @RequestBody(required = false) @Nullable DecideChangeRequest body,
            @AuthenticationPrincipal SandboxSession session) {
        String note = noteOf(body);
        return decided(() -> changes.reject(id, session.sandboxId(), note));
    }

    private static ChangeDecisionResponse decided(Supplier<Optional<ChangeDecision>> call) {
        try {
            return call.get().map(ChangeDecisionResponse::of).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        } catch (VersionStatusException e) {
            throw new ApiException(ErrorCode.VERSION_STATUS_CONFLICT);
        }
    }

    private static @Nullable String noteOf(@Nullable DecideChangeRequest body) {
        return body == null ? null : body.normalizedNote();
    }

    private void run(SseEmitter emitter, StreamRegistry.Lease lease, ChangeBase base, String text, UUID sandboxId) {
        Events events = new Events(emitter, lease, base.ruleSet().rules().size());
        try {
            switch (changes.submit(base, text, sandboxId, events)) {
                case Submitted.Stored stored ->
                        events.send("proposal", ChangeEventPayloads.Proposal.of(stored.request()));
                case Submitted.NotStored notStored -> events.send("error", refusal(notStored.proposal()));
            }
            emitter.complete();
        } catch (ClientGone e) {
            // the client hung up; a proposal stored before it did stays stored, PROPOSED
            emitter.complete();
        } catch (LlmUnavailableException e) {
            fail(events, emitter, ErrorCode.PROVIDER_UNAVAILABLE, e);
        } catch (LlmMalformedOutputException e) {
            fail(events, emitter, ErrorCode.RULESET_INVALID, e);
        } catch (RuntimeException e) {
            fail(events, emitter, ErrorCode.INTERNAL_ERROR, e);
        } finally {
            lease.close();
        }
    }

    /**
     * A proposal that was not stored, as Document 2 has it: RULESET_INVALID with its problems and findings and the
     * model's last answer, whether it failed after its repairs or was refused (Document 5, RT-04).
     */
    private static StreamFailure refusal(Proposal proposal) {
        List<FindingResponse> findings = Stream.concat(
                proposal.validation().problems().stream().map(FindingResponse::of),
                proposal.validation().findings().stream().map(FindingResponse::of)).toList();
        return new StreamFailure(ErrorCode.RULESET_INVALID.name(), findings, proposal.answer());
    }

    private static void fail(Events events, SseEmitter emitter, ErrorCode code, RuntimeException cause) {
        LOG.atWarn().setMessage("change.failed").addKeyValue("code", code.name()).setCause(cause).log();
        try {
            events.send("error", new StreamFailure(code.name(), List.of(), null));
        } catch (ClientGone ignored) {
            // nobody is listening for the error either
        }
        emitter.complete();
    }

    /** The change's progress as SSE, each event keeping the stream's lease alive. */
    private final class Events implements ChangeProgress {

        private final SseEmitter emitter;
        private final StreamRegistry.Lease lease;
        private final int rules;

        Events(SseEmitter emitter, StreamRegistry.Lease lease, int rules) {
            this.emitter = emitter;
            this.lease = lease;
            this.rules = rules;
        }

        @Override
        public void analyzing() {
            send("analyzing", new ChangeEventPayloads.Stage(rules));
        }

        @Override
        public void proposing(Candidates candidates) {
            send("proposing", new ChangeEventPayloads.Proposing(candidates.ruleIds(), candidates.fields()));
        }

        @Override
        public void validating() {
            send("validating", new ChangeEventPayloads.Stage(rules));
        }

        @Override
        public void regression() {
            send("regression", new ChangeEventPayloads.Stage(rules));
        }

        void send(String event, Object data) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
                lease.touch(clock.instant());
            } catch (IOException e) {
                throw new ClientGone(e);
            }
        }
    }

    /** The client stopped listening; the stream ends quietly. */
    static final class ClientGone extends RuntimeException {
        ClientGone(Throwable cause) {
            super(cause);
        }
    }
}
