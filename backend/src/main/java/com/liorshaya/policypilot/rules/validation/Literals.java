package com.liorshaya.policypilot.rules.validation;

import com.liorshaya.policypilot.rules.model.BooleanLiteral;
import com.liorshaya.policypilot.rules.model.FieldType;
import com.liorshaya.policypilot.rules.model.Literal;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Whether a literal fits a field type (Document 3, Types, Values and Arithmetic). */
final class Literals {

    private static final Pattern DATE_SHAPE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private Literals() {}

    /** Booleans fit boolean; numbers fit number, and integer when whole; strings fit string, enum and a valid date. */
    static boolean fits(FieldType type, Literal literal) {
        return switch (literal) {
            case BooleanLiteral b -> type == FieldType.BOOLEAN;
            case NumberLiteral n -> type == FieldType.NUMBER || type == FieldType.INTEGER && isWhole(n.value());
            case StringLiteral s -> type == FieldType.STRING || type == FieldType.ENUM
                    || type == FieldType.DATE && isDate(s.value());
        };
    }

    /** A number without a fraction; {@code 34.0} is whole. */
    static boolean isWhole(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0;
    }

    /** An ISO-8601 calendar date, {@code 2026-09-16}, that exists. */
    static boolean isDate(String value) {
        if (!DATE_SHAPE.matcher(value).matches()) {
            return false;
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
