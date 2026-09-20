package com.liorshaya.policypilot.ai.prompt;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One prompt template and the placeholders it declares (Document 4, Template format). A placeholder is
 * {@code {name}} with a name of letters, digits and underscores; every other brace in the file is the literal
 * text of the prompt, which matters because the prompts are full of JSON.
 *
 * <p>A template is rendered with exactly the names it declares: a missing name or an unexpected one is a
 * programming error and is thrown at startup, not at request time.
 */
public final class PromptTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}");

    private final String text;
    private final Set<String> placeholders;

    public PromptTemplate(String text) {
        this.text = text;
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        this.placeholders = Set.copyOf(found);
    }

    /** The names this template expects, in no particular order. */
    public Set<String> placeholders() {
        return placeholders;
    }

    /**
     * The template with every placeholder replaced by its value.
     *
     * @throws IllegalArgumentException when the values do not match the placeholders exactly
     */
    public String render(Map<String, String> values) {
        if (!values.keySet().equals(placeholders)) {
            throw new IllegalArgumentException("the template expects " + placeholders + " but was given "
                    + new LinkedHashSet<>(values.keySet()));
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder rendered = new StringBuilder(text.length());
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /** The raw template text, for the test that reads what a version says. */
    public String text() {
        return text;
    }
}
