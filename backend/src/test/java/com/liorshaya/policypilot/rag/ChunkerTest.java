package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rag.service.Chunk;
import com.liorshaya.policypilot.rag.service.Chunker;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.RuleSetBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.node.ObjectNode;

/**
 * The corpus of a published version (Document 4, Retrieval Pipeline, Corpus): one chunk per policy paragraph,
 * {@code p:<index>} with the paragraph text, and one per rule, {@code r:<ruleId>} rendered as its label, its
 * condition in the decision-table cell grammar of Document 3, its actions with their reasons and the quoted passage.
 * Every expected text is written out by hand from the lending fixture and the grammar, never taken from a run.
 */
@Requirement("FR-12")
class ChunkerTest {

    private static final RuleSetMapper MAPPER = new RuleSetMapper();

    private final Chunker chunker = new Chunker();

    // Document 6, Performance: "Corpus of the lending version (29 chunks)". Expected: 9 paragraphs, then the 20 rule
    // ids of fixtures/policies/consumer-lending/ruleset.v1.json in document order
    @Test
    void lendingVersionYieldsNineParagraphChunksAndTwentyRuleChunks() {
        List<Chunk> chunks = chunker.chunk(lendingParagraphs(), lending());

        assertThat(chunks).extracting(Chunk::id).containsExactly(
                "p:1", "p:2", "p:3", "p:4", "p:5", "p:6", "p:7", "p:8", "p:9",
                "r:R-010", "r:R-020", "r:R-100", "r:R-110", "r:R-115", "r:R-116", "r:R-120", "r:R-130", "r:R-140",
                "r:R-150", "r:R-160", "r:R-170", "r:R-200", "r:R-220", "r:R-310", "r:R-320", "r:R-330", "r:R-410",
                "r:R-420", "r:R-900");
    }

    // Document 4, Corpus: "p:<index>, the paragraph text". Expected: paragraph 7 of policy.he.md as written
    @Test
    void paragraphChunkIsItsIndexAndItsTextVerbatim() {
        Chunk chunk = chunk("p:7");

        assertThat(chunk.kind()).isEqualTo(Chunk.Kind.PARAGRAPH);
        assertThat(chunk.refId()).isEqualTo("7");
        assertThat(chunk.text()).isEqualTo("מבקש שנרשמו לו שני אירועי אשראי שליליים או יותר ב-24 החודשים האחרונים,"
                + " בקשתו תידחה. מבקש עם אירוע אחד יידרש להעמיד ערב.");
    }

    // Document 4, Corpus, in its order: label, condition, action and reason, the quoted passage. Expected: R-330
    // of ruleset.v1.json, "eq" as "= v" and the leaves of a top-level all joined by AND (Document 3, Cell grammar)
    @Test
    void ruleChunkRendersLabelConditionActionReasonAndQuoteInOrder() {
        Chunk chunk = chunk("r:R-330");

        assertThat(chunk.kind()).isEqualTo(Chunk.Kind.RULE);
        assertThat(chunk.refId()).isEqualTo("R-330");
        assertThat(chunk.text()).isEqualTo("""
                R-330 · בדיקת חתם: אירוע אשראי אחד ללא ערב
                When: credit_events_24m = 1 AND has_guarantor = false
                Then: refer: נדרש ערב בשל אירוע אשראי אחד ב-24 החודשים האחרונים
                Source, paragraph 7: "מבקש עם אירוע אחד יידרש להעמיד ערב\"""");
    }

    // Document 3, line 194 and the Condition columns row: "≥ 78 − term_months / 12". Expected: R-116's comparison
    @Test
    void expressionOperandRendersAsItsInfixText() {
        assertThat(chunk("r:R-116").text().lines().toList().get(1))
                .isEqualTo("When: employment_type = retired AND age ≥ 78 − term_months / 12");
    }

    // Document 3, Cell grammar: "between as [a .. b]", numbers with the field's unit and grouping; Structure:
    // "NOT [...]". Expected: R-120, requested_amount in ILS
    @Test
    void negatedRangeRendersAsNotAroundTheCell() {
        assertThat(chunk("r:R-120").text().lines().toList().get(1))
                .isEqualTo("When: NOT [requested_amount [10,000 ILS .. 150,000 ILS]]");
    }

    // Document 3, Cell grammar: "in as ∈ {a, b}", "present/absent as the words". Expected: R-310, whose provenance is
    // an analyst's, so the chunk ends with its action and has no Source line
    @Test
    void ruleWithoutQuotedProvenanceHasNoSourceLine() {
        assertThat(chunk("r:R-310").text()).isEqualTo("""
                R-310 · בדיקה ידנית: ותק לא דווח
                When: employment_type ∈ {salaried, self_employed} AND employment_months absent
                Then: refer: לא ניתן לאמת ותק תעסוקתי ללא נתון""");
    }

