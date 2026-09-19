package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The access code comparison (Document 5, Code exchange: constant-time compare; Security Test Plan, unit). Constant
 * time is a property of the implementation (digests of equal length compared by {@code MessageDigest.isEqual}), so
 * these tests fix the outcomes, including the near misses an early-exit comparison would treat differently.
 */
class AccessCodeVerifierTest {

    private final AccessCodeVerifier verifier = new AccessCodeVerifier("qwertyui");

    @Test
    void matchingCodeIsAccepted() {
        assertThat(verifier.matches("qwertyui")).isTrue();
    }

    @Test
    void wrongCodeIsRejected() {
        assertThat(verifier.matches("asdfghjk")).isFalse();
    }

    @Test
    void prefixOfTheCodeIsRejected() {
        assertThat(verifier.matches("qwertyu")).isFalse();
    }

    @Test
    void longerCodeStartingWithTheCodeIsRejected() {
        assertThat(verifier.matches("qwertyuiop")).isFalse();
    }

    @Test
    void codeDifferingOnlyInCaseIsRejected() {
        assertThat(verifier.matches("QWERTYUI")).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void emptyAndBlankCodesAreRejected(String code) {
        assertThat(verifier.matches(code)).isFalse();
    }
}
