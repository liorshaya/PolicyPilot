package com.liorshaya.policypilot.rules.patch;

import java.util.Locale;

/** The five patch ops of Document 3, Change Patches; the Patches object writes each in lower case. */
enum PatchOp {
    ADD,
    REPLACE,
    REMOVE,
    ADD_FIELD,
    SET_DEFAULTS;

    /** The op a patch names; the schema has already refused any other. */
    static PatchOp of(String json) {
        return valueOf(json.toUpperCase(Locale.ROOT));
    }
}
