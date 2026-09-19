package com.liorshaya.policypilot.rules.validation;

import com.google.re2j.PatternSyntaxException;
import com.liorshaya.policypilot.rules.model.Action;
import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Expression;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.FieldType;
import com.liorshaya.policypilot.rules.model.Function;
import com.liorshaya.policypilot.rules.model.Literal;
import com.liorshaya.policypilot.rules.model.LiteralList;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operator;
import com.liorshaya.policypilot.rules.model.Provenance;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Layer 2 (Document 3, Static Validation): every reference, type, enum value, expression, pattern and provenance
 * of a rule set that has already passed the schema, so the shapes the schema guarantees are taken as given. Each
 * finding points at the node that is wrong.
 */
final class SemanticValidator {

    /** Document 3, RESERVED_IDENTIFIER. */
    private static final Set<String> RESERVED = Set.of("today", "now", "null", "true", "false");

    /** Document 3, Expressions: the most function nodes on the longest path from the top of an expression. */
    static final int MAX_EXPRESSION_DEPTH = 8;

    List<Finding> validate(RuleSet ruleSet, ValidationContext context, List<String> paragraphs, Set<String> modelRuleIds) {
        return new Run(context, paragraphs, modelRuleIds).check(ruleSet);
    }

    /** The type an expression evaluates to, when it is valid. */
    private enum Kind { NUMBER, DATE }

    private static final class Run {

        private final ValidationContext context;
        private final List<String> paragraphs;
        private final Set<String> modelRuleIds;
        private final Map<String, Field> fields = new HashMap<>();
        private final List<Finding> findings = new ArrayList<>();

        Run(ValidationContext context, List<String> paragraphs, Set<String> modelRuleIds) {
            this.context = context;
            this.paragraphs = paragraphs;
            this.modelRuleIds = modelRuleIds;
        }

        List<Finding> check(RuleSet ruleSet) {
            for (int i = 0; i < ruleSet.fields().size(); i++) {
                field(ruleSet.fields().get(i), "/fields/" + i);
            }
            Set<String> ruleIds = new HashSet<>();
            for (int i = 0; i < ruleSet.rules().size(); i++) {
                Rule rule = ruleSet.rules().get(i);
                String path = "/rules/" + i;
                if (!ruleIds.add(rule.id())) {
                    report(ValidationCode.RULE_ID_DUPLICATE, path + "/id", rule.id() + " is used by an earlier rule",
                            rule.id(), null);
                }
                provenance(rule, path + "/provenance");
                condition(rule.condition(), path + "/condition", rule.id());
                for (int j = 0; j < rule.actions().size(); j++) {
                    if (rule.actions().get(j) instanceof Action.SetField set) {
                        set(set, path + "/actions/" + j, rule.id());
                    }
                }
            }
            return findings;
        }

        // ------------------------------------------------------------------ fields

        private void field(Field field, String path) {
            String name = field.name();
            if (fields.put(name, field) != null) {
                report(ValidationCode.FIELD_DUPLICATE, path + "/name", name + " is declared twice", null, name);
            }
            if (RESERVED.contains(name)) {
                report(ValidationCode.RESERVED_IDENTIFIER, path + "/name", name + " is a reserved word", null, name);
            }
            domain(field, path);
            if (field.source() != null) {
                quoted(field.source(), path + "/source", null, name);
            }
        }

        private void domain(Field field, String path) {
            Map<String, BigDecimal> bounds = new LinkedHashMap<>();
            putIfPresent(bounds, "minimum", field.minimum());
            putIfPresent(bounds, "maximum", field.maximum());
            putIfPresent(bounds, "exclusiveMinimum", field.exclusiveMinimum());
            putIfPresent(bounds, "exclusiveMaximum", field.exclusiveMaximum());
            if (bounds.isEmpty()) {
                return;
            }
            if (!field.type().isNumeric()) {
                String first = bounds.keySet().iterator().next();
                report(ValidationCode.FIELD_DOMAIN_INVALID, path + "/" + first,
                        field.name() + " is " + field.type().json() + " and cannot have a domain", null, field.name());
                return;
            }
            BigDecimal low = field.minimum() != null ? field.minimum() : field.exclusiveMinimum();
            BigDecimal high = field.maximum() != null ? field.maximum() : field.exclusiveMaximum();
            if (low != null && high != null && low.compareTo(high) > 0) {
                report(ValidationCode.FIELD_DOMAIN_INVALID, path,
                        field.name() + ": the lower bound " + low.toPlainString() + " exceeds the upper bound "
                                + high.toPlainString(), null, field.name());
            }
        }

