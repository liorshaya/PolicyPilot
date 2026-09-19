package com.liorshaya.policypilot.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Compares a typed code with {@code POLICYPILOT_ACCESS_CODE} in constant time (Document 5, Code strength): both are
 * hashed to 32 bytes first, so neither the length nor the first differing character changes the time taken.
 */
public class AccessCodeVerifier {

    private final byte[] expected;

    public AccessCodeVerifier(String accessCode) {
        this.expected = sha256(accessCode);
    }

    public boolean matches(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(expected, sha256(candidate));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every JDK", e);
        }
    }
}
