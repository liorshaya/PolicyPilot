package com.liorshaya.policypilot.rules.json;

import com.liorshaya.policypilot.rules.model.Action;
import com.liorshaya.policypilot.rules.model.BooleanLiteral;
import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Defaults;
import com.liorshaya.policypilot.rules.model.Expression;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.FieldType;
import com.liorshaya.policypilot.rules.model.Function;
import com.liorshaya.policypilot.rules.model.Language;
import com.liorshaya.policypilot.rules.model.Literal;
import com.liorshaya.policypilot.rules.model.LiteralList;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operand;
import com.liorshaya.policypilot.rules.model.Operator;
import com.liorshaya.policypilot.rules.model.Outcome;
import com.liorshaya.policypilot.rules.model.Provenance;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import com.liorshaya.policypilot.rules.model.Value;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Maps a rule set tree to the records, strictly: every object may carry only the properties Document 3 defines,
 * and every value must have the type the DSL gives it. The first problem is thrown with its JSON pointer.
 */
final class RuleSetReader {

    private static final Set<String> RULE_SET_KEYS =
            Set.of("dslVersion", "id", "name", "language", "description", "fields", "defaults", "rules");
    private static final Set<String> FIELD_KEYS = Set.of("name", "type", "unit", "values", "required", "derived",
            "default", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "description", "source");
    private static final Set<String> DEFAULTS_KEYS = Set.of("outcome", "reason");
    private static final Set<String> RULE_KEYS =
            Set.of("id", "label", "priority", "enabled", "condition", "actions", "provenance", "tags");
    private static final Set<String> COMPARISON_KEYS = Set.of("field", "op", "value");
    private static final Set<String> FIELD_REF_KEYS = Set.of("field");
    private static final Set<String> CALL_KEYS = Set.of("fn", "args");
    private static final Set<String> DECIDE_KEYS = Set.of("type", "outcome", "terminal", "reason");
    private static final Set<String> SET_KEYS = Set.of("type", "field", "value");
    private static final Set<String> FLAG_KEYS = Set.of("type", "code", "message");
    private static final Set<String> QUOTED_KEYS = Set.of("kind", "paragraph", "quote", "confidence");
    private static final Set<String> ANALYST_KEYS = Set.of("kind", "note", "actor", "changeRequestId");
    private static final Set<String> PENDING_KEYS = Set.of("kind", "changeRequestId", "rationale");

    private RuleSetReader() {}

    static RuleSet ruleSet(JsonNode node) {
        object(node, "", RULE_SET_KEYS);
        return new RuleSet(
                text(node, "dslVersion", ""),
                text(node, "id", ""),
                text(node, "name", ""),
                constant(Language.values(), Language::json, node, "language", ""),
                optionalText(node, "description", ""),
                list(node, "fields", "", RuleSetReader::field),
                defaults(required(node, "defaults", ""), "/defaults"),
                list(node, "rules", "", RuleSetReader::rule));
    }

    private static Field field(JsonNode node, String path) {
        object(node, path, FIELD_KEYS);
        JsonNode values = node.get("values");
        JsonNode defaultValue = node.get("default");
        JsonNode source = node.get("source");
        return new Field(
                text(node, "name", path),
                constant(FieldType.values(), FieldType::json, node, "type", path),
                optionalText(node, "unit", path),
                values == null ? null : list(node, "values", path, RuleSetReader::string),
                optionalBoolean(node, "required", path),
                optionalBoolean(node, "derived", path),
                defaultValue == null ? null : literal(defaultValue, path + "/default"),
                optionalNumber(node, "minimum", path),
                optionalNumber(node, "maximum", path),
                optionalNumber(node, "exclusiveMinimum", path),
                optionalNumber(node, "exclusiveMaximum", path),
                optionalText(node, "description", path),
                source == null ? null : quoted(source, path + "/source"));
    }

    private static Defaults defaults(JsonNode node, String path) {
        object(node, path, DEFAULTS_KEYS);
        return new Defaults(
                constant(Outcome.values(), Outcome::json, node, "outcome", path), text(node, "reason", path));
    }

