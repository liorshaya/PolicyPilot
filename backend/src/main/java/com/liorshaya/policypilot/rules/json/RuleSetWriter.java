package com.liorshaya.policypilot.rules.json;

import com.liorshaya.policypilot.rules.model.Action;
import com.liorshaya.policypilot.rules.model.BooleanLiteral;
import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Expression;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.LiteralList;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operand;
import com.liorshaya.policypilot.rules.model.Provenance;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes the records as a tree in the key order of Document 3. An attribute the records hold as {@code null} is
 * omitted, so reading and writing a document gives back the same tree.
 */
final class RuleSetWriter {

    private static final int INT_BITS = 31;
    private static final int LONG_BITS = 63;

    private final JsonNodeFactory nodes;

    RuleSetWriter(JsonNodeFactory nodes) {
        this.nodes = nodes;
    }

    ObjectNode ruleSet(RuleSet ruleSet) {
        ObjectNode out = nodes.objectNode();
        out.put("dslVersion", ruleSet.dslVersion());
        out.put("id", ruleSet.id());
        out.put("name", ruleSet.name());
        out.put("language", ruleSet.language().json());
        putIfPresent(out, "description", ruleSet.description());
        ArrayNode fields = out.putArray("fields");
        ruleSet.fields().forEach(field -> fields.add(field(field)));
        ObjectNode defaults = out.putObject("defaults");
        defaults.put("outcome", ruleSet.defaults().outcome().json());
        defaults.put("reason", ruleSet.defaults().reason());
        ArrayNode rules = out.putArray("rules");
        ruleSet.rules().forEach(rule -> rules.add(rule(rule)));
        return out;
    }

    private ObjectNode field(Field field) {
        ObjectNode out = nodes.objectNode();
        out.put("name", field.name());
        out.put("type", field.type().json());
        putIfPresent(out, "unit", field.unit());
        putIfPresent(out, "values", strings(field.values()));
        putIfPresent(out, "required", field.required());
        putIfPresent(out, "derived", field.derived());
        putIfPresent(out, "default", field.defaultValue() == null ? null : operand(field.defaultValue()));
        putIfPresent(out, "minimum", number(field.minimum()));
        putIfPresent(out, "maximum", number(field.maximum()));
        putIfPresent(out, "exclusiveMinimum", number(field.exclusiveMinimum()));
        putIfPresent(out, "exclusiveMaximum", number(field.exclusiveMaximum()));
        putIfPresent(out, "description", field.description());
        putIfPresent(out, "source", field.source() == null ? null : provenance(field.source()));
        return out;
    }

    private ObjectNode rule(Rule rule) {
        ObjectNode out = nodes.objectNode();
        out.put("id", rule.id());
        out.put("label", rule.label());
        out.put("priority", rule.priority());
        putIfPresent(out, "enabled", rule.enabled());
        out.set("condition", condition(rule.condition()));
        ArrayNode actions = out.putArray("actions");
        rule.actions().forEach(action -> actions.add(action(action)));
        out.set("provenance", provenance(rule.provenance()));
        putIfPresent(out, "tags", strings(rule.tags()));
        return out;
    }

    private ObjectNode condition(Condition condition) {
        ObjectNode out = nodes.objectNode();
        switch (condition) {
            case Condition.Comparison c -> {
                out.put("field", c.field());
                out.put("op", c.op().json());
                putIfPresent(out, "value", c.value() == null ? null : operand(c.value()));
            }
            case Condition.All c -> {
                ArrayNode all = out.putArray("all");
                c.all().forEach(child -> all.add(condition(child)));
            }
            case Condition.Any c -> {
                ArrayNode any = out.putArray("any");
                c.any().forEach(child -> any.add(condition(child)));
            }
            case Condition.Not c -> out.set("not", condition(c.not()));
            case Condition.Always c -> out.put("always", true);
        }
        return out;
    }

    private JsonNode operand(Operand operand) {
        return switch (operand) {
            case NumberLiteral n -> number(n.value());
            case StringLiteral s -> nodes.stringNode(s.value());
            case BooleanLiteral b -> nodes.booleanNode(b.value());
            case FieldRef f -> nodes.objectNode().put("field", f.field());
            case Call c -> call(c);
            case LiteralList l -> {
                ArrayNode values = nodes.arrayNode();
                l.values().forEach(value -> values.add(operand(value)));
                yield values;
            }
        };
    }

    private ObjectNode call(Call call) {
        ObjectNode out = nodes.objectNode();
        out.put("fn", call.fn().json());
        ArrayNode args = out.putArray("args");
        call.args().forEach(arg -> args.add(expression(arg)));
        return out;
    }

    private JsonNode expression(Expression expression) {
        return switch (expression) {
            case NumberLiteral n -> number(n.value());
            case FieldRef f -> nodes.objectNode().put("field", f.field());
            case Call c -> call(c);
        };
    }

    private ObjectNode action(Action action) {
        ObjectNode out = nodes.objectNode();
        switch (action) {
            case Action.Decide d -> {
                out.put("type", "decide");
                out.put("outcome", d.outcome().json());
                putIfPresent(out, "terminal", d.terminal());
                out.put("reason", d.reason());
            }
            case Action.SetField s -> {
                out.put("type", "set");
                out.put("field", s.field());
                out.set("value", operand(s.value()));
            }
            case Action.Flag f -> {
                out.put("type", "flag");
                out.put("code", f.code());
                out.put("message", f.message());
            }
        }
        return out;
    }

    private ObjectNode provenance(Provenance provenance) {
        ObjectNode out = nodes.objectNode();
        switch (provenance) {
            case Provenance.Quoted q -> {
                out.put("kind", "quoted");
                out.put("paragraph", q.paragraph());
                out.put("quote", q.quote());
                putIfPresent(out, "confidence", number(q.confidence()));
            }
            case Provenance.Analyst a -> {
                out.put("kind", "analyst");
                out.put("note", a.note());
                out.put("actor", a.actor());
                putIfPresent(out, "changeRequestId", a.changeRequestId());
            }
            case Provenance.Pending p -> {
                out.put("kind", "pending");
                out.put("changeRequestId", p.changeRequestId());
                out.put("rationale", p.rationale());
            }
        }
        return out;
    }

    /**
     * A decimal with a fraction stays a decimal node; a whole number becomes the integral node the parser would
     * have produced for it, so a written tree equals a parsed one.
     */
    private @Nullable JsonNode number(@Nullable BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.scale() != 0) {
            return nodes.numberNode(value);
        }
        BigInteger integral = value.unscaledValue();
        if (integral.bitLength() <= INT_BITS) {
            return nodes.numberNode(integral.intValue());
        }
        if (integral.bitLength() <= LONG_BITS) {
            return nodes.numberNode(integral.longValue());
        }
        return nodes.numberNode(integral);
    }

    private @Nullable ArrayNode strings(@Nullable List<String> values) {
        if (values == null) {
            return null;
        }
        ArrayNode out = nodes.arrayNode();
        values.forEach(out::add);
        return out;
    }

    private static void putIfPresent(ObjectNode out, String key, @Nullable String value) {
        if (value != null) {
            out.put(key, value);
        }
    }

    private static void putIfPresent(ObjectNode out, String key, @Nullable Boolean value) {
        if (value != null) {
            out.put(key, value);
        }
    }

    private static void putIfPresent(ObjectNode out, String key, @Nullable JsonNode value) {
        if (value != null) {
            out.set(key, value);
        }
    }
}
