package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.audit.service.AuditEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/** Audit entries, newest first (Document 2, {@code GET /audit} and {@code GET /audit/export}). */
public record AuditEntriesResponse(@JsonProperty(required = true) List<Entry> entries) {

    public static AuditEntriesResponse of(List<AuditEntry> entries) {
        return new AuditEntriesResponse(entries.stream().map(entry -> new Entry(entry.id(), entry.at(),
                entry.actor(), entry.action().name(), entry.rulesetVersionId(), entry.changeRequestId(),
                entry.details())).toList());
    }

    /**
     * One entry (Document 2, audit_entry).
     *
     * @param actor the sandbox that acted, or {@code demo-analyst} for the seeded rows (Document 5)
     * @param changeRequestId the change request the entry is about, or null
     */
    public record Entry(
            @JsonProperty(required = true) UUID id,
            @JsonProperty(required = true) Instant at,
            @JsonProperty(required = true) String actor,
            @JsonProperty(required = true)
            @Schema(allowableValues = {"PUBLISH", "CHANGE_PROPOSED", "CHANGE_APPROVED", "CHANGE_REJECTED",
                    "GAP_ACKNOWLEDGED", "RESET"})
            String action,
            @JsonProperty(required = true) UUID rulesetVersionId,
            @JsonProperty(required = true) @Schema(nullable = true) @Nullable UUID changeRequestId,
            @JsonProperty(required = true) @Schema(implementation = Object.class, description = "The entry's details")
            ObjectNode details) {}
}
