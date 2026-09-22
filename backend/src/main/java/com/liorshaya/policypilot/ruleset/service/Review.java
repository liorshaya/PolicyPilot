package com.liorshaya.policypilot.ruleset.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A draft's review (Document 2, Flow 1 and ruleset_version.review_json): its status, the prompt version that wrote
 * it, its findings and the coverage map the reviewer returned, paragraph number to rule ids.
 */
public record Review(ReviewStatus status, String promptVersion, List<ReviewFinding> findings,
        Map<String, List<String>> coverage) {

    public Review {
        findings = List.copyOf(findings);
        coverage = copy(coverage);
    }

    /** A review whose call failed: no findings, and a draft that cannot be published until it is run again. */
    public static Review failed(String promptVersion) {
        return new Review(ReviewStatus.FAILED, promptVersion, List.of(), Map.of());
    }

    public Optional<ReviewFinding> finding(String id) {
        return findings.stream().filter(finding -> finding.id().equals(id)).findFirst();
    }

    /** The same findings, no longer describing the document as it now stands. */
    Review stale() {
        return new Review(ReviewStatus.STALE, promptVersion, findings, coverage);
    }

    Review with(ReviewFinding changed) {
        return new Review(status, promptVersion,
                findings.stream().map(finding -> finding.id().equals(changed.id()) ? changed : finding).toList(),
                coverage);
    }

    /** Keeps the paragraphs in the order the reviewer gave them. */
    private static Map<String, List<String>> copy(Map<String, List<String>> coverage) {
        Map<String, List<String>> copied = new LinkedHashMap<>();
        coverage.forEach((paragraph, rules) -> copied.put(paragraph, List.copyOf(rules)));
        return java.util.Collections.unmodifiableMap(copied);
    }
}
