package com.liorshaya.policypilot.decision.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A row of {@code case_fixture} (Document 2, Data Model): one synthetic applicant. The 200 of {@code cases-200} are
 * protected rows with their case numbers; a case entered in the UI belongs to its sandbox.
 */
@Entity
@Table(name = "case_fixture")
public class CaseFixtureEntity {

    @Id
    private UUID id;

    @Column(name = "sandbox_id")
    private UUID sandboxId;

    @Column(name = "protected", nullable = false)
    private boolean protectedRow;

    @Column(name = "fixture_set")
    private String fixtureSet;

    @Column(name = "case_no")
    private Integer caseNo;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields_json", nullable = false)
    private String fields;

    @Column(name = "expected_outcome")
    private String expectedOutcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String tags;

    protected CaseFixtureEntity() {}

    public CaseFixtureEntity(UUID id, UUID sandboxId, boolean protectedRow, String fixtureSet, Integer caseNo,
            String name, String fields, String expectedOutcome, String tags) {
        this.id = id;
        this.sandboxId = sandboxId;
        this.protectedRow = protectedRow;
        this.fixtureSet = fixtureSet;
        this.caseNo = caseNo;
        this.name = name;
        this.fields = fields;
        this.expectedOutcome = expectedOutcome;
        this.tags = tags;
    }

    public UUID getId() {
        return id;
    }

    public Integer getCaseNo() {
        return caseNo;
    }

    public String getFields() {
        return fields;
    }
}
