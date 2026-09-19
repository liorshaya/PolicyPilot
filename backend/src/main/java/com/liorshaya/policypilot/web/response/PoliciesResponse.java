package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.policy.service.PolicyView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The policies a sandbox can see (Document 2, API Surface: {@code GET /policies}), each with the size of its latest
 * version, so the web app can open one without knowing its id.
 */
public record PoliciesResponse(@JsonProperty(required = true) List<Policy> policies) {

    /** One policy; {@code forkedFromId} appears only on a sandbox's copy of a protected policy. */
    public record Policy(
            @JsonProperty(required = true) UUID id,
            @JsonProperty(required = true) String title,
            @JsonProperty(required = true) String language,
            @JsonProperty(value = "protected", required = true) boolean isProtected,
            @JsonInclude(JsonInclude.Include.NON_NULL) UUID forkedFromId,
            @JsonProperty(required = true) int versionNo,
            @JsonProperty(required = true) int paragraphs,
            @JsonProperty(required = true) Instant createdAt) {}

    public static PoliciesResponse of(List<PolicyView> policies) {
        return new PoliciesResponse(policies.stream()
                .map(policy -> {
                    PolicyView.Version latest = policy.versions().getLast();
                    return new Policy(policy.id(), policy.title(), policy.language().code(), policy.isProtected(),
                            policy.forkedFromId(), latest.versionNo(), latest.paragraphs().size(),
                            policy.createdAt());
                })
                .toList());
    }
}