    private static Rule rule(JsonNode node, String path) {
        object(node, path, RULE_KEYS);
        JsonNode tags = node.get("tags");
        return new Rule(
                text(node, "id", path),
                text(node, "label", path),
                integer(node, "priority", path),
                optionalBoolean(node, "enabled", path),
                condition(required(node, "condition", path), path + "/condition"),
                list(node, "actions", path, RuleSetReader::action),
                provenance(required(node, "provenance", path), path + "/provenance"),
                tags == null ? null : list(node, "tags", path, RuleSetReader::string));
    }

    private static Condition condition(JsonNode node, String path) {
        if (node.has("field")) {
            object(node, path, COMPARISON_KEYS);
            JsonNode value = node.get("value");
            return new Condition.Comparison(
                    text(node, "field", path),
                    constant(Operator.values(), Operator::json, node, "op", path),
                    value == null ? null : operand(value, path + "/value"));
        }
        if (node.has("all")) {
            object(node, path, Set.of("all"));
            return new Condition.All(list(node, "all", path, RuleSetReader::condition));
        }
        if (node.has("any")) {
            object(node, path, Set.of("any"));
            return new Condition.Any(list(node, "any", path, RuleSetReader::condition));
        }
        if (node.has("not")) {
            object(node, path, Set.of("not"));
            return new Condition.Not(condition(node.get("not"), path + "/not"));
        }
        if (node.has("always")) {
            object(node, path, Set.of("always"));
            if (!node.get("always").isBoolean() || !node.get("always").booleanValue()) {
                throw fail(path + "/always", "expected true");
            }
            return new Condition.Always();
        }
        throw fail(path, "expected a comparison, all, any, not or always");
    }

    private static Operand operand(JsonNode node, String path) {
        if (node.isArray()) {
            return new LiteralList(elements(node, path, RuleSetReader::literal));
        }
        return value(node, path);
    }

    private static Value value(JsonNode node, String path) {
        if (!node.isObject()) {
            return literal(node, path);
        }
        return node.has("fn") ? call(node, path) : fieldRef(node, path);
    }

    private static Expression expression(JsonNode node, String path) {
        if (node.isNumber()) {
            return new NumberLiteral(node.decimalValue());
        }
        if (!node.isObject()) {
            throw fail(path, "expected a number, a field reference or a function");
        }
        return node.has("fn") ? call(node, path) : fieldRef(node, path);
    }

    private static Call call(JsonNode node, String path) {
        object(node, path, CALL_KEYS);
        return new Call(
                constant(Function.values(), Function::json, node, "fn", path),
                list(node, "args", path, RuleSetReader::expression));
    }

    private static FieldRef fieldRef(JsonNode node, String path) {
        object(node, path, FIELD_REF_KEYS);
        return new FieldRef(text(node, "field", path));
    }

    private static Literal literal(JsonNode node, String path) {
        if (node.isNumber()) {
            return new NumberLiteral(node.decimalValue());
        }
        if (node.isString()) {
            return new StringLiteral(node.stringValue());
        }
        if (node.isBoolean()) {
            return new BooleanLiteral(node.booleanValue());
        }
        throw fail(path, "expected a number, a string or a boolean");
    }

    private static Action action(JsonNode node, String path) {
        object(node, path);
        return switch (text(node, "type", path)) {
            case "decide" -> {
                object(node, path, DECIDE_KEYS);
                yield new Action.Decide(
                        constant(Outcome.values(), Outcome::json, node, "outcome", path),
                        optionalBoolean(node, "terminal", path),
                        text(node, "reason", path));
            }
            case "set" -> {
                object(node, path, SET_KEYS);
                yield new Action.SetField(
                        text(node, "field", path), value(required(node, "value", path), path + "/value"));
            }
            case "flag" -> {
                object(node, path, FLAG_KEYS);
                yield new Action.Flag(text(node, "code", path), text(node, "message", path));
            }
            default -> throw fail(path + "/type", "expected decide, set or flag");
        };
    }

