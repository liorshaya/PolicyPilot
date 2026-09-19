package com.liorshaya.policypilot.policy.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A row of {@code policy_document}: the logical document; its text lives in its versions (Document 2, Data Model). */
@Entity
@Table(name = "policy_document")
public class PolicyDocumentEntity {

    @Id
    private UUID id;

    @Column(name = "sandbox_id")
    private UUID sandboxId;

    @Column(name = "protected", nullable = false)
    private boolean protectedRow;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String language;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNo")
    private List<PolicyVersionEntity> versions = new ArrayList<>();

    protected PolicyDocumentEntity() {}

    public PolicyDocumentEntity(UUID id, UUID sandboxId, boolean protectedRow, String title, String language,
            Instant createdAt) {
        this.id = id;
        this.sandboxId = sandboxId;
        this.protectedRow = protectedRow;
        this.title = title;
        this.language = language;
        this.createdAt = createdAt;
    }

    public void addVersion(PolicyVersionEntity version) {
        versions.add(version);
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

    public String getTitle() {
        return title;
    }

    public String getLanguage() {
        return language;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PolicyVersionEntity> getVersions() {
        return versions;
    }
}
