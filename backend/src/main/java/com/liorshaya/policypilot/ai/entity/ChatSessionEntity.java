package com.liorshaya.policypilot.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A row of {@code chat_session} (Document 2, Data Model): one sandbox's conversation about one published version. */
@Entity
@Table(name = "chat_session")
public class ChatSessionEntity {

    @Id
    private UUID id;

    @Column(name = "sandbox_id", nullable = false)
    private UUID sandboxId;

    @Column(name = "ruleset_version_id", nullable = false)
    private UUID rulesetVersionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatSessionEntity() {}

    public ChatSessionEntity(UUID id, UUID sandboxId, UUID rulesetVersionId, Instant createdAt) {
        this.id = id;
        this.sandboxId = sandboxId;
        this.rulesetVersionId = rulesetVersionId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSandboxId() {
        return sandboxId;
    }

    public UUID getRulesetVersionId() {
        return rulesetVersionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
