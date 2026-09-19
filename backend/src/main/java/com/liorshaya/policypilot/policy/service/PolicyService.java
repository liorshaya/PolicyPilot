package com.liorshaya.policypilot.policy.service;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.policy.entity.PolicyDocumentEntity;
import com.liorshaya.policypilot.policy.entity.PolicyVersionEntity;
import com.liorshaya.policypilot.policy.repository.PolicyDocumentRepository;
import com.liorshaya.policypilot.policy.repository.PolicyVersionRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Policy documents (Brief FR-1; Document 2, API Surface: {@code POST /policies}, {@code GET /policies/{id}}): stores a
 * normalized text as a document with its first version and paragraphs, and reads a document back only for the
 * sandbox it belongs to or, when protected, for every sandbox (Document 5, Authorization (sandbox)).
 */
@Service
public class PolicyService {

    static final String ENTITY = "policy";

    private final PolicyDocumentRepository documents;
    private final PolicyVersionRepository versions;
    private final SecurityEvents events;
    private final PolicyDocuments builder;

    public PolicyService(PolicyDocumentRepository documents, PolicyVersionRepository versions, SecurityEvents events,
            Clock clock) {
        this.documents = documents;
        this.versions = versions;
        this.events = events;
        this.builder = new PolicyDocuments(clock);
    }

    /**
     * Creates a document in {@code sandboxId} with {@code text} as version 1; the text is already normalized by the
     * API (Document 5: normalized once at the boundary). Throws {@link PolicyTextException} over the limits.
     */
    @Transactional
    public PolicyView create(UUID sandboxId, String title, PolicyLanguage language, String text) {
        return PolicyDocuments.view(documents.save(builder.build(sandboxId, false, title, language, text)));
    }

    /** Stores a protected document, visible to every sandbox and writable by none (the seeded demo policy). */
    @Transactional
    public PolicyView createProtected(String title, PolicyLanguage language, String text) {
        return PolicyDocuments.view(documents.save(builder.build(null, true, title, language, text)));
    }

    /** The protected documents, oldest first. */
    @Transactional(readOnly = true)
    public List<PolicyView> protectedPolicies() {
        return documents.findByProtectedRowTrueOrderByCreatedAt().stream().map(PolicyDocuments::view).toList();
    }

    /**
     * The sandbox's copy of the protected document {@code protectedId}, made on the first call and returned again
     * on every later one; the protected row itself is never modified.
     */
    @Transactional
    public PolicyView forkOf(UUID protectedId, UUID sandboxId) {
        PolicyDocumentEntity original = documents.findById(protectedId)
                .filter(PolicyDocumentEntity::isProtectedRow)
                .orElseThrow(() -> new IllegalArgumentException("not a protected policy: " + protectedId));
        return PolicyDocuments.view(documents.findBySandboxIdAndForkedFromId(sandboxId, protectedId)
                .orElseGet(() -> documents.save(builder.copy(original, sandboxId))));
    }

    /** The document {@code id} if the sandbox may see it; any other id, including another sandbox's, is absent. */
    @Transactional(readOnly = true)
    public Optional<PolicyView> find(UUID id, UUID sandboxId) {
        Optional<PolicyDocumentEntity> visible = documents.findVisible(id, sandboxId);
        if (visible.isEmpty() && documents.existsById(id)) {
            events.authorizationDenied(ENTITY, sandboxId, id.toString());
        }
        return visible.map(PolicyDocuments::view);
    }

    /**
     * The policy version {@code policyVersionId} as a rule set cites it. No sandbox check: only a rule set that already
     * references the version asks, and the rule set was checked against the caller's sandbox.
     */
    @Transactional(readOnly = true)
    public Optional<PolicyVersionRef> version(UUID policyVersionId) {
        return versions.findById(policyVersionId).map(PolicyService::ref);
    }

    /** Version {@code versionNo} of the document {@code policyId}, if the sandbox may see the document. */
    @Transactional(readOnly = true)
    public Optional<PolicyVersionRef> version(UUID policyId, int versionNo, UUID sandboxId) {
        return documents.findVisible(policyId, sandboxId)
                .flatMap(document -> versions.findByDocument(document.getId(), versionNo))
                .map(PolicyService::ref);
    }

    private static PolicyVersionRef ref(PolicyVersionEntity version) {
        return new PolicyVersionRef(version.getId(), version.getDocumentId(), version.getVersionNo(),
                version.getParagraphs().stream()
                        .map(p -> new PolicyVersionRef.Paragraph(p.getId(), p.getIndex(), p.getText()))
                        .toList());
    }
}
