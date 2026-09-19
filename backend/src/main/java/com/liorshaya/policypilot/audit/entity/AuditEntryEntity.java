package com.liorshaya.policypilot.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A row of {@code audit_entry} (Document 2, Data Model). Append-only: no setters, {@code @Immutable} for Hibernate,
 * and no update or delete grant for the API's role at the database.
 */
@Entity
@Immutable
@Table(name = "audit_entry")
public class AuditEntryEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private Instant at;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(name = "ruleset_version_id")
    private UUID rulesetVersionId;

    @Column(name = "change_request_id")
    private UUID changeRequestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", nullable = false)
    private String details;

    protected AuditEntryEntity() {}

    public AuditEntryEntity(UUID id, Instant at, String actor, String action, UUID rulesetVersionId,
            UUID changeRequestId, String details) {
        this.id = id;
        this.at = at;
        this.actor = actor;
        this.action = action;
        this.rulesetVersionId = rulesetVersionId;
        this.changeRequestId = changeRequestId;
        this.details = details;
    }

    public UUID getId() {
        return id;
    }

    public Instant getAt() {
        return at;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public UUID getRulesetVersionId() {
        return rulesetVersionId;
    }

    public UUID getChangeRequestId() {
        return changeRequestId;
    }

    public String getDetails() {
        return details;
    }
}
