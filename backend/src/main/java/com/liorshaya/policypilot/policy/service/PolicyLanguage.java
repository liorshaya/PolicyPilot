package com.liorshaya.policypilot.policy.service;

import java.util.Arrays;
import java.util.Optional;

/** The two policy languages (Brief FR-1; Document 4, Language handling), written as their ISO 639-1 codes. */
public enum PolicyLanguage {
    HE("he"),
    EN("en");

    private final String code;

    PolicyLanguage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<PolicyLanguage> fromCode(String code) {
        return Arrays.stream(values()).filter(language -> language.code.equals(code)).findFirst();
    }
}
