package com.liorshaya.policypilot.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import org.junit.jupiter.api.Test;

/**
 * A short text as the chat, retrieval and change requests take it (Document 5, Input limits: "Chat message, retrieval
 * question: 2 KB", the same Unicode normalization as policy text, and "Change request: 2 KB, same as chat message").
 * 2 KB is counted in UTF-8 bytes, so a Hebrew letter counts twice.
 */
class ShortTextTest {

    // Document 5: format characters are stripped and the text is NFC. Expected: the words without the zero-width
    // space and without the spaces around them
    @Test
    void aTextIsNormalizedAndStripped() {
        assertThat(ShortText.of("  \u200Bמה התקופה?  ", "/question")).isEqualTo("מה התקופה?");
    }

    // Expected: 1,024 Hebrew letters are 2,048 bytes and pass; one more letter does not
    @Test
    void twoKilobytesOfUtf8IsTheLimit() {
        assertThat(ShortText.of("א".repeat(1024), "/question")).hasSize(1024);
        assertThatThrownBy(() -> ShortText.of("א".repeat(1025), "/question"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.REQUEST_INVALID));
    }

    // Expected: nothing, blanks only, and a control character are all REQUEST_INVALID
    @Test
    void anEmptyTextOrAControlCharacterIsRefused() {
        assertThatThrownBy(() -> ShortText.of(null, "/question")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ShortText.of("   ", "/question")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ShortText.of("why\u0007", "/question")).isInstanceOf(ApiException.class);
    }

    // Document 2, the change route's body is {text}. Expected: the refusal names /text, the pointer it was given
    @Test
    void aRefusalNamesThePointerOfTheText() {
        assertThatThrownBy(() -> ShortText.of("   ", "/text"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.details()).containsExactly(new ErrorDetail("/text", "is empty")));
    }
}
