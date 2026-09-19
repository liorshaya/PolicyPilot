package com.liorshaya.policypilot.web.validation;

import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The fields of a new policy, validated and normalized at the boundary (Document 5, Input Validation): the title and
 * the text through {@link InputNormalizer}, the title required and at most 200 characters, the language {@code he} or
 * {@code en}. Every problem becomes one detail of a 422 {@code POLICY_INVALID}, by pointer, never by value.
 */
public record PolicyInput(String title, PolicyLanguage language, String text) {

    public static final int MAX_TITLE_CODE_POINTS = 200;

    /**
     * Validates the fields; {@code text} is read only when the title and language are valid, so an upload is not
     * parsed for a request that is refused anyway. An upload refusal keeps its own code ({@code UPLOAD_REJECTED}).
     */
    public static PolicyInput of(String title, String language, Supplier<String> text) {
        List<ErrorDetail> details = new ArrayList<>();
        String normalizedTitle = normalize(title, "/title", details);
        if (normalizedTitle != null && normalizedTitle.isBlank()) {
            details.add(new ErrorDetail("/title", "is required"));
        } else if (normalizedTitle != null
                && normalizedTitle.codePointCount(0, normalizedTitle.length()) > MAX_TITLE_CODE_POINTS) {
            details.add(new ErrorDetail("/title", "is longer than 200 characters"));
        }
        PolicyLanguage parsedLanguage = PolicyLanguage.fromCode(language).orElse(null);
        if (parsedLanguage == null) {
            details.add(new ErrorDetail("/language", "must be he or en"));
        }
        if (!details.isEmpty()) {
            throw new ApiException(ErrorCode.POLICY_INVALID, details);
        }
        String rawText;
        try {
            rawText = text.get();
        } catch (InputRejectedException e) {
            throw new ApiException(e.code(), List.of(new ErrorDetail("/file", e.problem())));
        }
        String normalizedText = normalize(rawText, "/text", details);
        if (!details.isEmpty()) {
            throw new ApiException(ErrorCode.POLICY_INVALID, details);
        }
        return new PolicyInput(normalizedTitle.strip(), parsedLanguage, normalizedText);
    }

    private static String normalize(String value, String path, List<ErrorDetail> details) {
        if (value == null) {
            details.add(new ErrorDetail(path, "is required"));
            return null;
        }
        try {
            return InputNormalizer.normalize(value);
        } catch (InputRejectedException e) {
            details.add(new ErrorDetail(path, e.problem()));
            return null;
        }
    }
}
