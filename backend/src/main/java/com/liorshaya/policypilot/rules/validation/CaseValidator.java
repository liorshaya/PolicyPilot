package com.liorshaya.policypilot.rules.validation;

import com.liorshaya.policypilot.rules.model.BooleanLiteral;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.Literal;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Case validation, before any evaluation (Document 3, Evaluation Semantics, steps 1 and 2): every required field is
 * present, every supplied value has its field's type and lies inside the field's domain, and no derived field is
 * supplied; a string is at most {@link #MAX_STRING_CHARS} characters; absent optional fields take their default. Every field is checked, in declaration order, so the case
 * error lists every problem at once. Properties the rule set does not declare are ignored, as in the reference.
 */
public final class CaseValidator {

    /** Document 3, Types: a string is at most 2,000 characters (code points); a longer case value is out of range. */
    public static final int MAX_STRING_CHARS = 2000;

    public CaseValidation validate(RuleSet ruleSet, ObjectNode input) {
        Map<String, Literal> values = new HashMap<>();
        List<CaseProblem> problems = new ArrayList<>();
        for (Field field : ruleSet.fields()) {
            JsonNode supplied = input.get(field.name());
            if (field.isDerived()) {
                if (supplied != null) {
                    problems.add(new CaseProblem(CaseProblem.Code.CASE_DERIVED_SUPPLIED, field.name(), null));
                }
            } else if (supplied == null) {
                if (field.isRequired()) {
                    problems.add(new CaseProblem(CaseProblem.Code.CASE_REQUIRED_MISSING, field.name(), null));
                } else if (field.defaultValue() != null) {
                    values.put(field.name(), field.defaultValue());
                }
            } else {
                Literal value = typed(field, supplied);
                CaseProblem.Code problem = value == null ? CaseProblem.Code.CASE_TYPE_MISMATCH : outOfDomain(field, value);
                if (problem == null) {
                    values.put(field.name(), value);
                } else {
                    problems.add(new CaseProblem(problem, field.name(), supplied));
                }
            }
        }
        return new CaseValidation(values, problems);
    }

    /** The supplied value as a literal of the field's type, or {@code null} when it does not have that type. */
    private static @Nullable Literal typed(Field field, JsonNode supplied) {
        return switch (field.type()) {
            case NUMBER -> supplied.isNumber() ? new NumberLiteral(supplied.decimalValue()) : null;
            case INTEGER -> supplied.isNumber() && Literals.isWhole(supplied.decimalValue())
                    ? new NumberLiteral(supplied.decimalValue())
                    : null;
            case BOOLEAN -> supplied.isBoolean() ? new BooleanLiteral(supplied.booleanValue()) : null;
            case STRING -> supplied.isString() ? new StringLiteral(supplied.stringValue()) : null;
            case ENUM -> supplied.isString() && field.values().contains(supplied.stringValue())
                    ? new StringLiteral(supplied.stringValue())
                    : null;
            case DATE -> supplied.isString() && Literals.isDate(supplied.stringValue())
                    ? new StringLiteral(supplied.stringValue())
                    : null;
        };
    }

    /**
     * CASE_OUT_OF_RANGE when a number lies outside the field's declared domain or a string is longer than
     * {@link #MAX_STRING_CHARS}, otherwise {@code null}.
     */
    private static CaseProblem.@Nullable Code outOfDomain(Field field, Literal value) {
        if (value instanceof StringLiteral text) {
            String s = text.value();
            return s.codePointCount(0, s.length()) > MAX_STRING_CHARS ? CaseProblem.Code.CASE_OUT_OF_RANGE : null;
        }
        if (!(value instanceof NumberLiteral number)) {
            return null;
        }
        BigDecimal v = number.value();
        boolean outside = field.minimum() != null && v.compareTo(field.minimum()) < 0
                || field.maximum() != null && v.compareTo(field.maximum()) > 0
                || field.exclusiveMinimum() != null && v.compareTo(field.exclusiveMinimum()) <= 0
                || field.exclusiveMaximum() != null && v.compareTo(field.exclusiveMaximum()) >= 0;
        return outside ? CaseProblem.Code.CASE_OUT_OF_RANGE : null;
    }
}
