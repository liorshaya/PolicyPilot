package com.liorshaya.policypilot.ai.service;

import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.ruleset.service.EmbeddingSource;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.node.ObjectNode;

/**
 * The published version a change request is proposed against (Document 4, Prompt 5): its document as stored, its
 * retrieval corpus, the title of the policy it was written from, and the rule ids its lineage has retired.
 */
public record ChangeBase(UUID rulesetId, int versionNo, ObjectNode document, EmbeddingSource corpus, String title,
        Set<String> retiredIds) {

    public ChangeBase {
        retiredIds = Set.copyOf(retiredIds);
    }

    /** The version's row id. */
    public UUID versionId() {
        return corpus.versionId();
    }

    public RuleSet ruleSet() {
        return corpus.ruleSet();
    }

    /** The paragraphs the version's rules quote; paragraph {@code n} is at {@code n - 1}. */
    public List<PolicyVersionRef.Paragraph> paragraphs() {
        return corpus.paragraphs();
    }
}
