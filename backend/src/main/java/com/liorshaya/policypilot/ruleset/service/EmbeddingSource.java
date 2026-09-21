package com.liorshaya.policypilot.ruleset.service;

import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.List;
import java.util.UUID;

/** What a version's retrieval corpus is made of: its rule set and the paragraphs of the policy version it cites. */
public record EmbeddingSource(UUID versionId, RuleSet ruleSet, List<PolicyVersionRef.Paragraph> paragraphs) {

    public EmbeddingSource {
        paragraphs = List.copyOf(paragraphs);
    }
}