    private static Provenance provenance(JsonNode node, String path) {
        object(node, path);
        return switch (text(node, "kind", path)) {
            case "quoted" -> quoted(node, path);
            case "analyst" -> {
                object(node, path, ANALYST_KEYS);
                yield new Provenance.Analyst(
                        text(node, "note", path), text(node, "actor", path),
                        optionalText(node, "changeRequestId", path));
            }
            case "pending" -> {
                object(node, path, PENDING_KEYS);
                yield new Provenance.Pending(text(node, "changeRequestId", path), text(node, "rationale", path));
            }
            default -> throw fail(path + "/kind", "expected quoted, analyst or pending");
        };
    }

    private static Provenance.Quoted quoted(JsonNode node, String path) {
        object(node, path, QUOTED_KEYS);
        if (!"quoted".equals(text(node, "kind", path))) {
            throw fail(path + "/kind", "expected quoted");
        }
        return new Provenance.Quoted(
                integer(node, "paragraph", path), text(node, "quote", path), optionalNumber(node, "confidence", path));
    }

    // ------------------------------------------------------------------ typed access

    private interface ElementReader<T> {
        T read(JsonNode node, String path);
    }

    private static void object(JsonNode node, String path) {
        if (!node.isObject()) {
            throw fail(path, "expected an object");
        }
    }

    private static void object(JsonNode node, String path, Set<String> allowed) {
        object(node, path);
        for (String name : node.propertyNames()) {
            if (!allowed.contains(name)) {
                throw fail(path + "/" + escape(name), "unknown property");
            }
        }
    }

    private static JsonNode required(JsonNode object, String key, String path) {
        JsonNode value = object.get(key);
        if (value == null) {
            throw fail(path, "missing property " + key);
        }
        return value;
    }

    private static String text(JsonNode object, String key, String path) {
        return string(required(object, key, path), path + "/" + key);
    }

    private static @Nullable String optionalText(JsonNode object, String key, String path) {
        return object.has(key) ? text(object, key, path) : null;
    }

    private static String string(JsonNode node, String path) {
        if (!node.isString()) {
            throw fail(path, "expected a string");
        }
        return node.stringValue();
    }

    private static int integer(JsonNode object, String key, String path) {
        JsonNode node = required(object, key, path);
        if (!node.isNumber()) {
            throw fail(path + "/" + key, "expected an integer");
        }
        try {
            return node.decimalValue().intValueExact();
        } catch (ArithmeticException e) {
            throw fail(path + "/" + key, "expected an integer");
        }
    }

    private static @Nullable BigDecimal optionalNumber(JsonNode object, String key, String path) {
        JsonNode node = object.get(key);
        if (node == null) {
            return null;
        }
        if (!node.isNumber()) {
            throw fail(path + "/" + key, "expected a number");
        }
        return node.decimalValue();
    }

    private static @Nullable Boolean optionalBoolean(JsonNode object, String key, String path) {
        JsonNode node = object.get(key);
        if (node == null) {
            return null;
        }
        if (!node.isBoolean()) {
            throw fail(path + "/" + key, "expected a boolean");
        }
        return node.booleanValue();
    }

    private static <E> E constant(E[] constants, java.util.function.Function<E, String> json, JsonNode object,
            String key, String path) {
        String text = text(object, key, path);
        for (E constant : constants) {
            if (json.apply(constant).equals(text)) {
                return constant;
            }
        }
        throw fail(path + "/" + key, "unknown value");
    }

    private static <T> List<T> list(JsonNode object, String key, String path, ElementReader<T> reader) {
        return elements(required(object, key, path), path + "/" + key, reader);
    }

    private static <T> List<T> elements(JsonNode array, String path, ElementReader<T> reader) {
        if (!array.isArray()) {
            throw fail(path, "expected an array");
        }
        List<T> out = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            out.add(reader.read(array.get(i), path + "/" + i));
        }
        return out;
    }

    /** RFC 6901 escaping of one reference token. */
    private static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    private static RuleSetFormatException fail(String pointer, String message) {
        return new RuleSetFormatException(pointer, message);
    }
}
