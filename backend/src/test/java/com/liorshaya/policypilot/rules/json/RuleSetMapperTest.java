package com.liorshaya.policypilot.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.liorshaya.policypilot.rules.model.Action;
import com.liorshaya.policypilot.rules.model.BooleanLiteral;
import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.Function;
import com.liorshaya.policypilot.rules.model.LiteralList;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operator;
import com.liorshaya.policypilot.rules.model.Outcome;
import com.liorshaya.policypilot.rules.model.Provenance;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.RuleSetBuilder;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** The DSL 1.0 records and their strict, lossless JSON mapping (Document 3; Document 5, JSON and deserialization). */
@Requirement({"FR-3", "NFR-7"})
class RuleSetMapperTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final RuleSetMapper mapper = new RuleSetMapper();

    @Test
    void lendingV1MapsToRecordsAndBackToAnEqualJsonTree() {
        ObjectNode document = Fixtures.lendingV1();

        RuleSet ruleSet = mapper.toRuleSet(document);

        assertThat(ruleSet.rules()).hasSize(20);
        assertThat(ruleSet.fields()).hasSize(11);
        assertThat(mapper.toJson(ruleSet)).isEqualTo(document);
    }

    @Test
    void numericLiteralsKeepTheirExactDecimalValue() {
        RuleSet ruleSet = mapper.toRuleSet(Fixtures.lendingV1());

        // R-010: round(div(mul(requested_amount, 0.0075), sub(1, pow(1.0075, sub(0, term_months)))), 2)
        Call round = (Call) ((Action.SetField) rule(ruleSet, "R-010").actions().getFirst()).value();
        Call div = (Call) round.args().getFirst();
        Call mul = (Call) div.args().getFirst();
        Call pow = (Call) ((Call) div.args().get(1)).args().get(1);
        assertThat(mul.args().get(1)).isEqualTo(new NumberLiteral(new BigDecimal("0.0075")));
        assertThat(pow.args().getFirst()).isEqualTo(new NumberLiteral(new BigDecimal("1.0075")));
        assertThat(round.args().get(1)).isEqualTo(new NumberLiteral(new BigDecimal("2")));

        ObjectNode large = RuleSetBuilder.lendingV1()
                .rule("R-170", r -> ((ObjectNode) r.get("condition"))
                        .set("value", mapper.readTree("123456789012345678901234567890")))
                .rule("R-220", r -> ((ObjectNode) r.get("condition")).put("value", 3_000_000_000L))
                .rule("R-100", r -> ((ObjectNode) r.get("condition")).put("value", Integer.MAX_VALUE))
                .rule("R-140", r -> ((ObjectNode) r.get("condition")).put("value", Long.MAX_VALUE))
                .field("age", f -> f.put("exclusiveMinimum", 0).put("exclusiveMaximum", new BigDecimal("120.5")))
                .build();
        RuleSet mapped = mapper.toRuleSet(large);
        assertThat(((Condition.Comparison) rule(mapped, "R-170").condition()).value())
                .isEqualTo(new NumberLiteral(new BigDecimal("123456789012345678901234567890")));
        assertThat(field(mapped, "age").exclusiveMaximum()).isEqualTo(new BigDecimal("120.5"));
        assertThat(mapper.toJson(mapped)).isEqualTo(large);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conformanceFixtures")
    void everyConformanceRuleSetMapsToRecords(Path fixture) {
        JsonNode document = Fixtures.ruleSetOf(Fixtures.json(fixture));

        assertThat(mapper.toJson(mapper.toRuleSet(document))).isEqualTo(document);
    }

    @Test
    void conditionTreeMapsEveryNodeKindAndOperandForm() {
        RuleSet ruleSet = mapper.toRuleSet(Fixtures.lendingV1());

        assertThat(rule(ruleSet, "R-110").condition()).isEqualTo(new Condition.All(List.of(
                new Condition.Comparison("age", Operator.GT, number("70")),
                new Condition.Comparison("employment_type", Operator.NE, new StringLiteral("retired")))));
        assertThat(rule(ruleSet, "R-120").condition()).isEqualTo(new Condition.Not(
                new Condition.Comparison("requested_amount", Operator.BETWEEN,
                        new LiteralList(List.of(number("10000"), number("150000"))))));
        assertThat(((Condition.All) rule(ruleSet, "R-116").condition()).all().get(1)).isEqualTo(
                new Condition.Comparison("age", Operator.GTE, new Call(Function.SUB, List.of(number("78"),
                        new Call(Function.DIV, List.of(new FieldRef("term_months"), number("12")))))));
        assertThat(rule(ruleSet, "R-310").condition()).isEqualTo(new Condition.All(List.of(
                new Condition.Comparison("employment_type", Operator.IN,
                        new LiteralList(List.of(new StringLiteral("salaried"), new StringLiteral("self_employed")))),
                new Condition.Comparison("employment_months", Operator.ABSENT, null))));
        assertThat(((Condition.All) rule(ruleSet, "R-330").condition()).all().get(1)).isEqualTo(
                new Condition.Comparison("has_guarantor", Operator.EQ, new BooleanLiteral(false)));
        assertThat(rule(ruleSet, "R-900").condition()).isEqualTo(new Condition.Always());

        // Document 3, Conditions: any, and a field reference as the operand
        ObjectNode withAny = RuleSetBuilder.lendingV1()
                .rule("R-140", r -> r.set("condition", mapper.readTree("""
                        {"any": [{"field": "monthly_income", "op": "lt", "value": {"field": "existing_monthly_debt"}},
                                 {"field": "employment_type", "op": "eq", "value": "unemployed"}]}""")))
                .build();
        RuleSet mapped = mapper.toRuleSet(withAny);
        assertThat(rule(mapped, "R-140").condition()).isEqualTo(new Condition.Any(List.of(
                new Condition.Comparison("monthly_income", Operator.LT, new FieldRef("existing_monthly_debt")),
                new Condition.Comparison("employment_type", Operator.EQ, new StringLiteral("unemployed")))));
        assertThat(mapper.toJson(mapped)).isEqualTo(withAny);
    }

    @Test
    void decideActionIsTerminalWhenTerminalIsOmitted() {
        ObjectNode document = RuleSetBuilder.lendingV1()
                .rule("R-100", r -> ((ObjectNode) r.get("actions").get(0)).remove("terminal"))
                .build();
        RuleSet ruleSet = mapper.toRuleSet(document);

        Action.Decide omitted = (Action.Decide) rule(ruleSet, "R-100").actions().getFirst();
        assertThat(omitted.terminal()).isNull();
        assertThat(omitted.isTerminal()).isTrue();
        assertThat(mapper.toJson(ruleSet)).isEqualTo(document);

        RuleSet candidates = mapper.toRuleSet(Fixtures.ruleSetOf(Fixtures.json("conformance/C-04.json")));
        Action.Decide nonTerminal = (Action.Decide) candidates.rules().getFirst().actions().getFirst();
        assertThat(nonTerminal.isTerminal()).isFalse();

        assertThat(rule(ruleSet, "R-010").actions().getFirst()).isInstanceOf(Action.SetField.class);
        assertThat(rule(ruleSet, "R-410").actions().getFirst()).isEqualTo(new Action.Flag(
                "INCOME_NEAR_MINIMUM", "ההכנסה בטווח של 1,000 ש\"ח מעל המינימום"));
        assertThat(((Action.Decide) rule(ruleSet, "R-900").actions().getFirst()).outcome())
                .isEqualTo(Outcome.APPROVE);
    }

    @Test
    void provenanceMapsToQuotedAnalystAndPending() {
        RuleSet ruleSet = mapper.toRuleSet(Fixtures.lendingV1());

        assertThat(rule(ruleSet, "R-100").provenance()).isEqualTo(
                new Provenance.Quoted(1, "גילו 21 עד 70 בעת הגשת הבקשה", new BigDecimal("0.97")));
        assertThat(rule(ruleSet, "R-310").provenance()).isEqualTo(new Provenance.Analyst(
                "ותק חסר אינו ניתן לאימות אוטומטי; הופנה לבדיקה ידנית בהחלטת האנליסט", "demo-analyst", null));
        assertThat(field(ruleSet, "age").source()).isEqualTo(
                new Provenance.Quoted(1, "גילו 21 עד 70 בעת הגשת הבקשה", null));

        ObjectNode approved = RuleSetBuilder.lendingV1().rule("R-170", r -> r.putObject("provenance")
                        .put("kind", "analyst")
                        .put("note", "Change request cr-0042: raise the minimum income to 9,000")
                        .put("actor", "demo-analyst")
                        .put("changeRequestId", "cr-0042"))
                .build();
        RuleSet approvedSet = mapper.toRuleSet(approved);
        assertThat(rule(approvedSet, "R-170").provenance()).isEqualTo(new Provenance.Analyst(
                "Change request cr-0042: raise the minimum income to 9,000", "demo-analyst", "cr-0042"));
        assertThat(mapper.toJson(approvedSet)).isEqualTo(approved);

        JsonNode proposal = Fixtures.ruleSetOf(Fixtures.json("conformance/invalid-PROVENANCE_PENDING_AT_PUBLISH.json"));
        RuleSet mapped = mapper.toRuleSet(proposal);
        assertThat(rule(mapped, "R-170").provenance()).isEqualTo(
                new Provenance.Pending("cr-0001", "threshold raised per request"));
        assertThat(mapper.toJson(mapped)).isEqualTo(proposal);
    }

    @Test
    void omittedFlagsTakeTheirDocumentedDefaults() {
        RuleSet ruleSet = mapper.toRuleSet(Fixtures.lendingV1());

        Field age = field(ruleSet, "age");
        Field installment = field(ruleSet, "monthly_installment");
        assertThat(age.isRequired()).isTrue();
        assertThat(age.derived()).isNull();
        assertThat(age.isDerived()).isFalse();
        assertThat(installment.isDerived()).isTrue();
        assertThat(installment.required()).isNull();
        assertThat(installment.isRequired()).isFalse();
        assertThat(field(ruleSet, "has_guarantor").defaultValue()).isEqualTo(new BooleanLiteral(false));

        Rule r100 = rule(ruleSet, "R-100");
        assertThat(r100.enabled()).isNull();
        assertThat(r100.isEnabled()).isTrue();
        RuleSet disabled = mapper.toRuleSet(Fixtures.ruleSetOf(Fixtures.json("conformance/C-25.json")));
        assertThat(disabled.rules().getFirst().isEnabled()).isFalse();
    }

    /** Document 5, FAIL_ON_UNKNOWN_PROPERTIES: every object kind of the DSL refuses a property it does not define. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unknownProperties")
    void unknownPropertyFailsMappingEvenWithoutTheSchema(String name, Consumer<RuleSetBuilder> change, String pointer) {
        RuleSetBuilder builder = RuleSetBuilder.lendingV1();
        change.accept(builder);
        ObjectNode document = builder.build();

        assertThatThrownBy(() -> mapper.toRuleSet(document))
                .isInstanceOfSatisfying(RuleSetFormatException.class, e -> {
                    assertThat(e.pointer()).isEqualTo(pointer);
                    assertThat(e.getMessage()).isEqualTo("unknown property");
                });
    }

    /**
     * Document 5, strict Jackson: the mapping holds its own line behind the schema. Each case breaks one shape rule
     * of Document 3 and names the pointer of the node it broke.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("shapeViolations")
    void shapeTheSchemaWouldRejectFailsMappingWithItsPointer(
            String name, Consumer<RuleSetBuilder> change, String pointer, String message) {
        RuleSetBuilder builder = RuleSetBuilder.lendingV1();
        change.accept(builder);
        ObjectNode document = builder.build();

        assertThatThrownBy(() -> mapper.toRuleSet(document))
                .isInstanceOfSatisfying(RuleSetFormatException.class, e -> {
                    assertThat(e.pointer()).isEqualTo(pointer);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
    }

    @Test
    void documentThatIsNotAnObjectFailsMapping() {
        assertThatThrownBy(() -> mapper.toRuleSet(mapper.readTree("[]")))
                .isInstanceOfSatisfying(RuleSetFormatException.class, e -> assertThat(e.pointer()).isEmpty());
    }

    @Test
    void documentNestedDeeperThan32LevelsIsRejectedBeforeMapping() {
        String depth32 = "[".repeat(RuleSetMapper.MAX_NESTING_DEPTH) + "]".repeat(RuleSetMapper.MAX_NESTING_DEPTH);
        String depth33 = "[" + depth32 + "]";

        assertThat(mapper.readTree(depth32).isArray()).isTrue();
        assertThatThrownBy(() -> mapper.readTree(depth33))
                .isInstanceOf(RuleSetFormatException.class)
                .hasMessageContaining("nesting depth");
    }

    @Test
    void documentLargerThanOneMegabyteIsRejectedBeforeMapping() {
        String padding = "x".repeat(RuleSetMapper.MAX_DOCUMENT_CHARS);
        String tooLarge = "{\"description\": \"" + padding + "\"}";

        assertThatThrownBy(() -> mapper.readTree(tooLarge))
                .isInstanceOf(RuleSetFormatException.class)
                .hasMessageContaining("length");
    }

    @Test
    void textThatIsNotJsonIsRejectedWithoutEchoingIt() {
        assertThatThrownBy(() -> mapper.readTree("{\"name\": secret-value}"))
                .isInstanceOfSatisfying(RuleSetFormatException.class, e -> {
                    assertThat(e.pointer()).isEmpty();
                    assertThat(e.getMessage()).isEqualTo("the document is not well-formed JSON");
                });
    }

    static Stream<Path> conformanceFixtures() {
        return Fixtures.conformanceCases().stream();
    }

    static Stream<Arguments> unknownProperties() {
        return Stream.of(
                unknown("the rule set", b -> b.document(d -> d.put("x", 1)), "/x"),
                unknown("a field", b -> b.field("age", f -> f.put("x", 1)), "/fields/0/x"),
                unknown("a field source", b -> b.field("age", f -> ((ObjectNode) f.get("source")).put("x", 1)),
                        "/fields/0/source/x"),
                unknown("the defaults", b -> b.document(d -> ((ObjectNode) d.get("defaults")).put("x", 1)),
                        "/defaults/x"),
                unknown("a rule", b -> b.rule("R-100", r -> r.put("x", 1)), "/rules/2/x"),
                unknown("a comparison", b -> b.rule("R-100", r -> edit(r, "/condition").put("x", 1)),
                        "/rules/2/condition/x"),
                unknown("an all", b -> b.rule("R-110", r -> edit(r, "/condition").put("x", 1)),
                        "/rules/3/condition/x"),
                unknown("an any", b -> b.rule("R-140", r -> r.set("condition", NODES.objectNode()
                                .put("x", 1)
                                .set("any", NODES.arrayNode().add(r.get("condition").deepCopy())))),
                        "/rules/8/condition/x"),
                unknown("a not", b -> b.rule("R-120", r -> edit(r, "/condition").put("x", 1)),
                        "/rules/6/condition/x"),
                unknown("an always", b -> b.rule("R-900", r -> edit(r, "/condition").put("x", 1)),
                        "/rules/19/condition/x"),
                unknown("a function node", b -> b.rule("R-116", r -> edit(r, "/condition/all/1/value").put("x", 1)),
                        "/rules/5/condition/all/1/value/x"),
                unknown("a field reference",
                        b -> b.rule("R-116", r -> edit(r, "/condition/all/1/value/args/1/args/0").put("x", 1)),
                        "/rules/5/condition/all/1/value/args/1/args/0/x"),
                unknown("a decide action", b -> b.rule("R-100", r -> edit(r, "/actions/0").put("x", 1)),
                        "/rules/2/actions/0/x"),
                unknown("a set action", b -> b.rule("R-010", r -> edit(r, "/actions/0").put("x", 1)),
                        "/rules/0/actions/0/x"),
                unknown("a flag action", b -> b.rule("R-410", r -> edit(r, "/actions/0").put("x", 1)),
                        "/rules/17/actions/0/x"),
                unknown("quoted provenance", b -> b.rule("R-100", r -> edit(r, "/provenance").put("x", 1)),
                        "/rules/2/provenance/x"),
                unknown("analyst provenance", b -> b.rule("R-310", r -> edit(r, "/provenance").put("x", 1)),
                        "/rules/14/provenance/x"),
                unknown("pending provenance", b -> b.rule("R-100", r -> r.putObject("provenance")
                                .put("kind", "pending").put("changeRequestId", "cr-1").put("rationale", "why")
                                .put("x", 1)),
                        "/rules/2/provenance/x"),
                unknown("a name that needs escaping", b -> b.rule("R-100", r -> r.put("a/b~c", 1)),
                        "/rules/2/a~1b~0c"));
    }

    static Stream<Arguments> shapeViolations() {
        return Stream.of(
                violation("a required property is missing",
                        b -> b.document(d -> d.remove("name")), "", "missing property name"),
                violation("a number where text is expected",
                        b -> b.document(d -> d.put("name", 5)), "/name", "expected a string"),
                violation("an unknown enum value",
                        b -> b.document(d -> d.put("language", "fr")), "/language", "unknown value"),
                violation("an object where an array is expected",
                        b -> b.document(d -> d.putObject("fields")), "/fields", "expected an array"),
                violation("a field that is not an object",
                        b -> b.document(d -> ((ArrayNode) d.get("fields")).set(0, "age")), "/fields/0",
                        "expected an object"),
                violation("a string where an integer is expected",
                        b -> b.rule("R-100", r -> r.put("priority", "100")), "/rules/2/priority",
                        "expected an integer"),
                violation("a fraction where an integer is expected",
                        b -> b.rule("R-100", r -> r.put("priority", new BigDecimal("100.5"))), "/rules/2/priority",
                        "expected an integer"),
                violation("a string where a number is expected",
                        b -> b.field("age", f -> f.put("minimum", "0")), "/fields/0/minimum", "expected a number"),
                violation("a string where a boolean is expected",
                        b -> b.field("age", f -> f.put("required", "yes")), "/fields/0/required",
                        "expected a boolean"),
                violation("a field source that is not quoted",
                        b -> b.field("age", f -> ((ObjectNode) f.get("source")).put("kind", "analyst")),
                        "/fields/0/source/kind", "expected quoted"),
                violation("a condition of no known shape",
                        b -> b.rule("R-900", r -> r.putObject("condition")), "/rules/19/condition",
                        "expected a comparison, all, any, not or always"),
                violation("always set to false",
                        b -> b.rule("R-900", r -> r.putObject("condition").put("always", false)),
                        "/rules/19/condition/always", "expected true"),
                violation("always set to a string",
                        b -> b.rule("R-900", r -> r.putObject("condition").put("always", "true")),
                        "/rules/19/condition/always", "expected true"),
                violation("a null in a literal list",
                        b -> b.rule("R-120", r -> ((ArrayNode) r.at("/condition/not/value")).setNull(0)),
                        "/rules/6/condition/not/value/0", "expected a number, a string or a boolean"),
                violation("a string as a function argument",
                        b -> b.rule("R-116", r -> ((ArrayNode) r.at("/condition/all/1/value/args")).set(0, "78")),
                        "/rules/5/condition/all/1/value/args/0", "expected a number, a field reference or a function"),
                violation("an action that is not an object",
                        b -> b.rule("R-100", r -> r.putArray("actions").add("reject")), "/rules/2/actions/0",
                        "expected an object"),
                violation("an unknown action type",
                        b -> b.rule("R-100", r -> edit(r, "/actions/0").put("type", "notify")),
                        "/rules/2/actions/0/type", "expected decide, set or flag"),
                violation("a provenance that is not an object",
                        b -> b.rule("R-100", r -> r.put("provenance", "quoted")), "/rules/2/provenance",
                        "expected an object"),
                violation("an unknown provenance kind",
                        b -> b.rule("R-100", r -> edit(r, "/provenance").put("kind", "model")),
                        "/rules/2/provenance/kind", "expected quoted, analyst or pending"));
    }

    private static Arguments unknown(String name, Consumer<RuleSetBuilder> change, String pointer) {
        return arguments(name, change, pointer);
    }

    private static Arguments violation(String name, Consumer<RuleSetBuilder> change, String pointer, String message) {
        return arguments(name, change, pointer, message);
    }

    private static ObjectNode edit(ObjectNode rule, String pointer) {
        return (ObjectNode) rule.at(pointer);
    }

    private static NumberLiteral number(String value) {
        return new NumberLiteral(new BigDecimal(value));
    }

    private static Rule rule(RuleSet ruleSet, String id) {
        return ruleSet.rules().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    private static Field field(RuleSet ruleSet, String name) {
        return ruleSet.fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }
}
