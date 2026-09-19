package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyTextException;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import com.liorshaya.policypilot.web.request.CreatePolicyRequest;
import com.liorshaya.policypilot.web.response.PolicyResponse;
import com.liorshaya.policypilot.web.security.SandboxSession;
import com.liorshaya.policypilot.web.validation.PolicyInput;
import com.liorshaya.policypilot.web.validation.UploadReader;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code POST /api/v1/policies} (pasted text or one uploaded file) and {@code GET /api/v1/policies/{id}} (Document 2,
 * API Surface; Brief FR-1). The sandbox comes from the session cookie only (Document 5, Authorization (sandbox)).
 */
@RestController
public class PolicyController {

    private final PolicyService policies;
    private final UploadReader uploads;
    private final SecurityEvents events;

    public PolicyController(PolicyService policies, UploadReader uploads, SecurityEvents events) {
        this.policies = policies;
        this.uploads = uploads;
        this.events = events;
    }

    @Operation(summary = "Create a policy from pasted text, returning its paragraph split")
    @PostMapping(value = ApiPaths.POLICIES, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<PolicyResponse> create(@RequestBody CreatePolicyRequest body,
            @AuthenticationPrincipal SandboxSession session) {
        return created(session, body.title(), body.language(), body::text);
    }

    @Operation(summary = "Create a policy from an uploaded .txt, .md or .pdf file, returning its paragraph split")
    @PostMapping(value = ApiPaths.POLICIES, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<PolicyResponse> upload(@RequestPart("file") MultipartFile file,
            @RequestParam("title") String title, @RequestParam("language") String language,
            @AuthenticationPrincipal SandboxSession session) {
        return created(session, title, language, () -> uploads.read(bytes(file)));
    }

    @Operation(summary = "A policy with its versions and paragraphs")
    @GetMapping(ApiPaths.POLICY)
    public PolicyResponse get(@PathVariable UUID id, @AuthenticationPrincipal SandboxSession session) {
        return policies.find(id, session.sandboxId())
                .map(PolicyResponse::of)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private ResponseEntity<PolicyResponse> created(SandboxSession session, String title, String language,
            Supplier<String> text) {
        PolicyView policy;
        try {
            PolicyInput input = PolicyInput.of(title, language, text);
            policy = policies.create(session.sandboxId(), input.title(), input.language(), input.text());
        } catch (ApiException e) {
            events.inputRejected("POST " + ApiPaths.POLICIES, e.code().name());
            throw e;
        } catch (PolicyTextException e) {
            events.inputRejected("POST " + ApiPaths.POLICIES, ErrorCode.POLICY_INVALID.name());
            throw new ApiException(ErrorCode.POLICY_INVALID, e.violations().stream()
                    .map(violation -> new ErrorDetail(violation.path(), violation.problem()))
                    .toList());
        }
        return ResponseEntity.created(URI.create(ApiPaths.POLICIES + "/" + policy.id()))
                .body(PolicyResponse.of(policy));
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
