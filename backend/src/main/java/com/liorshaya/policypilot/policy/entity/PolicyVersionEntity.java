package com.liorshaya.policypilot.policy.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A row of {@code policy_version}: one text of a document, numbered from 1, with its paragraphs. */
@Entity
@Table(name = "policy_version")
public class PolicyVersionEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private PolicyDocumentEntity document;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("index")
    private List<PolicyParagraphEntity> paragraphs = new ArrayList<>();

    protected PolicyVersionEntity() {}

    public PolicyVersionEntity(UUID id, PolicyDocumentEntity document, int versionNo, String rawText, Instant createdAt) {
        this.id = id;
        this.document = document;
        this.versionNo = versionNo;
        this.rawText = rawText;
        this.createdAt = createdAt;
    }

    public void addParagraph(PolicyParagraphEntity paragraph) {
        paragraphs.add(paragraph);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return document.getId();
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getRawText() {
        return rawText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PolicyParagraphEntity> getParagraphs() {
        return paragraphs;
    }
}
