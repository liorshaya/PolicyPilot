package com.liorshaya.policypilot.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * The fields of a new policy at the boundary (Document 5, Input Validation; Document 2, error envelope: 422 with
 * JSON pointers, never the offending value). Title rules: required, at most 200 characters after normalization.
 */
class PolicyInputTest {

    @Test
    void validFieldsAreNormalizedAndKept() {
        PolicyInput input = PolicyInput.of("  Lending\u200B policy ", "he", () -> "line one\r\n\r\nline two");

        assertThat(input).isEqualTo(new PolicyInput("Lending policy", PolicyLanguage.HE, "line one\n\nline two"));
    }

    @Test
    void missingTitleAndLanguageAreEachNamed() {
        assertThatThrownBy(() -> PolicyInput.of(null, null, () -> "text"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.POLICY_INVALID);
                    assertThat(e.details()).extracting(ErrorDetail::path, ErrorDetail::problem).containsExactly(
                            tuple("/title", "is required"), tuple("/language", "must be he or en"));
                });
    }

    @Test
    void blankTitleIsRequired() {
        assertThatThrownBy(() -> PolicyInput.of(" \u200B ", "en", () -> "text"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.details())
                        .extracting(ErrorDetail::path, ErrorDetail::problem).containsExactly(tuple("/title", "is required")));
    }

    @Test
    void titleOf200CharactersIsAccepted() {
        assertThat(PolicyInput.of("t".repeat(200), "en", () -> "text").title()).hasSize(200);
    }

    @Test
    void titleOf201CharactersIsRejected() {
        assertThatThrownBy(() -> PolicyInput.of("t".repeat(201), "en", () -> "text"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.details())
                        .extracting(ErrorDetail::problem).containsExactly("is longer than 200 characters"));
    }

    @Test
    void aControlCharacterInTheTitleIsNamedWithoutItsValue() {
        assertThatThrownBy(() -> PolicyInput.of("title\u0007", "en", () -> "text"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.details())
                        .extracting(ErrorDetail::path, ErrorDetail::problem)
                        .containsExactly(tuple("/title", "contains a control character")));
    }

    @Test
    void theTextIsNotReadWhenTheOtherFieldsAreInvalid() {
        AtomicBoolean read = new AtomicBoolean();

        assertThatThrownBy(() -> PolicyInput.of("title", "fr", () -> {
            read.set(true);
            return "text";
        })).isInstanceOf(ApiException.class);
        assertThat(read).isFalse();
    }

    @Test
    void aControlCharacterInTheTextIsPointedAtTheText() {
        assertThatThrownBy(() -> PolicyInput.of("title", "en", () -> "a\u0000b"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.details())
                        .extracting(ErrorDetail::path).containsExactly("/text"));
    }

    @Test
    void missingTextIsRequired() {
        assertThatThrownBy(() -> PolicyInput.of("title", "en", () -> null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.details())
                        .extracting(ErrorDetail::path, ErrorDetail::problem).containsExactly(tuple("/text", "is required")));
    }

    @Test
    void anUploadRefusalKeepsItsCodeAndPointsAtTheFile() {
        assertThatThrownBy(() -> PolicyInput.of("title", "en", () -> {
            throw new InputRejectedException(ErrorCode.UPLOAD_REJECTED, "contains JavaScript");
        })).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.UPLOAD_REJECTED);
            assertThat(e.details()).extracting(ErrorDetail::path, ErrorDetail::problem)
                    .containsExactly(tuple("/file", "contains JavaScript"));
        });
    }
}