    // Document 3, Action column: "flag CODE", and "always" for the constant condition. Expected: R-420
    @Test
    void flagActionRendersItsCodeAndMessage() {
        assertThat(chunk("r:R-420").text()).isEqualTo("""
                R-420 · סימון: יציבות ההכנסה נבדקת ידנית
                When: always
                Then: flag STABLE_INCOME_MANUAL_CHECK: המדיניות דורשת הכנסה יציבה; הקריטריון אינו מוגדר ונבדק ידנית
                Source, paragraph 4: "המבקש יציג הכנסה יציבה\"""");
    }

    // Document 3, Action column: "rows whose action is set show the expression". Expected: R-020's derivation, the
    // sum in parentheses because it is divided, round written as a function
    @Test
    void setActionRendersTheFieldAndItsExpression() {
        assertThat(chunk("r:R-020").text().lines().toList().get(2)).isEqualTo(
                "Then: set debt_to_income = round((existing_monthly_debt + monthly_installment) / monthly_income, 4)");
    }

    // Document 3, Expressions: the right operand of a subtraction or a division keeps its parentheses; functions with
    // no sign are calls. Expected: R-010's Spitzer installment, written out by hand from ruleset.v1.json
    @Test
    void expressionKeepsOnlyTheParenthesesPrecedenceNeeds() {
        assertThat(chunk("r:R-010").text().lines().toList().get(2)).isEqualTo("Then: set monthly_installment ="
                + " round(requested_amount × 0.0075 / (1 − pow(1.0075, 0 − term_months)), 2)");
    }

    // Document 3, Expressions: sub takes two arguments and is not associative. Expected: 100 − (age − 21), which
    // without its parentheses would read as a different number
    @Test
    void subtractionOfASubtractionKeepsItsParentheses() {
        ObjectNode document = RuleSetBuilder.lendingV1().rule("R-020", rule -> {
            ObjectNode sub = ((ObjectNode) rule.withArray("actions").get(0)).putObject("value").put("fn", "sub");
            sub.putArray("args").add(100).addObject().put("fn", "sub").putArray("args")
                    .add(sub.objectNode().put("field", "age")).add(21);
        }).build();

        assertThat(ruleLines(document, "r:R-020").get(2)).isEqualTo("Then: set debt_to_income = 100 − (age − 21)");
    }

    // Document 3, Structure column: any and not rendered compactly. Expected: the inner any bracketed inside the all,
    // so the reader sees which operator binds
    @Test
    void combinatorInsideAnotherIsBracketed() {
        ObjectNode document = RuleSetBuilder.lendingV1().rule("R-140", rule -> {
            ObjectNode all = rule.putObject("condition");
            ObjectNode any = all.putArray("all").addObject();
            any.putArray("any").addObject().put("field", "age").put("op", "lt").put("value", 21);
            any.withArray("any").addObject().put("field", "age").put("op", "gt").put("value", 75);
            all.withArray("all").addObject().put("field", "has_guarantor").put("op", "eq").put("value", false);
        }).build();

        assertThat(ruleLines(document, "r:R-140").get(1))
                .isEqualTo("When: [age < 21 years OR age > 75 years] AND has_guarantor = false");
    }

    // Document 3, Rules: a disabled rule never fires, and a reader of the chunk must not think it does. Expected: the
    // label line says so
    @Test
    void disabledRuleSaysSoOnItsFirstLine() {
        ObjectNode document = RuleSetBuilder.lendingV1().rule("R-410", rule -> rule.put("enabled", false)).build();

        assertThat(ruleLines(document, "r:R-410").getFirst()).endsWith(" (disabled)");
    }

    // Document 3, Actions: up to five actions, a decide may be non-terminal. Expected: one Then line per action, in
    // order, the non-terminal decision marked
    @Test
    void ruleWithSeveralActionsRendersEachActionOnItsOwnLine() {
        ObjectNode document = RuleSetBuilder.lendingV1().rule("R-900", rule -> {
            rule.withArray("actions").removeAll();
            rule.withArray("actions").addObject().put("type", "flag").put("code", "REVIEWED").put("message", "נבדק");
            rule.withArray("actions").addObject().put("type", "decide").put("outcome", "approve")
                    .put("terminal", false).put("reason", "אושר");
        }).build();

        List<String> lines = ruleLines(document, "r:R-900");

        assertThat(lines.subList(2, 4)).containsExactly("Then: flag REVIEWED: נבדק", "Then: approve (not terminal): אושר");
    }

