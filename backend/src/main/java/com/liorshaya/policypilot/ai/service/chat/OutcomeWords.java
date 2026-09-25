package com.liorshaya.policypilot.ai.service.chat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * The words that name a decision's outcome (Document 4, Prompt 4: Words that name an outcome), from
 * {@code prompts/answer/outcome-words.yml}. A label's word that is one of an outcome's forms is said by any form of
 * that outcome: an answer's word that is the form, in Hebrew also after up to three prefix letters, in English in any
 * letter case, with no negation among the three words before it. Any other word is said when the text holds it as
 * written.
 */
public final class OutcomeWords {

    private static final String FILE = "prompts/answer/outcome-words.yml";
    /** Letters, digits and the apostrophe inside an English word such as "wouldn't". */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}'’]+");
    /** The Hebrew letters that attach to a word in front of it: and, the, that, in, to, as, from. */
    private static final String PREFIX_LETTERS = "והשבלכמ";
    private static final int MOST_PREFIX_LETTERS = 3;
    private static final int NEGATION_REACH = 3;

    private final List<Set<String>> outcomes;
    private final Set<String> negations;

    private OutcomeWords(List<Set<String>> outcomes, Set<String> negations) {
        this.outcomes = List.copyOf(outcomes);
        this.negations = Set.copyOf(negations);
    }

    /** Reads the forms and the negations; a malformed file is a startup failure. */
    @SuppressWarnings("unchecked")
    public static OutcomeWords load() {
        try (InputStream in = new ClassPathResource(FILE).getInputStream()) {
            Map<String, Object> yaml = new Yaml().load(in);
            List<Set<String>> outcomes = ((Map<String, Map<String, List<String>>>) yaml.get("outcomes")).values()
                    .stream().map(OutcomeWords::lowerCase).toList();
            return new OutcomeWords(outcomes, lowerCase((Map<String, List<String>>) yaml.get("negations")));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + FILE, e);
        }
    }

    /** Whether the text says the word: any form of its outcome when it names one, else the word as written. */
    public boolean says(String text, String word) {
        Optional<Set<String>> outcome = outcomes.stream()
                .filter(forms -> forms.contains(word.toLowerCase(Locale.ROOT))).findFirst();
        if (outcome.isEmpty()) {
            return text.contains(word);
        }
        List<String> words = WORD.matcher(text.toLowerCase(Locale.ROOT)).results().map(MatchResult::group).toList();
        for (int index = 0; index < words.size(); index++) {
            if (isOneOf(words.get(index), outcome.get()) && !negatedAt(words, index)) {
                return true;
            }
        }
        return false;
    }

    private boolean negatedAt(List<String> words, int index) {
        for (int before = Math.max(0, index - NEGATION_REACH); before < index; before++) {
            String word = words.get(before);
            if (isOneOf(word, negations) || word.endsWith("n't") || word.endsWith("n’t")) {
                return true;
            }
        }
        return false;
    }

    /** The word is one of these, itself or after one to three Hebrew prefix letters. */
    private static boolean isOneOf(String word, Set<String> words) {
        if (words.contains(word)) {
            return true;
        }
        for (int prefix = 1; prefix <= MOST_PREFIX_LETTERS && prefix < word.length(); prefix++) {
            if (PREFIX_LETTERS.indexOf(word.charAt(prefix - 1)) < 0) {
                return false;
            }
            if (words.contains(word.substring(prefix))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> lowerCase(Map<String, List<String>> byLanguage) {
        return byLanguage.values().stream().flatMap(List::stream).map(word -> word.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
