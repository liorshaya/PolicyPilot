package com.liorshaya.policypilot.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * A question as the chat and retrieval take it (Document 5, Input limits: "Chat message, retrieval question: 2 KB",
 * the same Unicode normalization as policy text). 2 KB is counted in UTF-8 bytes, so a Hebrew letter counts twice.
 */
class QuestionTextTest {

    // Document 5: format characters are stripped and the text is NFC. Expected: the words without the zero-width
    // space and without the spaces around them
    @Test
    void aQuestionIsNormalizedAndStripped() {
        assertThat(QuestionText.of("  \u200Bמה התקופה?  ")).isEqualTo("מה התקופה?");
    }

    // Expected: 1,024 Hebrew letters are 2,048 bytes and pass; one more letter does not
    @Test
    void twoKilobytesOfUtf8IsTheLimit() {
        assertThat(QuestionText.of("א".repeat(1024))).hasSize(1024);
        assertThatThrownBy(() -> QuestionText.of("א".repeat(1025)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.REQUEST_INVALID));
    }

    // Expected: nothing, blanks only, and a control character are all REQUEST_INVALID
    @Test
    void anEmptyQuestionOrAControlCharacterIsRefused() {
        assertThatThrownBy(() -> QuestionText.of(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> QuestionText.of("   ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> QuestionText.of("why\u0007")).isInstanceOf(ApiException.class);
    }
}
