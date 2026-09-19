package com.liorshaya.policypilot.rules.model;

/** Language of labels, reasons and quotes; drives text direction in the UI (Document 3, Document Structure). */
public enum Language {
    HE("he"),
    EN("en");

    private final String json;

    Language(String json) {
        this.json = json;
    }

    /** The value as the DSL writes it. */
    public String json() {
        return json;
    }
}
