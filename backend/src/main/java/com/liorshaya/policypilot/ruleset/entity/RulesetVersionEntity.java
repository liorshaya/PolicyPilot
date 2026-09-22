package com.liorshaya.policypilot.ruleset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A row of {@code ruleset_version} (Document 2, Data Model): the whole DSL document of one version. Only a DRAFT
 * changes, through {@link #replace} and {@link #publish}; once published, the row is frozen by the trigger, except for
 * its embedding status, which the {@code rag} module moves through {@code RulesetService}.
 */
@Entity
@Table(name = "ruleset_version")
public class RulesetVersionEntity {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    /** The embedding status a version takes when it is published (Document 2, RAG pipeline, Embedding). */
    public static final String EMBEDDING_PENDING = "PENDING";

    @Id
    private UUID id;

    @Column(name = "ruleset_id", nullable = false)
    private UUID rulesetId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private String status;

    @Column(name = "policy_version_id", nullable = false)
    private UUID policyVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_json", nullable = false)
    private String rulesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_schema_json", nullable = false)
    private String fieldSchemaJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retired_ids", nullable = false)
    private String retiredIds;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private String publishedBy;

    @Column(name = "parent_version_id")
    private UUID parentVersionId;

    /** Null on a DRAFT; the one column the V7 trigger lets change after publishing. */
    @Column(name = "embedding_status")
    private String embeddingStatus;

    /** The draft's review (V9); null until the draft is reviewed, frozen with the rest of the row once published. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_json")
    private String reviewJson;

    protected RulesetVersionEntity() {}

    /** A new DRAFT version. */
    public RulesetVersionEntity(UUID id, UUID rulesetId, int versionNo, UUID policyVersionId, String rulesJson,
            String fieldSchemaJson, UUID parentVersionId) {
        this.id = id;
        this.rulesetId = rulesetId;
        this.versionNo = versionNo;
        this.status = DRAFT;
        this.policyVersionId = policyVersionId;
        this.rulesJson = rulesJson;
        this.fieldSchemaJson = fieldSchemaJson;
        this.retiredIds = "[]";
        this.parentVersionId = parentVersionId;
    }

    /** Replaces the document of a DRAFT. */
    public void replace(String rulesJson, String fieldSchemaJson) {
        requireDraft();
        this.rulesJson = rulesJson;
        this.fieldSchemaJson = fieldSchemaJson;
    }

    /** Stores the review of a DRAFT, replacing any earlier one (Document 2, Flow 1). */
    public void review(String reviewJson) {
        requireDraft();
        this.reviewJson = reviewJson;
    }

    /** Publishes a DRAFT: the last change the row ever takes. */
    public void publish(Instant at, String by) {
        requireDraft();
        this.status = PUBLISHED;
        this.publishedAt = at;
        this.publishedBy = by;
        this.embeddingStatus = EMBEDDING_PENDING;
    }

    public boolean isDraft() {
        return DRAFT.equals(status);
    }

    private void requireDraft() {
        if (!isDraft()) {
            throw new IllegalStateException("version " + id + " is " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRulesetId() {
        return rulesetId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getStatus() {
        return status;
    }

    public UUID getPolicyVersionId() {
        return policyVersionId;
    }

    public String getRulesJson() {
        return rulesJson;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public String getEmbeddingStatus() {
        return embeddingStatus;
    }

    public String getReviewJson() {
        return reviewJson;
    }

    public UUID getParentVersionId() {
        return parentVersionId;
    }
}
