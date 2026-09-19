package com.liorshaya.policypilot.rules.validation;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Quote normalization (Document 3, Provenance): the comparison a quoted provenance must pass against its paragraph.
 * Both sides are decomposed to NFKD; combining marks (Hebrew niqqud), punctuation (Unicode categories P*) and
 * format characters (Cf: bidi overrides and isolates, zero-width characters) are removed; the text is lower-cased
 * without regard to the locale and whitespace runs collapse to one space. The steps and their order are those of
 * {@code norm} in {@code fixtures/reference/reference_check.py}.
 */
final class QuoteNormalizer {

    /** A normalized quote shorter than this never matches: a quote that normalizes to nothing cites nothing. */
    static final int MINIMUM_QUOTE_CODE_POINTS = 3;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private QuoteNormalizer() {}

    static String normalize(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        Normalizer.normalize(text, Normalizer.Form.NFKD).codePoints()
                .filter(codePoint -> !removed(Character.getType(codePoint)))
                .forEach(kept::appendCodePoint);
        return WHITESPACE.matcher(kept.toString().toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /** Whether the quote, normalized, keeps at least three characters and occurs in the normalized paragraph. */
    static boolean occursIn(String quote, String paragraph) {
        String normalized = normalize(quote);
        return normalized.codePointCount(0, normalized.length()) >= MINIMUM_QUOTE_CODE_POINTS
                && normalize(paragraph).contains(normalized);
    }

    private static boolean removed(int type) {
        return switch (type) {
            case Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK,
                    Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION, Character.FORMAT -> true;
            default -> false;
        };
    }
}
