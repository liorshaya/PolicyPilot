package com.liorshaya.policypilot.ruleset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A row of {@code rule}: one rule of a published version, denormalized for queries and provenance joins (Document 2). */
@Entity
@Immutable
@Table(name = "rule")
public class RuleEntity {

    @Id
    private UUID id;

    @Column(name = "ruleset_version_id", nullable = false)
    private UUID rulesetVersionId;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private String label;

    @Column(name = "provenance_kind", nullable = false)
    private String provenanceKind;

    @Column(name = "paragraph_id")
    private UUID paragraphId;

    @Column(name = "source_quote")
    private String sourceQuote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_json", nullable = false)
    private String ruleJson;

    protected RuleEntity() {}

    public RuleEntity(UUID id, UUID rulesetVersionId, String ruleId, int priority, String label, String provenanceKind,
            UUID paragraphId, String sourceQuote, String ruleJson) {
        this.id = id;
        this.rulesetVersionId = rulesetVersionId;
        this.ruleId = ruleId;
        this.priority = priority;
        this.label = label;
        this.provenanceKind = provenanceKind;
        this.paragraphId = paragraphId;
        this.sourceQuote = sourceQuote;
        this.ruleJson = ruleJson;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getProvenanceKind() {
        return provenanceKind;
    }

    public UUID getParagraphId() {
        return paragraphId;
    }
}
