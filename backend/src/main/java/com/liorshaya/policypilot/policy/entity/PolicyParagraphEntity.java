package com.liorshaya.policypilot.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** A row of {@code policy_paragraph}: the provenance unit, its index the position within the version from 1. */
@Entity
@Table(name = "policy_paragraph")
public class PolicyParagraphEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_version_id")
    private PolicyVersionEntity version;

    @Column(name = "index", nullable = false)
    private int index;

    @Column(nullable = false)
    private String text;

    protected PolicyParagraphEntity() {}

    public PolicyParagraphEntity(UUID id, PolicyVersionEntity version, int index, String text) {
        this.id = id;
        this.version = version;
        this.index = index;
        this.text = text;
    }

    public UUID getId() {
        return id;
    }

    public int getIndex() {
        return index;
    }

    public String getText() {
        return text;
    }
}
