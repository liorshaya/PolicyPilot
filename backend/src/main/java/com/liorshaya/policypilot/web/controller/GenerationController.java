package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.ai.LlmMalformedOutputException;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.service.AuthorService;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.request.GenerateRulesRequest;
import com.liorshaya.policypilot.web.response.VersionResponse;
import com.liorshaya.policypilot.web.response.VersionResponse.FindingResponse;
import com.liorshaya.policypilot.web.security.SandboxSession;
import com.liorshaya.policypilot.web.security.StreamRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
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
 * {@code POST /api/v1/policies/{id}/rulesets}: generate a draft rule set from the policy's latest version
 * (Document 2, API Surface; Work Plan day 7). The answer is a stream, because the work takes tens of seconds and
 * the analyst should see where it is: {@code parsing}, {@code authoring}, {@code validating}, then the draft
 * itself. Reviewing joins the sequence on day 10.
 *
 * <p>Nothing is stored unless the document validates: a failed generation answers an {@code error} event with the
 * findings, and the analyst sees what went wrong.
 */
@RestController
public class GenerationController {

    private static final Logger log = LoggerFactory.getLogger(GenerationController.class);

    private final AuthorService author;
    private final PolicyService policies;
    private final RulesetService rulesets;
    private final StreamRegistry streams;
    private final ExecutorService generations;
    private final Clock clock;

    public GenerationController(AuthorService author, PolicyService policies, RulesetService rulesets,
            StreamRegistry streams, ExecutorService generations, Clock clock) {
        this.author = author;
        this.policies = policies;
        this.rulesets = rulesets;
        this.streams = streams;
        this.generations = generations;
        this.clock = clock;
    }

    @Operation(summary = "Generate a draft rule set from a policy, as a stream of progress events")
    @ApiResponse(responseCode = "200", description = "An event stream: parsing, authoring, validating, draft")
    @PostMapping(value = ApiPaths.POLICY_RULESETS, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@PathVariable UUID id, @RequestBody(required = false) GenerateRulesRequest body,
            @AuthenticationPrincipal SandboxSession session) {
        PolicyView policy = policies.find(id, session.sandboxId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        PolicyVersionRef version = policies.version(id, policy.versions().getLast().versionNo(), session.sandboxId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        StreamRegistry.Lease lease = streams.open(session.sandboxId(), clock.instant())
                .orElseThrow(() -> new ApiException(ErrorCode.RATE_LIMITED));

        SseEmitter emitter = new SseEmitter(StreamRegistry.MAX_LIFETIME.toMillis());
        emitter.onCompletion(lease::close);
        emitter.onTimeout(() -> {
            lease.close();
            emitter.complete();
        });
        generations.execute(() -> run(emitter, lease, policy, version, hintsOf(body), session.sandboxId()));
        return emitter;
    }

    private void run(SseEmitter emitter, StreamRegistry.Lease lease, PolicyView policy, PolicyVersionRef version,
            @Nullable String hints, UUID sandboxId) {
        try {
            send(emitter, lease, "parsing", new Progress(version.paragraphs().size()));
            AuthorService.Authored authored = author.write(version, policy.title(), policy.language().name().toLowerCase(java.util.Locale.ROOT), hints,
                    stage -> send(emitter, lease, stage.name().toLowerCase(java.util.Locale.ROOT),
                            new Progress(version.paragraphs().size())));
            if (!authored.valid()) {
                send(emitter, lease, "error", new Failed(ErrorCode.RULESET_INVALID.name(),
                        authored.findings().stream().map(FindingResponse::of).toList(), authored.document()));
            } else {
                VersionView draft = rulesets.createDraft(sandboxId, version.id(), authored.document(),
                        ValidationContext.AUTHORING, Set.of());
                send(emitter, lease, "draft", VersionResponse.of(draft));
            }
            emitter.complete();
        } catch (LlmUnavailableException e) {
            fail(emitter, lease, ErrorCode.PROVIDER_UNAVAILABLE, e.reason().name(), e);
        } catch (LlmMalformedOutputException e) {
            fail(emitter, lease, ErrorCode.RULESET_INVALID, "the model did not answer with a document", e);
        } catch (RuntimeException e) {
            fail(emitter, lease, ErrorCode.INTERNAL_ERROR, "the generation failed", e);
        } finally {
            lease.close();
        }
    }

    private void fail(SseEmitter emitter, StreamRegistry.Lease lease, ErrorCode code, String message,
            RuntimeException cause) {
        log.warn("generation failed: {} ({})", code, message, cause);
        send(emitter, lease, "error", new Failed(code.name(), List.of(), null));
        emitter.complete();
    }

    private void send(SseEmitter emitter, StreamRegistry.Lease lease, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            lease.touch(clock.instant());
        } catch (IOException e) {
            // the client hung up; the stream is over and nothing else needs to be said
            throw new GenerationAbandoned(e);
        }
    }

    private static @Nullable String hintsOf(@Nullable GenerateRulesRequest body) {
        return Optional.ofNullable(body).map(GenerateRulesRequest::hints).orElse(null);
    }

    /** The payload of a progress event: what the stage is working on. */
    record Progress(int paragraphs) {}

    /** The payload of the error event: the code, the findings if there are any, and the last document. */
    record Failed(String code, List<FindingResponse> findings, @Nullable Object document) {}

    /** The client stopped listening; the stream ends quietly. */
    static final class GenerationAbandoned extends RuntimeException {
        GenerationAbandoned(Throwable cause) {
            super(cause);
        }
    }
}
