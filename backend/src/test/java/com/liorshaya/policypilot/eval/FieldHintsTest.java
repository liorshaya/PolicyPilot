package com.liorshaya.policypilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import org.junit.jupiter.api.Test;

/**
 * Document 4, Prompt 1: Author, Field hints: "one line per input, {@code - <name> (<type>[, <unit>][: <value>,
 * <value>])}, under the line {@code The application supplies these inputs:}, and it names inputs only". Each expected
 * text is written out by hand from that sentence and the fixture's fields.
 */
@Requirement("FR-22")
class FieldHintsTest {

    // Expected: the seven inputs of the second domain, with their units, and not the derived discount_rate
    @Test
    void theSecondDomainsInputsWithTheirUnitsAndNoDerivedField() {
        String hints = FieldHints.of(Fixtures.json("eval/policies/arnona-discount-seniors/expected.ruleset.json"));

        assertThat(hints).isEqualTo("""
                The application supplies these inputs:
                - age (integer, years)
                - receives_old_age_pension (boolean)
                - receives_income_supplement (boolean)
                - apartment_count (integer)
                - area_sqm (number, sqm)
                - municipal_debt (number, ILS)
                - submission_date (date)""");
    }

    // Expected: the nine inputs of the lending policy, the enum with its values, and none of its two derived fields
    @Test
    void theLendingInputsWithTheEnumsValues() {
        String hints = FieldHints.of(Fixtures.lendingV1());

        assertThat(hints).isEqualTo("""
                The application supplies these inputs:
                - age (integer, years)
                - requested_amount (number, ILS)
                - term_months (integer, months)
                - employment_type (enum: salaried, self_employed, retired, unemployed)
                - employment_months (integer, months)
                - monthly_income (number, ILS)
                - existing_monthly_debt (number, ILS)
                - credit_events_24m (integer)
                - has_guarantor (boolean)""");
    }
}
