package com.liorshaya.policypilot.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 from the JDK (Document 5, OWASP A04: no home-made crypto). */
public final class Hmac {

    private static final String ALGORITHM = "HmacSHA256";

    private Hmac() {}

    public static byte[] sha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is part of every JDK", e);
        }
    }

    public static byte[] sha256(byte[] key, String data) {
        return sha256(key, data.getBytes(StandardCharsets.UTF_8));
    }
}
