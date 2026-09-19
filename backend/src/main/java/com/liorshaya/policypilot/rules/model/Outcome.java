package com.liorshaya.policypilot.rules.model;

/** A decision outcome (Document 3, Actions and Rules). */
public enum Outcome {
    APPROVE("approve"),
    REJECT("reject"),
    REFER("refer");

    private final String json;

    Outcome(String json) {
        this.json = json;
    }

    /** The value as the DSL writes it. */
    public String json() {
        return json;
    }
}