    // Document 3, Cell grammar: a plain number keeps its decimals and loses nothing. Expected: R-200, "> 0.4"
    @Test
    void decimalLiteralKeepsItsDigits() {
        assertThat(chunk("r:R-200").text().lines().toList().get(1)).isEqualTo("When: debt_to_income > 0.4");
    }

    // Document 3, Cell grammar, every operator in its vocabulary: "eq as = v, ne as ≠ v, lt/lte/gt/gte as <, ≤, >, ≥,
    // between as [a .. b], in as ∈ {a, b}, not_in as ∉ {a, b}, matches as ~ /pattern/, present/absent as the words".
    // Expected: the rendering Document 3 gives, on R-140's single leaf with the unit of its field where it has one
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            eq      | employment_type   | "unemployed"                 | employment_type = unemployed
            ne      | employment_type   | "unemployed"                 | employment_type ≠ unemployed
            lt      | age               | 21                           | age < 21 years
            lte     | age               | 21                           | age ≤ 21 years
            gt      | age               | 21                           | age > 21 years
            gte     | age               | 21                           | age ≥ 21 years
            between | requested_amount  | [10000, 150000]              | requested_amount [10,000 ILS .. 150,000 ILS]
            in      | employment_type   | ["salaried", "retired"]      | employment_type ∈ {salaried, retired}
            not_in  | employment_type   | ["salaried", "retired"]      | employment_type ∉ {salaried, retired}
            matches | employment_type   | "^sal"                       | employment_type ~ /^sal/
            present | employment_months |                              | employment_months present
            absent  | employment_months |                              | employment_months absent
            gt      | debt_to_income    | 0.40                         | debt_to_income > 0.4
            """)
    void everyOperatorRendersInTheCellGrammar(String op, String field, String value, String expected) {
        ObjectNode document = RuleSetBuilder.lendingV1().rule("R-140", rule -> {
            ObjectNode leaf = rule.putObject("condition").put("field", field).put("op", op);
            if (value != null) {
                leaf.set("value", MAPPER.readTree(value));
            }
        }).build();

        assertThat(ruleLines(document, "r:R-140").get(1)).isEqualTo("When: " + expected);
    }

    // NFR-5: chunking is paragraph-based and the text is UTF-8 end to end. Expected: every Hebrew paragraph of the
    // fixture, byte for byte, including the quotation mark in ש"ח and the hyphen in ל-150,000
    @Test
    void hebrewParagraphsArePassedThroughUnchanged() {
        List<Chunk> chunks = chunker.chunk(lendingParagraphs(), lending());

        assertThat(chunks.subList(0, 9)).extracting(Chunk::text).containsExactlyElementsOf(Fixtures.lendingParagraphs());
    }

    // NFR-5: the evaluation set has English policies too. Expected: consumer-lending-en's first paragraph and R-100
    // label, from fixtures/eval/policies/consumer-lending-en
    @Test
    void englishPolicyChunksTheSameWay() {
        List<String> texts = Fixtures.paragraphs("eval/policies/consumer-lending-en/policy.en.md");
        RuleSet ruleSet = MAPPER.toRuleSet(Fixtures.json("eval/policies/consumer-lending-en/expected.ruleset.json"));

        List<Chunk> chunks = chunker.chunk(paragraphs(texts), ruleSet);

        assertThat(chunks.getFirst().text()).isEqualTo(texts.getFirst());
        assertThat(chunks.stream().filter(chunk -> chunk.id().equals("r:R-100")).findFirst().orElseThrow().text())
                .startsWith("R-100 · Reject: younger than 21\nWhen: age < 21 years\n");
    }

    private List<String> ruleLines(ObjectNode document, String id) {
        return chunker.chunk(lendingParagraphs(), MAPPER.toRuleSet(document)).stream()
                .filter(chunk -> chunk.id().equals(id)).findFirst().orElseThrow().text().lines().toList();
    }

    private Chunk chunk(String id) {
        return chunker.chunk(lendingParagraphs(), lending()).stream()
                .filter(chunk -> chunk.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no chunk " + id));
    }

    private static RuleSet lending() {
        return MAPPER.toRuleSet(Fixtures.lendingV1());
    }

    private static List<PolicyVersionRef.Paragraph> lendingParagraphs() {
        return paragraphs(Fixtures.lendingParagraphs());
    }

    private static List<PolicyVersionRef.Paragraph> paragraphs(List<String> texts) {
        List<PolicyVersionRef.Paragraph> paragraphs = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            paragraphs.add(new PolicyVersionRef.Paragraph(UUID.randomUUID(), i + 1, texts.get(i)));
        }
        return paragraphs;
    }
}
