package com.liorshaya.policypilot.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The cheat sheet the author prompt is given (Document 4: generated from the schema so it cannot drift). Every
 * value asserted here is one Document 3 defines and the committed schema declares.
 */
class DslCheatSheetTest {

    private final String sheet = new DslCheatSheet().text();

    @Test
    void namesEveryComparisonOperatorOfDocument3() {
        assertThat(sheet)
                .contains("eq")
                .contains("ne")
                .contains("lt")
                .contains("lte")
                .contains("gt")
                .contains("gte")
                .contains("in")
                .contains("not_in")
                .contains("between")
                .contains("matches")
                .contains("present")
                .contains("absent");
    }

    @Test
    void namesEveryFieldTypeAndActionTypeAndOutcome() {
        assertThat(sheet).contains("FIELD TYPES: number, integer, boolean, string, enum, date.");
        assertThat(sheet).contains("ACTION TYPES: decide, set, flag.");
        assertThat(sheet).contains("decide takes outcome (approve, reject, refer)");
    }

    @Test
    void namesTheArithmeticTheEngineCanEvaluate() {
        assertThat(sheet).contains("EXPRESSION FUNCTIONS: ").contains("div").contains("round").contains("pow");
    }

    @Test
    void tellsTheModelItMayOnlyProduceQuotedProvenance() {
        assertThat(sheet)
                .contains("PROVENANCE KINDS: quoted, analyst, pending.")
                .contains("You may only produce quoted")
                .contains("analyst and pending are set by people");
    }

    @Test
    void staysAsShortAsDocument4Asks() {
        // "a 60-line summary of Document 3"
        assertThat(sheet.lines().count()).isLessThanOrEqualTo(60);
    }
}
