package com.liorshaya.policypilot.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.liorshaya.policypilot.policy.service.ParagraphSplitter;
import com.liorshaya.policypilot.policy.service.PolicyTextException;
import com.liorshaya.policypilot.policy.service.PolicyTextLimits;
import com.liorshaya.policypilot.support.Requirement;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Policy text limits (Document 5, Input Validation: 40 KB as 40,960 bytes of UTF-8 after normalization, 200
 * paragraphs, 4,000 characters as code points per paragraph; the basis of RT-10 on day 7).
 */
@Requirement("FR-1")
class PolicyTextLimitsTest {

    /** Ten paragraphs of 4,000 ASCII letters with nine blank-line separators: 40,018 bytes. */
    private static final String TEN_FULL_PARAGRAPHS = paragraphsOfLength(10, 4_000);

    @Test
    void textOf40960BytesIsAccepted() {
        String text = TEN_FULL_PARAGRAPHS + "\n\n" + "b".repeat(40_960 - 40_018 - 2);

        assertThat(text.getBytes(StandardCharsets.UTF_8)).hasSize(40_960);
        assertThat(check(text)).isEmpty();
    }

    @Test
    void textOneByteOver40960IsRejected() {
        String text = TEN_FULL_PARAGRAPHS + "\n\n" + "b".repeat(40_960 - 40_018 - 1);

        assertThat(text.getBytes(StandardCharsets.UTF_8)).hasSize(40_961);
        assertThat(check(text)).extracting(PolicyTextException.Violation::path, PolicyTextException.Violation::problem)
                .containsExactly(tuple("/text", "is longer than 40 KB"));
    }

    // Expected: a Hebrew letter is two bytes of UTF-8, so 20,500 letters are 41,000 bytes and over the limit
    @Test
    void hebrewTextIsMeasuredInUtf8Bytes() {
        String text = paragraphsOf("א".repeat(4_000), 5) + "\n\n" + "א".repeat(500);

        assertThat(check(text)).extracting(PolicyTextException.Violation::problem).containsExactly("is longer than 40 KB");
    }

    @Test
    void twoHundredParagraphsAreAccepted() {
        assertThat(check(paragraphsOfLength(200, 10))).isEmpty();
    }

    @Test
    void twoHundredAndOneParagraphsAreRejected() {
        assertThat(check(paragraphsOfLength(201, 10))).extracting(PolicyTextException.Violation::problem)
                .containsExactly("has more than 200 paragraphs");
    }

    @Test
    void paragraphOf4000CharactersIsAccepted() {
        assertThat(check("x\n\n" + "a".repeat(4_000))).isEmpty();
    }

    @Test
    void paragraphOf4001CharactersIsRejectedWithItsPointer() {
        assertThat(check("x\n\n" + "a".repeat(4_001)))
                .extracting(PolicyTextException.Violation::path, PolicyTextException.Violation::problem)
                .containsExactly(tuple("/paragraphs/2", "is longer than 4,000 characters"));
    }

    // Expected: Document 5 counts characters; a character outside the BMP is one code point and two Java chars
    @Test
    void charactersAreCountedAsCodePoints() {
        assertThat(check("\uD83D\uDE00".repeat(4_000))).isEmpty();
    }

    @Test
    void emptyTextIsRejected() {
        assertThat(check("  \n\n  ")).extracting(PolicyTextException.Violation::problem).containsExactly("has no paragraph");
    }

    private static List<PolicyTextException.Violation> check(String text) {
        return PolicyTextLimits.check(text, ParagraphSplitter.split(text));
    }

    private static String paragraphsOfLength(int count, int length) {
        return paragraphsOf("a".repeat(length), count);
    }

    private static String paragraphsOf(String paragraph, int count) {
        return String.join("\n\n", Collections.nCopies(count, paragraph));
    }
}
