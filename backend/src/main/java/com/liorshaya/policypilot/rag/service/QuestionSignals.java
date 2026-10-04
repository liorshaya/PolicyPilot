package com.liorshaya.policypilot.rag.service;

import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a question names that retrieval must not miss (Document 4, Retrieval Pipeline): the rule ids of the version it
 * names (Fusion keeps their chunks), the field names of the version and a decision number (Threshold keeps such a
 * question covered), and the terms the lexical query counts twice (Query). A follow-up that names nothing goes by
 * the names its conversation carries, in the same shape (Follow-up).
 */
public record QuestionSignals(Set<String> ruleIds, Set<String> fieldNames, boolean namesADecision, String strongTerms) {

    /** A rule id after {@link LexicalText}: {@code r} and its digits. */
    private static final Pattern NORMALIZED_RULE_ID = Pattern.compile("(?<![\\p{L}\\p{N}])r(\\p{Nd}{2,4})(?!\\p{N})");
    private static final Pattern NUMBER = Pattern.compile("\\p{Nd}+");
    /**
     * An application, case or decision followed by its number (Document 4, Threshold): in Hebrew with the prefixes a
     * word may carry, in English with number, no. or # between.
     */
    private static final Pattern DECISION = Pattern.compile(
            "(?:(?<![\\p{L}])[והבלמשכ]{0,2}(?:בקשה|בקשת|החלטה|תיק)(?:\\s+(?:מספר|מס'))?"
                    + "|(?i:\\b(?:application|decision|case)(?:\\s+(?:number|no\\.?))?))\\s*#?\\s*\\p{Nd}+");
    /** What a conversation that has named nothing carries. */
    public static final QuestionSignals NOTHING = naming(Set.of(), Set.of(), false);

    /** Both sets read in id order, so the strong terms and any listing of them are the same on every run. */
    public QuestionSignals {
        ruleIds = Collections.unmodifiableSortedSet(new TreeSet<>(ruleIds));
        fieldNames = Collections.unmodifiableSortedSet(new TreeSet<>(fieldNames));
    }

    /**
     * Names given alone, as a conversation carries them into a follow-up (Document 4, Follow-up): they count twice in
     * the lexical rank like a question's own, and no number comes with them.
     */
    public static QuestionSignals naming(Set<String> ruleIds, Set<String> fieldNames, boolean namesADecision) {
        return new QuestionSignals(ruleIds, fieldNames, namesADecision,
                String.join(" ", strong(new TreeSet<>(ruleIds), new TreeSet<>(fieldNames))));
    }

    public static QuestionSignals of(String question, RuleSet ruleSet) {
        String lexical = LexicalText.normalize(question);
        Set<String> ruleIds = new TreeSet<>();
        Matcher named = NORMALIZED_RULE_ID.matcher(lexical);
        while (named.find()) {
            String digits = named.group(1);
            ruleSet.rules().stream().map(Rule::id).filter(id -> id.equals("R-" + digits)).forEach(ruleIds::add);
        }
        String lower = question.toLowerCase(Locale.ROOT);
        Set<String> fieldNames = new TreeSet<>();
        ruleSet.fields().stream().map(Field::name)
                .filter(name -> Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}_])")
                        .matcher(lower).find())
                .forEach(fieldNames::add);
        List<String> strong = strong(ruleIds, fieldNames);
        Matcher numbers = NUMBER.matcher(NORMALIZED_RULE_ID.matcher(lexical).replaceAll(" "));
        while (numbers.find()) {
            strong.add(numbers.group());
        }
        return new QuestionSignals(ruleIds, fieldNames, DECISION.matcher(question).find(), String.join(" ", strong));
    }

    /** The rule ids as the lexical index holds them, then the field names as written, each in id order. */
    private static List<String> strong(Set<String> ruleIds, Set<String> fieldNames) {
        List<String> strong = new ArrayList<>();
        ruleIds.forEach(id -> strong.add("r" + id.substring(2)));
        strong.addAll(fieldNames);
        return strong;
    }

    /** Whether the question names a rule id, a field name or a decision number: the Threshold's exemption. */
    public boolean namesSomething() {
        return !ruleIds.isEmpty() || !fieldNames.isEmpty() || namesADecision;
    }
}
