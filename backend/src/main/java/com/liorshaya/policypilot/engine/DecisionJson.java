package com.liorshaya.policypilot.engine;

import com.liorshaya.policypilot.engine.TraceStep.Applied;
import com.liorshaya.policypilot.engine.TraceStep.Compared;
import com.liorshaya.policypilot.engine.TraceStep.ExpressionValue;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.validation.CaseProblem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The JSON of a decision (Document 3, Decision object and TraceStep object), in the shape the reference writes: the
 * same keys, numbers rounded to 12 places without trailing zeros. Keys come in a fixed order and decimals are written
 * plainly, so {@link #canonical} gives the same bytes for the same decision every time (Document 3, step 8).
 */
public final class DecisionJson {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).build();
    private static final RuleSetMapper RULES = new RuleSetMapper();

    private DecisionJson() {}

    /** The evaluation as a JSON tree. */
    public static ObjectNode toJson(Evaluation evaluation) {
        return switch (evaluation) {
            case Decision decision -> decision(decision);
            case CaseError error -> caseError(error);
        };
    }

    /** The evaluation as compact JSON text, byte for byte the same for the same evaluation. */
    public static String canonical(Evaluation evaluation) {
        return JSON.writeValueAsString(toJson(evaluation));
    }

    private static ObjectNode decision(Decision decision) {
        ObjectNode out = NODES.objectNode();
        out.put("status", decision.status().name());
        if (decision.status() == Decision.Status.OK) {
            out.put("outcome", decision.outcome().json());
            out.put("reason", decision.reason());
            out.put("decidingRuleId", decision.decidingRuleId());
            out.put("terminal", decision.terminal());
        } else {
            out.put("errorCode", decision.errorCode().name());
            out.put("errorRuleId", decision.errorRuleId());
        }
        ObjectNode derived = out.putObject("derived");
        decision.derived().forEach((name, value) -> derived.set(name, value(value)));
        if (decision.status() == Decision.Status.OK) {
            ArrayNode flags = out.putArray("flags");
            decision.flags().forEach(flag -> flags.addObject()
                    .put("code", flag.code()).put("message", flag.message()).put("ruleId", flag.ruleId()));
            ArrayNode candidates = out.putArray("candidates");
            decision.candidates().forEach(candidate -> candidates.addObject()
                    .put("outcome", candidate.outcome().json()).put("ruleId", candidate.ruleId()));
        }
        ArrayNode trace = out.putArray("trace");
        decision.trace().forEach(step -> trace.add(step(step)));
        if (decision.simulation()) {
            out.put("simulation", true);
            out.set("overrides", decision.overrides().deepCopy());
        }
        return out;
    }

    private static ObjectNode caseError(CaseError error) {
        ObjectNode out = NODES.objectNode();
        out.put("status", "CASE_INVALID");
        ArrayNode problems = out.putArray("problems");
        for (CaseProblem problem : error.problems()) {
            ObjectNode node = problems.addObject().put("code", problem.code().name()).put("field", problem.field());
            if (problem.value() != null) {
                node.set("value", problem.value().deepCopy());
            }
        }
        return out;
    }

    private static ObjectNode step(TraceStep step) {
        ObjectNode out = NODES.objectNode()
                .put("ruleId", step.ruleId()).put("label", step.label()).put("priority", step.priority())
                .put("status", step.status().json());
        if (step.comparisons() != null) {
            ArrayNode comparisons = out.putArray("comparisons");
            step.comparisons().forEach(compared -> comparisons.add(compared(compared)));
        }
        if (step.actions() != null) {
            ArrayNode actions = out.putArray("actions");
            step.actions().forEach(applied -> actions.add(applied(applied)));
        }
        if (step.error() != null) {
            out.putObject("error").put("code", step.error().code().name()).put("detail", step.error().detail());
        }
        if (step.provenance() != null) {
            out.set("provenance", RULES.toJson(step.provenance()));
        }
        return out;
    }

    private static ObjectNode compared(Compared compared) {
        ObjectNode out = NODES.objectNode().put("field", compared.field()).put("op", compared.op());
        if (compared.expected() != null) {
            out.set("expected", value(compared.expected()));
        }
        out.set("actual", value(compared.actual()));
        return out.put("result", compared.result());
    }

    private static ObjectNode applied(Applied applied) {
        return switch (applied) {
            case Applied.Set set -> {
                ObjectNode out = NODES.objectNode().put("type", "set").put("field", set.field());
                out.set("from", value(set.from()));
                out.set("to", value(set.to()));
                yield out;
            }
            case Applied.Flagged flagged -> NODES.objectNode().put("type", "flag").put("code", flagged.code());
            case Applied.Decided decided -> NODES.objectNode().put("type", "decide")
                    .put("outcome", decided.outcome().json()).put("terminal", decided.terminal());
        };
    }

    /**
     * A value of the engine as JSON: numbers at most 12 places without trailing zeros, text, lists, an expression's
     * value and text, and otherwise a boolean, the only other kind of value a case or a literal holds.
     */
    private static JsonNode value(@Nullable Object value) {
        return switch (value) {
            case null -> NODES.nullNode();
            case BigDecimal number -> NODES.numberNode(normalize(number));
            case String text -> NODES.stringNode(text);
            case ExpressionValue expression -> NODES.objectNode()
                    .<ObjectNode>set("value", value(expression.value())).put("text", expression.text());
            case List<?> list -> {
                ArrayNode out = NODES.arrayNode();
                list.forEach(item -> out.add(value(item)));
                yield out;
            }
            default -> NODES.booleanNode((Boolean) value);
        };
    }

    private static BigDecimal normalize(BigDecimal number) {
        BigDecimal rounded = number.setScale(ExpressionEvaluator.SCALE, RoundingMode.HALF_EVEN).stripTrailingZeros();
        return rounded.setScale(Math.max(rounded.scale(), 0));   // no exponent form: 6E+4 becomes 60000
    }
}
