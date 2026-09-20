package com.liorshaya.policypilot.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The content hash behind the response cache key and the recording file names (Document 4). */
class HashesTest {

    @Test
    void isTheSha256OfTheUtf8Bytes() {
        // the published SHA-256 of the empty string and of "abc" (FIPS 180-4, appendix B)
        assertThat(Hashes.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(Hashes.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void readsHebrewAsUtf8() {
        // python3: hashlib.sha256("\u05e9\u05dc\u05d5\u05dd".encode()).hexdigest()
        assertThat(Hashes.sha256Hex("\u05e9\u05dc\u05d5\u05dd"))
                .isEqualTo("b7ac0398ef74193ab738b21df0912329303df10d868c2bac37a84a59d12c0e2f");
    }

    @Test
    void givesADifferentHashToADifferentText() {
        assertThat(Hashes.sha256Hex("author|v1")).isNotEqualTo(Hashes.sha256Hex("author|v2"));
    }
}
