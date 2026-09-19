package com.liorshaya.policypilot.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.support.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unicode normalization of policy text at the boundary (Document 5, Input Validation, policy text row, and the Bidi
 * paragraph; the basis of RT-09 on day 7). Document 6's layout names this class.
 */
@Requirement({"FR-1", "NFR-5"})
class InputNormalizerTest {

    // Expected: Unicode NFC composes e + U+0301 into U+00E9
    @Test
    void textIsNormalizedToNfc() {
        assertThat(InputNormalizer.normalize("café")).isEqualTo("café");
    }

    // Expected: the list of Document 5, policy text row
    @ParameterizedTest(name = "U+{0}")
    @ValueSource(strings = {"202A", "202B", "202C", "202D", "202E", "2066", "2067", "2068", "2069", "200B", "200C",
        "200D", "FEFF"})
    void formatCharactersAreStripped(String hex) {
        String formatCharacter = Character.toString(Integer.parseInt(hex, 16));

        assertThat(InputNormalizer.normalize("app" + formatCharacter + "rove")).isEqualTo("approve");
    }

    @Test
    void newlineAndTabAreKept() {
        assertThat(InputNormalizer.normalize("a\tb\n\nc")).isEqualTo("a\tb\n\nc");
    }

    // Expected: Document 5, policy text row: CRLF or a lone CR become newlines
    @Test
    void carriageReturnsBecomeNewlines() {
        assertThat(InputNormalizer.normalize("a\r\n\r\nb\rc")).isEqualTo("a\n\nb\nc");
    }

    @ParameterizedTest(name = "U+{0}")
    @ValueSource(strings = {"0000", "0007", "001B", "007F", "0085"})
    void otherControlCharactersAreRejected(String hex) {
        String control = Character.toString(Integer.parseInt(hex, 16));

        assertThatThrownBy(() -> InputNormalizer.normalize("a" + control + "b"))
                .isInstanceOfSatisfying(InputRejectedException.class,
                        e -> assertThat(e.problem()).isEqualTo("contains a control character"));
    }

    // Expected: this text is already NFC (checked with Python's unicodedata.normalize), and only the quote comparison
    // of Document 3 strips niqqud and punctuation, never the stored text
    @Test
    void hebrewNiqqudAndPunctuationArePreserved() {
        String text = "שָׁלוֹם עוֹלָם, ש\"ח – מַקָּף־גֵּרֵשׁ׳";

        assertThat(InputNormalizer.normalize(text)).isEqualTo(text);
    }

    // Expected: Document 5, RT-09: overrides and zero-width characters hiding "approve" inside "reject" are gone, so
    // the visible and the stored text agree
    @Test
    void bidiOverrideHidingAWordIsRemovedSoStoredAndVisibleTextAgree() {
        String hidden = "reject\u202Eevorppa\u202C\u200B";

        assertThat(InputNormalizer.normalize(hidden)).isEqualTo("rejectevorppa");
    }

    @Test
    void anEmptyTextStaysEmpty() {
        assertThat(InputNormalizer.normalize("")).isEmpty();
    }
}
