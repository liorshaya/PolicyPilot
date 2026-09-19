package com.liorshaya.policypilot.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.policy.service.ParagraphSplitter;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Paragraph splitting (Document 5, policy text: split on blank lines, a line of spaces or tabs only is blank;
 * fixtures/README.md: the paragraph index is the position). Expected counts come from running the Python reference's
 * {@code load_paragraphs} on the committed files; expected texts from Document 3's table of the demo policy.
 */
@Requirement({"FR-1", "NFR-5"})
class ParagraphSplitterTest {

    // Expected: Document 3, the demo policy is nine Hebrew paragraphs; Work Plan day 4 done-when
    @Test
    void lendingPolicySplitsIntoNineParagraphs() throws IOException {
        assertThat(split("policies/consumer-lending/policy.he.md")).hasSize(9);
    }

    // Expected: Document 3, the table of the demo policy, paragraphs 1 and 9
    @Test
    void lendingParagraphsAreDocument3sParagraphs() throws IOException {
        List<String> paragraphs = split("policies/consumer-lending/policy.he.md");

        assertThat(paragraphs.getFirst()).isEqualTo("הלוואה אישית תינתן ליחיד שגילו 21 עד 70 בעת הגשת הבקשה.");
        assertThat(paragraphs.getLast())
                .isEqualTo("בקשה העומדת בכל התנאים לעיל תאושר. בקשה שלא הוכרעה על ידי הכללים לעיל תועבר לבדיקה ידנית.");
    }

    // Expected: the Python reference's load_paragraphs on each committed policy file, run on 2026-09-19
    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
        "eval/policies/arnona-discount-income/policy.he.md, 8",
        "eval/policies/arnona-discount-seniors/policy.he.md, 7",
        "eval/policies/consumer-lending-en/policy.en.md, 8",
        "eval/policies/consumer-lending-guarantor/policy.he.md, 9",
        "eval/policies/consumer-lending-strict/policy.he.md, 10",
        "eval/policies/consumer-lending/policy.he.md, 9",
        "eval/policies/municipal-tax-discount-en/policy.en.md, 8",
        "eval/policies/rental-deposit-en/policy.en.md, 7",
        "eval/policies/rental-deposit-return/policy.he.md, 7",
        "eval/policies/scholarship-en/policy.en.md, 8",
        "eval/policies/scholarship-merit/policy.he.md, 8",
        "eval/policies/scholarship-need/policy.he.md, 8",
        "eval/policies/synthetic-derived-chain/policy.he.md, 7",
        "eval/policies/synthetic-enums/policy.en.md, 9",
        "eval/policies/synthetic-referral-first/policy.he.md, 5",
        "eval/policies/synthetic-retirees-dates/policy.he.md, 6",
        "eval/policies/warranty-claim-electronics/policy.he.md, 7",
        "eval/policies/warranty-claim-en/policy.en.md, 7",
        "policies/consumer-lending/policy.he.md, 9"})
    void everyFixturePolicySplitsIntoTheReferenceCount(String file, int paragraphs) throws IOException {
        assertThat(split(file)).hasSize(paragraphs);
    }

    @Test
    void runsOfBlankLinesProduceNoEmptyParagraph() {
        assertThat(ParagraphSplitter.split("\n\none\n\n\n\n\ntwo\n\n")).containsExactly("one", "two");
    }

    // Expected: Document 5, policy text row: a line of spaces or tabs only is blank
    @Test
    void whitespaceOnlyLineSeparatesParagraphs() {
        assertThat(ParagraphSplitter.split("one\n \t \ntwo")).containsExactly("one", "two");
    }

    @Test
    void aSingleNewlineStaysInsideAParagraph() {
        assertThat(ParagraphSplitter.split("first line\nsecond line")).containsExactly("first line\nsecond line");
    }

    @Test
    void paragraphsAreTrimmed() {
        assertThat(ParagraphSplitter.split("  one  \n\n\ttwo\t")).containsExactly("one", "two");
    }

    @Test
    void blankTextHasNoParagraph() {
        assertThat(ParagraphSplitter.split(" \n\n \n")).isEmpty();
    }

    private static List<String> split(String file) throws IOException {
        return ParagraphSplitter.split(Files.readString(Fixtures.path(file)));
    }
}
