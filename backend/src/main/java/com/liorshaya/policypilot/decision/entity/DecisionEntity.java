package com.liorshaya.policypilot.decision.entity;

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
 * A row of {@code decision} (Document 2, Data Model; Brief FR-10): the input as it was, the decision object of
 * Document 3 and the version that produced it. A decision is written once and never changed.
 */
@Entity
@Immutable
@Table(name = "decision")
public class DecisionEntity {

    @Id
    private UUID id;

    @Column(name = "sandbox_id", nullable = false)
    private UUID sandboxId;

    @Column(name = "ruleset_version_id", nullable = false)
    private UUID rulesetVersionId;

    @Column(name = "case_id")
    private UUID caseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_json", nullable = false)
    private String input;

    @Column(nullable = false)
    private String status;

    @Column
    private String outcome;

    @Column(name = "deciding_rule_id")
    private String decidingRuleId;

    @Column(name = "error_code")
    private String errorCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trace_json", nullable = false)
    private String decision;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "duration_micros", nullable = false)
    private long durationMicros;

    protected DecisionEntity() {}

    public DecisionEntity(UUID id, UUID sandboxId, UUID rulesetVersionId, UUID caseId, String input, String status,
            String outcome, String decidingRuleId, String errorCode, String decision, Instant decidedAt,
            long durationMicros) {
        this.id = id;
        this.sandboxId = sandboxId;
        this.rulesetVersionId = rulesetVersionId;
        this.caseId = caseId;
        this.input = input;
        this.status = status;
        this.outcome = outcome;
        this.decidingRuleId = decidingRuleId;
        this.errorCode = errorCode;
        this.decision = decision;
        this.decidedAt = decidedAt;
        this.durationMicros = durationMicros;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRulesetVersionId() {
        return rulesetVersionId;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public String getInput() {
        return input;
    }

    public String getStatus() {
        return status;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getDecidingRuleId() {
        return decidingRuleId;
    }

    public String getDecision() {
        return decision;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public long getDurationMicros() {
        return durationMicros;
    }
}
