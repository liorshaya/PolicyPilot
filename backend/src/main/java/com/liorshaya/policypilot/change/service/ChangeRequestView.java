package com.liorshaya.policypilot.change.service;

import com.liorshaya.policypilot.ai.service.Candidates;
import com.liorshaya.policypilot.decision.service.Regression;
import com.liorshaya.policypilot.rules.diff.StructuralDiff;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.node.ObjectNode;

/**
 * A stored change request (Document 2, {@code change_request}): the request as the analyst wrote it, the version it
 * is proposed against, the proposal as validated, with the request's id in every pending provenance, and what it
 * would change.
 *
 * @param proposal the Patches object as stored: summary, patches, untouched and notes (Document 3, Change Patches)
 * @param candidates what candidate selection showed the model (Document 4, Prompt 5)
 * @param diff the base version against the patched copy (Document 3, Structural diff)
 * @param regression the sandbox's decisions on the base version decided again by the copy (Document 3)
 */
public record ChangeRequestView(UUID id, UUID baseVersionId, String requestText, String status, ObjectNode proposal,
        Candidates candidates, StructuralDiff diff, Regression regression, Instant createdAt, String actor) {}
