package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rag.service.QuestionSignals;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The names a conversation carries into a follow-up (Document 4, Retrieval Pipeline, Follow-up): those of the latest
 * question that names any, kept while each answer since cites them. The questions are the owner's of 2026-10-04 and
 * the demo's (Document 4, Scripted demo questions), on the lending rule set.
 */
@Requirement({"FR-13", "FR-16"})
class CarriedNamesTest {

    private static final RuleSet LENDING = new RuleSetMapper().toRuleSet(Fixtures.lendingV1());
    private static final String EXPLAIN_R410 = "תסביר לי את כלל R-410";
    private static final String WHEN_FLAGGED = "ומה קורה כאשר הכלל הזה מסומן?";

    // Document 4, Follow-up: "the rule ids, field names and decision number of the latest ... question that names
    // any". Expected: R-410, the rule the question before the follow-up named
    @Test
    void theQuestionBeforeNamesTheRuleAFollowUpIsAbout() {
        QuestionSignals carried = CarriedNames.of(List.of(turn(EXPLAIN_R410, rule("R-410"))), LENDING);

        assertThat(carried.ruleIds()).containsExactly("R-410");
        assertThat(carried.namesSomething()).isTrue();
    }

    // Document 4, Follow-up: a later turn whose question names nothing keeps the rule ids its answer cites. Expected:
    // R-410 still carried into a third question, and not R-900, which only the answer brought up
    @Test
    void theNamesAreKeptWhileEachAnswerSinceCitesThem() {
        QuestionSignals carried = CarriedNames.of(List.of(turn(EXPLAIN_R410, rule("R-410")),
                turn(WHEN_FLAGGED, rule("R-410"), rule("R-900"))), LENDING);

        assertThat(carried.ruleIds()).containsExactly("R-410");
    }

    // Document 4, Follow-up: "an answer that cites none of them ends the carry", and the demo's rate question is
    // still stopped. Expected: after Q-01, Q-02 and Q-03 as the live site answers them, nothing is carried
    @Test
    void anAnswerThatCitesNoneOfThemEndsTheCarry() {
        QuestionSignals carried = CarriedNames.of(List.of(
                turn("למה בקשה מספר 17 הופנתה לבדיקה?", decision(17), paragraph(7), rule("R-330")),
                turn("האם בקשה 17 הייתה מאושרת אם היה ערב?", simulation(), rule("R-900"), paragraph(9)),
                turn("מהי תקופת ההחזר המקסימלית להלוואה?", paragraph(2), rule("R-130"))), LENDING);

        assertThat(carried.namesSomething()).isFalse();
    }

    // Document 4, Follow-up: "the latest ... question that names any". Expected: R-320 alone once a later question
    // names it
    @Test
    void aLaterQuestionThatNamesSomethingReplacesTheNames() {
        QuestionSignals carried = CarriedNames.of(List.of(turn(EXPLAIN_R410, rule("R-410")),
                turn("מה עושה הכלל R-320?", rule("R-320"))), LENDING);

        assertThat(carried.ruleIds()).containsExactly("R-320");
    }

    // Document 4, Follow-up: a later turn keeps only rule ids and the decision, so a field name is carried into the
    // next question and no further. Expected: has_guarantor after its question, nothing one turn later
    @Test
    void aFieldNameIsCarriedOneTurnOnly() {
        Turns turns = new Turns().add(turn("what is has_guarantor for?", rule("R-900")));

        assertThat(CarriedNames.of(turns.list(), LENDING).fieldNames()).containsExactly("has_guarantor");
        assertThat(CarriedNames.of(turns.add(turn("and why?", rule("R-900"))).list(), LENDING).namesSomething())
                .isFalse();
    }

    // Document 4, Follow-up: the decision is kept "when its answer cites a decision or a simulation". Expected:
    // carried after Q-01, through an answer citing the decision and one citing a simulation, and dropped by an
    // answer citing a paragraph alone
    @Test
    void aDecisionIsKeptWhileTheAnswersCiteADecisionOrASimulation() {
        Turns turns = new Turns().add(turn("למה בקשה מספר 17 הופנתה לבדיקה?", decision(17), paragraph(7)));

        assertThat(CarriedNames.of(turns.list(), LENDING).namesADecision()).isTrue();
        assertThat(CarriedNames.of(turns.add(turn("ולמה?", decision(17))).list(), LENDING).namesADecision()).isTrue();
        assertThat(CarriedNames.of(turns.add(turn("ועם ערב?", simulation())).list(), LENDING).namesADecision())
                .isTrue();
        assertThat(CarriedNames.of(turns.add(turn("ומה עוד?", paragraph(7))).list(), LENDING).namesADecision())
                .isFalse();
    }

    // Document 4, Follow-up: "of the last 10 turns' questions". Expected: nothing carried when the question that
    // named R-410 is eleven turns back, although every answer since cites it
    @Test
    void onlyTheLastTenTurnsAreRead() {
        Turns turns = new Turns().add(turn(EXPLAIN_R410, rule("R-410")));
        for (int i = 0; i < 9; i++) {
            turns.add(turn("ומה עוד?", rule("R-410")));
        }

        assertThat(CarriedNames.of(turns.list(), LENDING).ruleIds()).containsExactly("R-410");
        assertThat(CarriedNames.of(turns.add(turn("ומה עוד?", rule("R-410"))).list(), LENDING).namesSomething())
                .isFalse();
    }

    // Document 4, Follow-up: the names are carried, not the numbers asked with them, and they count twice in the
    // lexical rank as the Query row normalizes them. Expected: r320 and the field, without 8000
    @Test
    void theNumbersAskedWithTheNamesAreNotCarried() {
        QuestionSignals carried = CarriedNames.of(
                List.of(turn("R-320 and monthly_income over 8,000?", rule("R-320"))), LENDING);

        assertThat(carried.strongTerms()).isEqualTo("r320 monthly_income");
    }

    // Expected: a new session carries nothing
    @Test
    void aNewSessionCarriesNothing() {
        assertThat(CarriedNames.of(List.of(), LENDING).namesSomething()).isFalse();
    }

    private static CarriedNames.Turn turn(String question, ChatCitation... cited) {
        return new CarriedNames.Turn(question, List.of(cited));
    }

    private static ChatCitation rule(String ruleId) {
        return new ChatCitation("r:" + ruleId, ChatCitation.Kind.RULE, null, ruleId, null, null, null, null);
    }

    private static ChatCitation paragraph(int index) {
        return new ChatCitation("p:" + index, ChatCitation.Kind.PARAGRAPH, index, null, null, null, null, null);
    }

    private static ChatCitation decision(int applicationNumber) {
        return new ChatCitation("d:" + applicationNumber, ChatCitation.Kind.DECISION, null, null, null,
                applicationNumber, "refer", null);
    }

    private static ChatCitation simulation() {
        return new ChatCitation("sim:17:has_guarantor=true", ChatCitation.Kind.SIMULATION, null, null, null, 17,
                "approve", "has_guarantor=true");
    }

    /** A session growing by one turn at a time, so a test reads what is carried after each. */
    private static final class Turns {

        private final List<CarriedNames.Turn> turns = new ArrayList<>();

        Turns add(CarriedNames.Turn turn) {
            turns.add(turn);
            return this;
        }

        List<CarriedNames.Turn> list() {
            return List.copyOf(turns);
        }
    }
}
