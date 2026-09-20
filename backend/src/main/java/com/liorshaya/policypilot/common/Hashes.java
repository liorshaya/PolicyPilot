package com.liorshaya.policypilot.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Content hashes, for keys that must be the same for the same input and different for any other: the response
 * cache key and the recording file name of the AI layer (Document 4). Not for secrets: signing is {@link Hmac}.
 */
public final class Hashes {

    private Hashes() {}

    /** The SHA-256 of the text's UTF-8 bytes, in lower-case hexadecimal. */
    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