        private static void putIfPresent(Map<String, BigDecimal> bounds, String key, @Nullable BigDecimal value) {
            if (value != null) {
                bounds.put(key, value);
            }
        }

        // ------------------------------------------------------------------ provenance

        private void provenance(Rule rule, String path) {
            switch (rule.provenance()) {
                case Provenance.Quoted quoted -> quoted(quoted, path, rule.id(), null);
                case Provenance.Analyst analyst -> {
                    if (context == ValidationContext.AUTHORING
                            || context == ValidationContext.CHANGE_PROPOSAL && modelRuleIds.contains(rule.id())) {
                        report(ValidationCode.PROVENANCE_ANALYST_FROM_MODEL, path,
                                rule.id() + " was written by the model and cannot claim analyst provenance",
                                rule.id(), null);
                    }
                }
                case Provenance.Pending pending -> {
                    if (context == ValidationContext.PUBLISH) {
                        report(ValidationCode.PROVENANCE_PENDING_AT_PUBLISH, path,
                                rule.id() + " still carries pending provenance", rule.id(), null);
                    } else if (context != ValidationContext.CHANGE_PROPOSAL) {
                        report(ValidationCode.PROVENANCE_PENDING_FROM_MODEL, path,
                                rule.id() + ": pending provenance is accepted only in a change proposal",
                                rule.id(), null);
                    }
                }
            }
        }

        private void quoted(Provenance.Quoted quoted, String path, @Nullable String ruleId, @Nullable String field) {
            String owner = ruleId != null ? ruleId : field;
            if (quoted.paragraph() > paragraphs.size()) {
                report(ValidationCode.PROVENANCE_PARAGRAPH_MISSING, path + "/paragraph",
                        owner + " cites paragraph " + quoted.paragraph() + " of a policy with " + paragraphs.size(),
                        ruleId, field);
            } else if (!QuoteNormalizer.occursIn(quoted.quote(), paragraphs.get(quoted.paragraph() - 1))) {
                report(ValidationCode.PROVENANCE_QUOTE_MISMATCH, path + "/quote",
                        owner + ": the quote does not occur in paragraph " + quoted.paragraph(), ruleId, field);
            }
        }

        // ------------------------------------------------------------------ conditions

        private void condition(Condition condition, String path, String ruleId) {
            switch (condition) {
                case Condition.Comparison comparison -> comparison(comparison, path, ruleId);
                case Condition.All all -> children(all.all(), path + "/all/", ruleId);
                case Condition.Any any -> children(any.any(), path + "/any/", ruleId);
                case Condition.Not not -> condition(not.not(), path + "/not", ruleId);
                case Condition.Always always -> { }
            }
        }

        private void children(List<Condition> children, String prefix, String ruleId) {
            for (int k = 0; k < children.size(); k++) {
                condition(children.get(k), prefix + k, ruleId);
            }
        }

        private void comparison(Condition.Comparison comparison, String path, String ruleId) {
            Field field = fields.get(comparison.field());
            if (field == null) {
                report(ValidationCode.FIELD_UNKNOWN, path + "/field", ruleId + ": " + comparison.field()
                        + " is not declared", ruleId, comparison.field());
                return;
            }
            switch (comparison.op()) {
                case PRESENT, ABSENT -> { }
                case MATCHES -> matches(comparison, field, path, ruleId);
                case IN, NOT_IN -> membership(comparison, field, path, ruleId);
                case BETWEEN -> between(comparison, field, path, ruleId);
                default -> equalityOrOrder(comparison, field, path, ruleId);
            }
        }

