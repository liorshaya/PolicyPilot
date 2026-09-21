package com.liorshaya.policypilot.rag.service;

import java.util.regex.Pattern;

/**
 * The one lexical normalization chunks and questions go through before PostgreSQL's {@code simple} parser sees them
 * (Document 4, Retrieval Pipeline, Lexical index). The parser reads {@code R-320} as {@code r} and {@code -320} and
 * {@code 8,000} as {@code 8} and {@code 000}, so a rule id becomes one token {@code r320}, grouping commas leave
 * numbers, and a hyphen between a letter and a digit ({@code ל-84}) becomes a space.
 */
public final class LexicalText {

    /** A rule id of the DSL ({@code R-} and 2 to 4 digits), in any case, with or without its hyphen. */
    private static final Pattern RULE_ID = Pattern.compile("(?<![\\p{L}\\p{N}])[Rr]-?(\\p{Nd}{2,4})(?!\\p{N})");
    private static final Pattern GROUPED_NUMBER = Pattern.compile("(?<![\\p{N},])\\p{Nd}{1,3}(?:,\\p{Nd}{3})+(?![\\p{N}])");
    private static final Pattern LETTER_HYPHEN_DIGIT = Pattern.compile("(?<=\\p{L})-(?=\\p{Nd})");

    private LexicalText() {}

    /** The text as the lexical index stores it and as a question is searched with. */
    public static String normalize(String text) {
        String ruleIds = RULE_ID.matcher(text).replaceAll("r$1");
        String numbers = GROUPED_NUMBER.matcher(ruleIds).replaceAll(match -> match.group().replace(",", ""));
        return LETTER_HYPHEN_DIGIT.matcher(numbers).replaceAll(" ");
    }
}
