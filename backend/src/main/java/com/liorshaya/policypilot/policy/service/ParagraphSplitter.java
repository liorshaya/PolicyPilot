package com.liorshaya.policypilot.policy.service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits policy text into paragraphs (Document 5, policy text: split on blank lines, a line of spaces or tabs only is
 * blank; fixtures/README.md: the paragraph index is the position, from 1). Each paragraph is trimmed and empty ones
 * are dropped, as the Python reference's {@code load_paragraphs} does. The text arrives with newlines only.
 */
public final class ParagraphSplitter {

    private static final Pattern BLANK_LINE = Pattern.compile("\n[ \t]*\n");

    private ParagraphSplitter() {}

    public static List<String> split(String text) {
        return Arrays.stream(BLANK_LINE.split(text))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .toList();
    }
}
