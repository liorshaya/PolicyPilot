package com.liorshaya.policypilot.web.response;

import com.liorshaya.policypilot.decision.service.DecisionView;
import tools.jackson.databind.node.ObjectNode;

/**
 * A stored decision as the API returns it: the decision object of Document 3 with the four things the API adds to
 * it (the stored id, the case it came from, the version that produced it and the timing). The engine emits none of
 * them, which is why they are added here and nowhere else.
 */
public final class DecisionResponses {

    private DecisionResponses() {}

    public static ObjectNode of(DecisionView decision) {
        ObjectNode response = decision.decision().deepCopy();
        response.put("id", decision.id().toString());
        if (decision.caseNo() != null) {
            response.put("caseNo", decision.caseNo());
        }
        response.putObject("rulesetVersion")
                .put("id", decision.rulesetId())
                .put("versionNo", decision.versionNo())
                .put("versionId", decision.versionId().toString());
        response.put("decidedAt", decision.decidedAt().toString());
        response.put("durationMicros", decision.durationMicros());
        return response;
    }
}
