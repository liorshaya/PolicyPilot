package com.liorshaya.policypilot.change.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A row of {@code change_request} (Document 2, Data Model): a stored proposal, the version it was proposed against,
 * the request as the analyst wrote it and the patches as validated. Day 12 writes it PROPOSED; day 13 decides it.
 */
@Entity
@Table(name = "change_request")
public class ChangeRequestEntity {

    @Id
    private UUID id;

    @Column(name = "sandbox_id", nullable = false)
    private UUID sandboxId;

    @Column(name = "base_version_id", nullable = false)
    private UUID baseVersionId;

    @Column(name = "request_text", nullable = false)
    private String requestText;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "patches_json", nullable = false)
    private String patchesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rationale_json", nullable = false)
    private String rationaleJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regression_json")
    private String regressionJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private String actor;

    protected ChangeRequestEntity() {}

    /** A proposal as it is first stored: PROPOSED, not decided. */
    public ChangeRequestEntity(UUID id, UUID sandboxId, UUID baseVersionId, String requestText, String patchesJson,
            String rationaleJson, String regressionJson, Instant createdAt, String actor) {
        this.id = id;
        this.sandboxId = sandboxId;
        this.baseVersionId = baseVersionId;
        this.requestText = requestText;
        this.status = "PROPOSED";
        this.patchesJson = patchesJson;
        this.rationaleJson = rationaleJson;
        this.regressionJson = regressionJson;
        this.createdAt = createdAt;
        this.actor = actor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSandboxId() {
        return sandboxId;
    }

    public UUID getBaseVersionId() {
        return baseVersionId;
    }

    public String getRequestText() {
        return requestText;
    }

    public String getStatus() {
        return status;
    }

    public String getPatchesJson() {
        return patchesJson;
    }

    public String getRationaleJson() {
        return rationaleJson;
    }

    public String getRegressionJson() {
        return regressionJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getActor() {
        return actor;
    }
}
