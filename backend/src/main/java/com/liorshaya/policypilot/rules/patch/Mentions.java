package com.liorshaya.policypilot.rules.patch;

import com.liorshaya.policypilot.rules.model.BooleanLiteral;
import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Expression;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.LiteralList;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operand;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a change request names (Document 3, Patch validation; Document 4, Candidate selection): a rule is named when
 * the request writes its id, or a number or a word its condition compares against; a field is named by its own
 * snake_case name. It is read from the request as the analyst wrote it, never from the model's answer.
 */
public final class Mentions {

    /** A rule id as a request writes it: R-140, r-140 or R140, not inside a longer word or number. */
    private static final Pattern RULE_ID = Pattern.compile("(?<![A-Za-z0-9])[Rr]-?([0-9]{2,4})(?![0-9])");
    /** A number with optional thousands separators and a decimal part: 9,000, 36, 89.9. */
    private static final Pattern NUMBER = Pattern.compile("(?<![0-9.,])[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?");

    private final SortedSet<String> ruleIds;
    /** Compared by value, so 9000 and 9000.0 are one number. */
    private final SortedSet<BigDecimal> numbers;
    private final String lowered;

    private Mentions(SortedSet<String> ruleIds, SortedSet<BigDecimal> numbers, String lowered) {
        this.ruleIds = Collections.unmodifiableSortedSet(ruleIds);
        this.numbers = Collections.unmodifiableSortedSet(numbers);
        this.lowered = lowered;
    }

    /** What the request names. The digits of a rule id are not also a number. */
    public static Mentions of(String request) {
        SortedSet<String> ruleIds = new TreeSet<>();
        Matcher id = RULE_ID.matcher(request);
        while (id.find()) {
            ruleIds.add("R-" + id.group(1));
        }
        SortedSet<BigDecimal> numbers = new TreeSet<>();
        Matcher number = NUMBER.matcher(RULE_ID.matcher(request).replaceAll(" "));
        while (number.find()) {
            numbers.add(new BigDecimal(number.group().replace(",", "")));
        }
        return new Mentions(ruleIds, numbers, request.toLowerCase(Locale.ROOT));
    }

    /** The rule ids the request writes, each as {@code R-} and its digits. */
    public SortedSet<String> ruleIds() {
        return ruleIds;
    }

    /** Whether the request names the rule: by its id, or by a number or a word its condition compares against. */
    public boolean names(Rule rule) {
        if (ruleIds.contains(rule.id())) {
            return true;
        }
        List<BigDecimal> tested = new ArrayList<>();
        List<String> words = new ArrayList<>();
        collect(rule.condition(), tested, words);
        return tested.stream().anyMatch(numbers::contains) || words.stream().anyMatch(this::writes);
    }

    /** The fields of the rule set the request names by their own names, in the rule set's order. */
    public List<String> fields(RuleSet ruleSet) {
        return ruleSet.fields().stream().map(Field::name).filter(this::writes).toList();
    }

    /** Whether the request writes the word as a whole word, whatever its case. */
    private boolean writes(String word) {
        return Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(word.toLowerCase(Locale.ROOT))
                + "(?![\\p{L}\\p{N}_])").matcher(lowered).find();
    }

    private static void collect(Condition condition, List<BigDecimal> numbers, List<String> words) {
        switch (condition) {
            case Condition.Comparison comparison -> collect(comparison.value(), numbers, words);
            case Condition.All all -> all.all().forEach(child -> collect(child, numbers, words));
            case Condition.Any any -> any.any().forEach(child -> collect(child, numbers, words));
            case Condition.Not not -> collect(not.not(), numbers, words);
            case Condition.Always always -> { }
        }
    }

    private static void collect(@Nullable Operand operand, List<BigDecimal> numbers, List<String> words) {
        switch (operand) {
            case NumberLiteral number -> numbers.add(number.value());
            case StringLiteral string -> words.add(string.value());
            case LiteralList list -> list.values().forEach(value -> collect(value, numbers, words));
            case Call call -> call.args().forEach(arg -> collect(arg, numbers));
            case BooleanLiteral ignored -> { }
            case FieldRef ignored -> { }
            case null -> { }
        }
    }

    private static void collect(Expression expression, List<BigDecimal> numbers) {
        switch (expression) {
            case NumberLiteral number -> numbers.add(number.value());
            case Call call -> call.args().forEach(arg -> collect(arg, numbers));
            case FieldRef ignored -> { }
        }
    }
}
