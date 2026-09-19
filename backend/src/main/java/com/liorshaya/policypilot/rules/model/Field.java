package com.liorshaya.policypilot.rules.model;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A declared case or derived field (Document 3, Field Schema). */
public record Field(
        String name,
        FieldType type,
        @Nullable String unit,
        @Nullable List<String> values,
        @Nullable Boolean required,
        @Nullable Boolean derived,
        @Nullable Literal defaultValue,
        @Nullable BigDecimal minimum,
        @Nullable BigDecimal maximum,
        @Nullable BigDecimal exclusiveMinimum,
        @Nullable BigDecimal exclusiveMaximum,
        @Nullable String description,
        Provenance.@Nullable Quoted source) {

    public Field {
        values = values == null ? null : List.copyOf(values);
    }

    /** {@code required}, which defaults to {@code false}. */
    public boolean isRequired() {
        return Boolean.TRUE.equals(required);
    }

    /** {@code derived}, which defaults to {@code false}. */
    public boolean isDerived() {
        return Boolean.TRUE.equals(derived);
    }
}
