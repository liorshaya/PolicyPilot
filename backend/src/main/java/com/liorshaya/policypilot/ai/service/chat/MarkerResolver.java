package com.liorshaya.policypilot.ai.service.chat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The citation protocol as the answer streams (Document 4, Prompt 4, Marker resolution; Output Contracts, Citation
 * marker protocol). A marker whose id was supplied this turn stays in the text and is cited once, in the order it
 * first appears; any other marker, one whose id was not supplied or one that breaks the grammar, is removed from what
 * is shown and counted. Tokens split markers anywhere, so text that could still become a marker is held back until it
 * closes, and text that cannot is passed on at once.
 */
public final class MarkerResolver {

    /** Document 4's four kinds and their ids: a paragraph index, a rule id, an application number, a simulation. */
    private static final Pattern MARKER = Pattern.compile(
            "\\[\\[(?:p:[1-9]\\d{0,3}|r:R-\\d{2,4}|d:[1-9]\\d{0,8}"
                    + "|sim:d[1-9]\\d{0,8}:[a-z][a-z0-9_]*=[^\\],\\s]+(?:,[a-z][a-z0-9_]*=[^\\],\\s]+)*)]]");
    /** Text that has begun to be a marker: two brackets, the kind's letters, and whatever has come since. */
    private static final Pattern OPENED = Pattern.compile("\\[\\[[a-z]+(?::[^\\]\\n]*)?]?");
    /** A marker longer than this is not one, and what was held is shown as text. */
    private static final int LONGEST = 120;

    private final Predicate<String> supplied;
    private final Set<String> cited = new LinkedHashSet<>();
    private final StringBuilder held = new StringBuilder();
    private int dropped;

    /** @param supplied whether an id ({@code p:7}, {@code d:17}) was supplied this turn; asked as each marker closes */
    public MarkerResolver(Predicate<String> supplied) {
        this.supplied = supplied;
    }

    /** Takes the next piece of the answer and returns what may be shown now. */
    public String accept(String chunk) {
        String text = held.append(chunk).toString();
        held.setLength(0);
        StringBuilder shown = new StringBuilder(text.length());
        int at = 0;
        while (at < text.length()) {
            int open = text.indexOf('[', at);
            if (open < 0) {
                shown.append(text, at, text.length());
                break;
            }
            shown.append(text, at, open);
            at = consume(text, open, shown);
            if (at < 0) {
                held.append(text, open, text.length());
                break;
            }
        }
        return shown.toString();
    }

    /** The answer has ended: a marker still open is dropped, anything else held is shown. */
    public String finish() {
        String rest = held.toString();
        held.setLength(0);
        if (rest.length() > 2 && OPENED.matcher(rest).matches()) {
            dropped++;
            return "";
        }
        return rest;
    }

    /** The ids cited, each once, in the order the answer first used them. */
    public List<String> cited() {
        return new ArrayList<>(cited);
    }

    /** How many markers were removed, unknown or malformed. */
    public int dropped() {
        return dropped;
    }

    /**
     * Handles the bracket at {@code open}: returns where to continue, or -1 when everything from {@code open} must be
     * held for the next token.
     */
    private int consume(String text, int open, StringBuilder shown) {
        if (open + 1 >= text.length()) {
            return -1;
        }
        if (text.charAt(open + 1) != '[') {
            shown.append('[');
            return open + 1;
        }
        if (open + 2 >= text.length()) {
            return -1;
        }
        if (!Character.isLetter(text.charAt(open + 2))) {
            shown.append("[[");
            return open + 2;
        }
        int close = text.indexOf("]]", open + 2);
        if (close < 0) {
            String begun = text.substring(open);
            if (begun.length() <= LONGEST && OPENED.matcher(begun).matches()) {
                return -1;
            }
            shown.append("[[");
            return open + 2;
        }
        String candidate = text.substring(open, close + 2);
        if (candidate.indexOf(':') < 0) {
            shown.append("[[");
            return open + 2;
        }
        String id = candidate.substring(2, candidate.length() - 2);
        if (MARKER.matcher(candidate).matches() && supplied.test(id)) {
            shown.append(candidate);
            cited.add(id);
        } else {
            dropped++;
        }
        return close + 2;
    }
}
