package com.liorshaya.policypilot.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** HMAC-SHA256 against the known answers of RFC 4231 (Document 5, Cookie: HMAC-SHA256). */
class HmacTest {

    // Expected: RFC 4231, test case 2
    @Test
    void matchesTheRfc4231KnownAnswerForAStringInput() {
        byte[] mac = Hmac.sha256("Jefe".getBytes(StandardCharsets.UTF_8), "what do ya want for nothing?");

        assertThat(HexFormat.of().formatHex(mac))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    // Expected: RFC 4231, test case 1 (key 0x0b repeated 20 times, data "Hi There")
    @Test
    void matchesTheRfc4231KnownAnswerForABinaryKey() {
        byte[] key = new byte[20];
        java.util.Arrays.fill(key, (byte) 0x0b);

        byte[] mac = Hmac.sha256(key, "Hi There".getBytes(StandardCharsets.US_ASCII));

        assertThat(HexFormat.of().formatHex(mac))
                .isEqualTo("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7");
    }
}
