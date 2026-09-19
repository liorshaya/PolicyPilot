package com.liorshaya.policypilot.engine;

import static com.liorshaya.policypilot.engine.EngineFixtures.ENGINE;
import static com.liorshaya.policypilot.engine.EngineFixtures.LENDING;
import static com.liorshaya.policypilot.engine.EngineFixtures.compile;
import static com.liorshaya.policypilot.engine.EngineFixtures.object;
import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Function;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Outcome;
import com.liorshaya.policypilot.support.Requirement;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import tools.jackson.databind.node.ObjectNode;

/**
 * Properties of the engine over random inputs (Document 6, Backend Test Design; Document 7, day 3). The seed is fixed,
 * so every run draws the same inputs (Document 6: random data comes from a seeded generator). Lending cases are drawn
 * inside the field domains, so every one is a valid case.
 */
@Requirement({"FR-8", "NFR-1"})
class EnginePropertiesTest {

    private static final String SEED = "20260919";
    private static final int LENDING_RULES = 20;

    @Property(seed = SEED)
    void randomValidCasesNeverThrow(@ForAll("lendingCases") ObjectNode input) {
        Evaluation evaluation = ENGINE.evaluate(LENDING, input);

        assertThat(evaluation).isInstanceOf(Decision.class);
        assertThat(((Decision) evaluation).status()).isEqualTo(Decision.Status.OK);
    }

    @Property(seed = SEED)
    void traceHasOneStepPerRule(@ForAll("lendingCases") ObjectNode input) {
        Decision decision = (Decision) ENGINE.evaluate(LENDING, input);

        assertThat(decision.trace()).hasSize(LENDING_RULES);
        assertThat(decision.trace()).extracting(TraceStep::ruleId).doesNotHaveDuplicates();
    }

    @Property(seed = SEED)
    void nothingFiresAfterATerminalRule(@ForAll("lendingCases") ObjectNode input) {
        Decision decision = (Decision) ENGINE.evaluate(LENDING, input);
        List<TraceStep> trace = decision.trace();
        int deciding = trace.indexOf(trace.stream().filter(step -> step.ruleId().equals(decision.decidingRuleId()))
                .findFirst().orElseThrow());

        assertThat(trace.get(deciding).status()).isEqualTo(TraceStep.Status.FIRED);
        assertThat(trace.subList(deciding + 1, trace.size()))
                .allSatisfy(step -> assertThat(step.status()).isEqualTo(TraceStep.Status.SKIPPED));
    }

    /**
     * Document 3, Worked Example: the installment is A x 0.0075 / (1 - 1.0075^-n) at 2 places and the ratio is
     * (debt + installment) / income at 4 places, written here from the formula with Document 3's decimal rules
     * (a negative power and a division at 12 places, HALF_EVEN).
     */
    @Property(seed = SEED)
    void derivedValuesEqualTheSpitzerFormula(@ForAll("lendingCases") ObjectNode input) {
        BigDecimal amount = input.get("requested_amount").decimalValue();
        int term = input.get("term_months").intValue();
        BigDecimal income = input.get("monthly_income").decimalValue();
        BigDecimal debt = input.get("existing_monthly_debt").decimalValue();
        BigDecimal discount = BigDecimal.ONE.divide(new BigDecimal("1.0075").pow(term), 12, RoundingMode.HALF_EVEN);
        BigDecimal installment = amount.multiply(new BigDecimal("0.0075"))
                .divide(BigDecimal.ONE.subtract(discount), 12, RoundingMode.HALF_EVEN)
                .setScale(2, RoundingMode.HALF_EVEN);

        Map<String, Object> derived = ((Decision) ENGINE.evaluate(LENDING, input)).derived();

        assertThat((BigDecimal) derived.get("monthly_installment")).isEqualByComparingTo(installment);
        if (income.signum() > 0) {
            BigDecimal ratio = debt.add(installment).divide(income, 12, RoundingMode.HALF_EVEN)
                    .setScale(4, RoundingMode.HALF_EVEN);
            assertThat((BigDecimal) derived.get("debt_to_income")).isEqualByComparingTo(ratio);
        } else {
            assertThat(derived.get("debt_to_income")).isNull();
        }
    }

    /** Document 3, step 6: with no terminal rule, the most severe candidate wins, the first of equals; else the default. */
    @Property(seed = SEED)
    void outcomeIsTheSeverityResolutionWhenNoTerminalFires(@ForAll("candidateOutcomes") List<Outcome> outcomes) {
        List<String> rules = new ArrayList<>();
        for (int i = 0; i < outcomes.size(); i++) {
            rules.add("""
                    {"id": "R-%d", "label": "candidate %d", "priority": %d, "condition": {"always": true},
                     "actions": [{"type": "decide", "outcome": "%s", "terminal": false, "reason": "candidate"}],
                     "provenance": {"kind": "analyst", "note": "property rule", "actor": "test"}}"""
                    .formatted(100 + i, i, 100 + i, outcomes.get(i).json()));
        }
        CompiledRuleSet candidates = compile(object("""
                {"dslVersion": "1.0", "id": "candidates", "name": "Candidates", "language": "en",
                 "fields": [{"name": "x", "type": "number"}],
                 "defaults": {"outcome": "refer", "reason": "nothing decided"}, "rules": [%s]}"""
                .formatted(String.join(", ", rules))));

        Decision decision = (Decision) ENGINE.evaluate(candidates, object("{}"));

        Outcome expected = Outcome.REFER;
        String deciding = null;
        for (int rank : new int[] {3, 2, 1}) {
            for (int i = 0; i < outcomes.size() && deciding == null; i++) {
                if (severity(outcomes.get(i)) == rank) {
                    expected = outcomes.get(i);
                    deciding = "R-" + (100 + i);
                }
            }
        }
        assertThat(decision.outcome()).isEqualTo(expected);
        assertThat(decision.decidingRuleId()).isEqualTo(deciding);
        assertThat(decision.terminal()).isFalse();
    }

