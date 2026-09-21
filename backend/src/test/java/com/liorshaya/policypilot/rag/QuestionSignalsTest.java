package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rag.service.QuestionSignals;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * What a question names that retrieval must not miss (Document 4, Retrieval Pipeline, Query, Fusion and Threshold):
 * a rule id of the version, a field name of the version, a decision number, and the numbers the lexical query
 * weighs twice. The questions are fixtures/eval/questions.json's where one fits.
 */
class QuestionSignalsTest {

    private static final RuleSet LENDING = new RuleSetMapper().toRuleSet(Fixtures.lendingV1());

    // Document 4, Fusion: a rule id of the version, in the spellings the normalization accepts. Expected: R-320 for
    // Q-07 and its variants; nothing for R-999, which the lending version does not have
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            מה עושה הכלל R-320?       | R-320
            what does r320 do         | R-320
            and R320 and R-010        | R-010 R-320
            what does R-999 do        | ''
            """)
    void findsTheRuleIdsOfTheVersionTheQuestionNames(String question, String expected) {
        assertThat(String.join(" ", QuestionSignals.of(question, LENDING).ruleIds())).isEqualTo(expected);
    }

    // Document 4, Threshold: "a decision number (an application, case or decision followed by a number)". Expected:
    // Q-01 and Q-02 name decision 17; Q-08's "200 הבקשות" and Q-03's term do not name one
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            למה בקשה מספר 17 הופנתה לבדיקה?               | true
            האם בקשה 17 הייתה מאושרת אם היה ערב?          | true
            why was application 17 referred?              | true
            explain decision #42                          | true
            case no. 3                                    | true
            כמה מתוך 200 הבקשות נדחו?                     | false
            מהי תקופת ההחזר המקסימלית להלוואה?            | false
            """)
    void noticesADecisionNumber(String question, boolean expected) {
        assertThat(QuestionSignals.of(question, LENDING).namesADecision()).isEqualTo(expected);
    }

    // Document 4, Threshold: a field name of the version. Expected: debt_to_income, from the lending fields
    @Test
    void findsTheFieldNamesOfTheVersion() {
        assertThat(QuestionSignals.of("Is debt_to_income computed on net income?", LENDING).fieldNames())
                .containsExactly("debt_to_income");
    }

    // Document 4, Query: rule ids, field names and numbers count twice, in their normalized form. Expected: the rule
    // id as r320, 8,000 as 8000, the field as written
    @Test
    void theStrongTermsAreNormalized() {
        assertThat(QuestionSignals.of("R-320 and monthly_income over 8,000?", LENDING).strongTerms())
                .isEqualTo("r320 monthly_income 8000");
    }

    // Expected: a question that names nothing names nothing
    @Test
    void anOrdinaryQuestionNamesNothing() {
        QuestionSignals signals = QuestionSignals.of("האם יש הנחה לחיילים משוחררים?", LENDING);

        assertThat(signals.namesSomething()).isFalse();
        assertThat(signals.strongTerms()).isEmpty();
    }
}
