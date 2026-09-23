package com.liorshaya.policypilot.change.service;

import com.liorshaya.policypilot.ai.service.Candidates;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.node.ObjectNode;

/**
 * A stored change request (Document 2, {@code change_request}): the request as the analyst wrote it, the version it
 * is proposed against, and the proposal as validated, with the request's id in every pending provenance.
 *
 * @param proposal the Patches object as stored: summary, patches, untouched and notes (Document 3, Change Patches)
 * @param candidates what candidate selection showed the model (Document 4, Prompt 5)
 */
public record ChangeRequestView(UUID id, UUID baseVersionId, String requestText, String status, ObjectNode proposal,
        Candidates candidates, Instant createdAt, String actor) {}
