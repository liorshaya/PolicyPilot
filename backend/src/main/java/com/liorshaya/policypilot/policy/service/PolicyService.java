package com.liorshaya.policypilot.policy.service;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.policy.entity.PolicyDocumentEntity;
import com.liorshaya.policypilot.policy.repository.PolicyDocumentRepository;
import java.time.Clock;
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
    private final SecurityEvents events;
    private final PolicyDocuments builder;

    public PolicyService(PolicyDocumentRepository documents, SecurityEvents events, Clock clock) {
        this.documents = documents;
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

    /** The document {@code id} if the sandbox may see it; any other id, including another sandbox's, is absent. */
    @Transactional(readOnly = true)
    public Optional<PolicyView> find(UUID id, UUID sandboxId) {
        Optional<PolicyDocumentEntity> visible = documents.findVisible(id, sandboxId);
        if (visible.isEmpty() && documents.existsById(id)) {
            events.authorizationDenied(ENTITY, sandboxId, id.toString());
        }
        return visible.map(PolicyDocuments::view);
    }
}
