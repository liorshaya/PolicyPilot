package com.liorshaya.policypilot.ruleset.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One review finding as stored with its draft (Document 4, Findings contract, with the id and the acknowledgement of
 * Document 2, ruleset_version.review_json). Its anchors were checked against the draft and the policy before it was
 * stored, so every rule id and paragraph number here exists.
 */
public record ReviewFinding(String id, FindingKind kind, List<String> ruleIds, List<Integer> paragraphIndexes,
        String message, String suggestion, double confidence, @Nullable Acknowledgement acknowledgement) {

    public ReviewFinding {
        ruleIds = List.copyOf(ruleIds);
        paragraphIndexes = List.copyOf(paragraphIndexes);
    }

    public String severity() {
        return kind.severity();
    }

    public boolean acknowledged() {
        return acknowledgement != null;
    }

    /** Blocks publishing: a kind that must be acknowledged, and is not yet. */
    public boolean blocking() {
        return kind.blocksPublishing() && !acknowledged();
    }

    ReviewFinding acknowledgedWith(Acknowledgement acknowledgement) {
        return new ReviewFinding(id, kind, ruleIds, paragraphIndexes, message, suggestion, confidence,
                acknowledgement);
    }
}
