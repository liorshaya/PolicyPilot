package com.liorshaya.policypilot.policy.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The policy text limits of Document 5 (Input Validation): 40 KB (40,960 bytes of UTF-8 after normalization), at
 * least one and at most 200 paragraphs, 4,000 characters (code points) per paragraph.
 */
public final class PolicyTextLimits {

    public static final int MAX_BYTES = 40 * 1024;
    public static final int MAX_PARAGRAPHS = 200;
    public static final int MAX_PARAGRAPH_CODE_POINTS = 4_000;

    private PolicyTextLimits() {}

    /** Every broken limit of {@code text} split into {@code paragraphs}; empty when the text may be stored. */
    public static List<PolicyTextException.Violation> check(String text, List<String> paragraphs) {
        List<PolicyTextException.Violation> violations = new ArrayList<>();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            violations.add(new PolicyTextException.Violation("/text", "is longer than 40 KB"));
        }
        if (paragraphs.isEmpty()) {
            violations.add(new PolicyTextException.Violation("/text", "has no paragraph"));
        }
        if (paragraphs.size() > MAX_PARAGRAPHS) {
            violations.add(new PolicyTextException.Violation("/text", "has more than 200 paragraphs"));
        }
        for (int i = 0; i < paragraphs.size(); i++) {
            String paragraph = paragraphs.get(i);
            if (paragraph.codePointCount(0, paragraph.length()) > MAX_PARAGRAPH_CODE_POINTS) {
                violations.add(new PolicyTextException.Violation(
                        "/paragraphs/" + (i + 1), "is longer than 4,000 characters"));
            }
        }
        return violations;
    }
}
