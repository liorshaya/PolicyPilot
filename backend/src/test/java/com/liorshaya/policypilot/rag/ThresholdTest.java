package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rag.service.NotCoveredSentences;
import com.liorshaya.policypilot.rag.service.NotCoveredThreshold;
import com.liorshaya.policypilot.rag.service.QuestionSignals;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.Language;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import org.junit.jupiter.api.Test;

/**
 * The not-covered threshold (Document 4, Retrieval Pipeline, Threshold; Brief FR-15): a question whose best chunk is
 * below 0.35 cosine and that names no rule id, field name or decision number of the version gets the fixed sentence of
 * its language, without a model call. That no model is called is also structural: {@code rag} cannot reach
 * {@code LlmGateway} ({@code PackageRulesTest}).
 */
@Requirement("FR-15")
class ThresholdTest {

    private static final RuleSet LENDING = new RuleSetMapper().toRuleSet(Fixtures.lendingV1());
    private final NotCoveredThreshold threshold = new NotCoveredThreshold(0.35);

    // Document 4: "below 0.35 ... and the question names no ...". Expected: not covered
    @Test
    void anOffCorpusQuestionIsNotCovered() {
        assertThat(threshold.covers(0.2, QuestionSignals.of("האם יש הנחה לחיילים משוחררים?", LENDING))).isFalse();
    }

    // Document 4: "below 0.35", so 0.35 itself is covered. Expected: covered at 0.35, not at 0.3499
    @Test
    void theThresholdItselfIsCovered() {
        QuestionSignals nothing = QuestionSignals.of("?", LENDING);

        assertThat(threshold.covers(0.35, nothing)).isTrue();
        assertThat(threshold.covers(0.3499, nothing)).isFalse();
    }

    // Document 4: a rule id, a field name or a decision number keeps a low-scoring question covered. Expected: covered
    @Test
    void aNamedRuleFieldOrDecisionKeepsALowScoringQuestionCovered() {
        assertThat(threshold.covers(0.1, QuestionSignals.of("מה עושה הכלל R-320?", LENDING))).isTrue();
        assertThat(threshold.covers(0.1, QuestionSignals.of("what is has_guarantor for?", LENDING))).isTrue();
        assertThat(threshold.covers(0.1, QuestionSignals.of("למה בקשה מספר 17 הופנתה לבדיקה?", LENDING))).isTrue();
    }

    // Document 4, Threshold: the fixed answer for each language, word for word
    @Test
    void theFixedSentenceIsDocumentFoursForEachLanguage() {
        NotCoveredSentences sentences = NotCoveredSentences.load();

        assertThat(sentences.of(Language.EN)).isEqualTo(
                "The documents do not cover this question; try asking about a rule, a paragraph or a decision number.");
        assertThat(sentences.of(Language.HE))
                .isEqualTo("המסמכים אינם עוסקים בשאלה הזו; אפשר לשאול על כלל, על סעיף או על מספר בקשה.");
    }
}