        private void matches(Condition.Comparison comparison, Field field, String path, String ruleId) {
            if (field.type() != FieldType.STRING) {
                mismatch(path + "/op", ruleId, field, "matches applies to string fields only");
                return;
            }
            try {
                com.google.re2j.Pattern.compile(((StringLiteral) comparison.value()).value());
            } catch (PatternSyntaxException e) {
                report(ValidationCode.REGEX_INVALID, path + "/value", ruleId + ": the pattern is not supported by the"
                        + " linear-time engine (" + e.getDescription() + ")", ruleId, field.name());
            }
        }

        private void membership(Condition.Comparison comparison, Field field, String path, String ruleId) {
            List<Literal> values = ((LiteralList) comparison.value()).values();
            for (int k = 0; k < values.size(); k++) {
                if (!Literals.fits(field.type(), values.get(k))) {
                    mismatch(path + "/value/" + k, ruleId, field, "a value that does not fit the field type");
                    return;
                }
            }
            if (field.type() == FieldType.ENUM) {
                for (int k = 0; k < values.size(); k++) {
                    enumValue(field, (StringLiteral) values.get(k), path + "/value/" + k, ruleId);
                }
            }
        }

        private void between(Condition.Comparison comparison, Field field, String path, String ruleId) {
            if (!field.type().isNumeric() && field.type() != FieldType.DATE) {
                mismatch(path + "/op", ruleId, field, "between applies to numbers and dates only");
                return;
            }
            List<Literal> bounds = ((LiteralList) comparison.value()).values();
            Literal low = bounds.get(0);
            Literal high = bounds.get(1);
            if (!Literals.fits(field.type(), low) || !Literals.fits(field.type(), high)) {
                mismatch(path + "/value", ruleId, field, "bounds that do not fit the field type");
                return;
            }
            boolean reversed = field.type() == FieldType.DATE
                    ? ((StringLiteral) low).value().compareTo(((StringLiteral) high).value()) > 0
                    : ((NumberLiteral) low).value().compareTo(((NumberLiteral) high).value()) > 0;
            if (reversed) {
                report(ValidationCode.BETWEEN_RANGE_INVALID, path + "/value",
                        ruleId + ": the low bound of " + field.name() + " exceeds the high bound", ruleId, field.name());
            }
        }

        private void equalityOrOrder(Condition.Comparison comparison, Field field, String path, String ruleId) {
            boolean ordered = comparison.op() != Operator.EQ && comparison.op() != Operator.NE;
            if (ordered && !field.type().isNumeric() && field.type() != FieldType.DATE) {
                mismatch(path + "/op", ruleId, field, comparison.op().json() + " orders numbers and dates only");
                return;
            }
            String valuePath = path + "/value";
            switch (comparison.value()) {
                case Call call -> {
                    if (field.type().isNumeric()) {
                        expression(call, valuePath, ruleId, 1);
                    } else {
                        mismatch(valuePath, ruleId, field, "an expression against a non-numeric field");
                    }
                }
                case FieldRef ref -> {
                    Field other = fields.get(ref.field());
                    if (other == null) {
                        report(ValidationCode.FIELD_UNKNOWN, valuePath, ruleId + ": " + ref.field()
                                + " is not declared", ruleId, ref.field());
                    } else if (category(other.type()) != category(field.type())) {
                        mismatch(valuePath, ruleId, field, "a comparison with the " + other.type().json()
                                + " field " + other.name());
                    }
                }
                case LiteralList list -> mismatch(valuePath, ruleId, field, "a list where one value is expected");
                case Literal literal -> {
                    if (!Literals.fits(field.type(), literal)) {
                        mismatch(valuePath, ruleId, field, "a value that does not fit the field type");
                    } else if (field.type() == FieldType.ENUM) {
                        enumValue(field, (StringLiteral) literal, valuePath, ruleId);
                    }
                }
            }
        }

        private void enumValue(Field field, StringLiteral value, String path, String ruleId) {
            if (!field.values().contains(value.value())) {
                report(ValidationCode.ENUM_VALUE_UNKNOWN, path, ruleId + ": " + value.value() + " is not a value of "
                        + field.name(), ruleId, field.name());
            }
        }

        /** Field references compare within a category: numbers with numbers, strings with enums, like with like. */
        private static FieldType category(FieldType type) {
            return switch (type) {
                case INTEGER -> FieldType.NUMBER;
                case ENUM -> FieldType.STRING;
                default -> type;
            };
        }

