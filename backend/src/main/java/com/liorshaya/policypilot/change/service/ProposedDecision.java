package com.liorshaya.policypilot.change.service;

import java.util.UUID;
import tools.jackson.databind.node.ObjectNode;

/**
 * What a proposal decides for one of the sandbox's decisions on its base version (Document 2, {@code GET
 * /changes/{id}/decisions/{decisionId}/trace}): the decision object the engine emits for the stored input on the
 * patched copy, with its trace; never stored.
 *
 * @param changeRequestNumber the request's number in its sandbox
 * @param basedOnDecisionId the stored decision whose input was decided again
 */
public record ProposedDecision(int changeRequestNumber, UUID basedOnDecisionId, ObjectNode decision) {}
