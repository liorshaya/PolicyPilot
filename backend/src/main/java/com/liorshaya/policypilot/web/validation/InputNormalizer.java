package com.liorshaya.policypilot.web.validation;

import com.liorshaya.policypilot.web.error.ErrorCode;
import java.text.Normalizer;

/**
 * Unicode normalization of every text input, once, at the boundary (Document 5, Input Validation, policy text; the
 * Bidi paragraph): carriage returns become newlines, format characters (category Cf: bidi overrides and isolates,
 * zero-width characters, the byte order mark) are stripped, the result is NFC, and any other control character than
 * newline and tab is refused. What is stored, searched, shown and sent to the model is this string.
 */
public final class InputNormalizer {

    private InputNormalizer() {}

    /** The normalized text; throws {@link InputRejectedException} ({@code POLICY_INVALID}) on a control character. */
    public static String normalize(String text) {
        String lines = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder kept = new StringBuilder(lines.length());
        lines.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (type == Character.CONTROL && codePoint != '\n' && codePoint != '\t') {
                throw new InputRejectedException(ErrorCode.POLICY_INVALID, "contains a control character");
            }
            if (type != Character.FORMAT) {
                kept.appendCodePoint(codePoint);
            }
        });
        return Normalizer.normalize(kept, Normalizer.Form.NFC);
    }
}