        // ------------------------------------------------------------------ expressions and set

        /** The kind of the expression; {@code depth} is the number of function nodes from the top to this one. */
        private @Nullable Kind expression(Expression expression, String path, String ruleId, int depth) {
            return switch (expression) {
                case NumberLiteral number -> Kind.NUMBER;
                case FieldRef ref -> reference(ref, path, ruleId);
                case Call call -> call(call, path, ruleId, depth);
            };
        }

        private @Nullable Kind reference(FieldRef ref, String path, String ruleId) {
            Field field = fields.get(ref.field());
            if (field == null) {
                report(ValidationCode.FIELD_UNKNOWN, path, ruleId + ": " + ref.field() + " is not declared",
                        ruleId, ref.field());
                return null;
            }
            if (field.type().isNumeric()) {
                return Kind.NUMBER;
            }
            if (field.type() == FieldType.DATE) {
                return Kind.DATE;
            }
            report(ValidationCode.EXPR_TYPE_MISMATCH, path, ruleId + ": " + ref.field() + " is "
                    + field.type().json() + " and cannot be used in arithmetic", ruleId, ref.field());
            return null;
        }

        private @Nullable Kind call(Call call, String path, String ruleId, int depth) {
            if (depth > MAX_EXPRESSION_DEPTH) {
                report(ValidationCode.EXPR_DEPTH, path, ruleId + ": the expression nests more than "
                        + MAX_EXPRESSION_DEPTH + " functions deep", ruleId, null);
                return null;
            }
            if (!call.fn().accepts(call.args().size())) {
                report(ValidationCode.EXPR_ARITY, path, ruleId + ": " + call.fn().json() + " does not take "
                        + call.args().size() + " arguments", ruleId, null);
                return null;
            }
            List<@Nullable Kind> kinds = new ArrayList<>();
            for (int k = 0; k < call.args().size(); k++) {
                kinds.add(expression(call.args().get(k), path + "/args/" + k, ruleId, depth + 1));
            }
            Kind expected = call.fn() == Function.MONTHS_BETWEEN ? Kind.DATE : Kind.NUMBER;
            for (int k = 0; k < kinds.size(); k++) {
                Kind kind = kinds.get(k);
                if (kind != null && kind != expected) {
                    report(ValidationCode.EXPR_TYPE_MISMATCH, path + "/args/" + k, ruleId + ": "
                            + call.fn().json() + " takes " + (expected == Kind.DATE ? "date fields" : "numbers")
                            + " only", ruleId, null);
                    return null;
                }
            }
            return Kind.NUMBER;
        }

        private void set(Action.SetField set, String path, String ruleId) {
            Field target = fields.get(set.field());
            if (target == null) {
                report(ValidationCode.FIELD_UNKNOWN, path + "/field", ruleId + ": " + set.field()
                        + " is not declared", ruleId, set.field());
                return;
            }
            if (!target.isDerived()) {
                report(ValidationCode.DERIVED_WRITE_ONLY, path + "/field", ruleId + ": " + set.field()
                        + " is a case field; only derived fields can be set", ruleId, set.field());
            }
            String valuePath = path + "/value";
            if (set.value() instanceof Literal literal) {
                if (!Literals.fits(target.type(), literal)) {
                    mismatch(valuePath, ruleId, target, "a value that does not fit the field type");
                }
            } else if (target.type().isNumeric()) {
                expression((Expression) set.value(), valuePath, ruleId, 1);
            } else {
                mismatch(valuePath, ruleId, target, "an expression into a non-numeric field");
            }
        }

        // ------------------------------------------------------------------ reporting

        private void mismatch(String path, String ruleId, Field field, String what) {
            report(ValidationCode.FIELD_TYPE_MISMATCH, path, ruleId + ": " + what + " (" + field.name() + " is "
                    + field.type().json() + ")", ruleId, field.name());
        }

        private void report(ValidationCode code, String path, String message, @Nullable String ruleId,
                @Nullable String fieldName) {
            findings.add(new Finding(code, path, message,
                    ruleId == null ? List.of() : List.of(ruleId),
                    fieldName == null ? List.of() : List.of(fieldName)));
        }
    }
}