    @Property(seed = SEED)
    void evaluatingTwiceGivesIdenticalBytes(@ForAll("lendingCases") ObjectNode input) {
        assertThat(DecisionJson.canonical(ENGINE.evaluate(LENDING, input)))
                .isEqualTo(DecisionJson.canonical(ENGINE.evaluate(LENDING, input.deepCopy())));
    }

    /** Document 3, Decimal semantics: the evaluator agrees with BigDecimal arithmetic done by hand. */
    @Property(seed = SEED)
    void arithmeticIsExact(@ForAll("decimals") BigDecimal a, @ForAll("decimals") BigDecimal b) {
        ExpressionEvaluator evaluator = new ExpressionEvaluator(Map.of());

        assertThat(evaluate(evaluator, Function.ADD, a, b)).isEqualByComparingTo(a.add(b));
        assertThat(evaluate(evaluator, Function.SUB, a, b)).isEqualByComparingTo(a.subtract(b));
        assertThat(evaluate(evaluator, Function.MUL, a, b)).isEqualByComparingTo(a.multiply(b));
        if (b.signum() != 0) {
            assertThat(evaluate(evaluator, Function.DIV, a, b))
                    .isEqualByComparingTo(a.divide(b, 12, RoundingMode.HALF_EVEN));
        }
    }

    @Provide
    Arbitrary<ObjectNode> lendingCases() {
        Arbitrary<Integer> age = Arbitraries.integers().between(0, 120);
        Arbitrary<BigDecimal> amount = Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("200000"))
                .ofScale(2);
        Arbitrary<Integer> term = Arbitraries.integers().between(1, 120);
        Arbitrary<String> employment = Arbitraries.of("salaried", "self_employed", "retired", "unemployed");
        Arbitrary<Integer> months = Arbitraries.integers().between(0, 400).injectNull(0.2);
        Arbitrary<BigDecimal> income = Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("60000"))
                .ofScale(2);
        Arbitrary<BigDecimal> debt = Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("20000"))
                .ofScale(2);
        Arbitrary<Integer> events = Arbitraries.integers().between(0, 4);
        Arbitrary<Boolean> guarantor = Arbitraries.of(true, false).injectNull(0.5);
        return Combinators.combine(List.of(anything(age), anything(amount), anything(term), anything(employment),
                        anything(months), anything(income), anything(debt), anything(events), anything(guarantor)))
                .as(values -> {
                    ObjectNode input = object("{}");
                    input.put("age", (Integer) values.get(0));
                    input.put("requested_amount", (BigDecimal) values.get(1));
                    input.put("term_months", (Integer) values.get(2));
                    input.put("employment_type", (String) values.get(3));
                    if (values.get(4) != null) {
                        input.put("employment_months", (Integer) values.get(4));
                    }
                    input.put("monthly_income", (BigDecimal) values.get(5));
                    input.put("existing_monthly_debt", (BigDecimal) values.get(6));
                    input.put("credit_events_24m", (Integer) values.get(7));
                    if (values.get(8) != null) {
                        input.put("has_guarantor", (Boolean) values.get(8));
                    }
                    return input;
                });
    }

    @Provide
    Arbitrary<List<Outcome>> candidateOutcomes() {
        return Arbitraries.of(Outcome.values()).list().ofMinSize(0).ofMaxSize(6);
    }

    @Provide
    Arbitrary<BigDecimal> decimals() {
        return Combinators.combine(Arbitraries.bigIntegers().between(BigInteger.valueOf(-1_000_000_000_000L),
                        BigInteger.valueOf(1_000_000_000_000L)), Arbitraries.integers().between(0, 8))
                .as(BigDecimal::new);
    }

    private static Arbitrary<Object> anything(Arbitrary<?> arbitrary) {
        return arbitrary.map(Object.class::cast);
    }

    private static BigDecimal evaluate(ExpressionEvaluator evaluator, Function fn, BigDecimal a, BigDecimal b) {
        return (BigDecimal) evaluator.evaluate(new Call(fn, List.of(new NumberLiteral(a), new NumberLiteral(b))), Map.of());
    }

    private static int severity(Outcome outcome) {
        return switch (outcome) {
            case REJECT -> 3;
            case REFER -> 2;
            case APPROVE -> 1;
        };
    }
}
