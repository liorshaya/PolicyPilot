package com.liorshaya.policypilot.ruleset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A row of {@code ruleset}: the logical rule set whose versions carry the documents (Document 2, Data Model).
 * {@code domain} is the DSL document's {@code id}, shared by every version; a protected row has no sandbox.
 */
@Entity
@Table(name = "ruleset")
public class RulesetEntity {

    @Id
    private UUID id;

    @Column(name = "sandbox_id")
    private UUID sandboxId;

    @Column(name = "protected", nullable = false)
    private boolean protectedRow;

    @Column(name = "forked_from_id")
    private UUID forkedFromId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String domain;

    @Column(name = "default_outcome", nullable = false)
    private String defaultOutcome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RulesetEntity() {}

    public RulesetEntity(UUID id, UUID sandboxId, boolean protectedRow, UUID forkedFromId, String name, String domain,
            String defaultOutcome, Instant createdAt) {
        this.id = id;
        this.sandboxId = sandboxId;
        this.protectedRow = protectedRow;
        this.forkedFromId = forkedFromId;
        this.name = name;
        this.domain = domain;
        this.defaultOutcome = defaultOutcome;
        this.createdAt = createdAt;
    }

    /** The display name and default outcome follow the latest document written to the rule set. */
    public void describe(String name, String defaultOutcome) {
        this.name = name;
        this.defaultOutcome = defaultOutcome;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSandboxId() {
        return sandboxId;
    }

    public boolean isProtectedRow() {
        return protectedRow;
    }

    public UUID getForkedFromId() {
        return forkedFromId;
    }

    public String getName() {
        return name;
    }

    public String getDomain() {
        return domain;
    }

    public String getDefaultOutcome() {
        return defaultOutcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
