package com.liorshaya.policypilot.ai.chat;

import com.liorshaya.policypilot.ai.ChatTool;
import com.liorshaya.policypilot.ai.service.chat.CappedTool;
import com.liorshaya.policypilot.ai.service.chat.ChatCitation;
import com.liorshaya.policypilot.ai.service.chat.ChatTurn;
import com.liorshaya.policypilot.ai.service.chat.RuleListing;
import com.liorshaya.policypilot.ai.service.chat.ToolArguments;
import com.liorshaya.policypilot.ai.service.chat.ToolRefusedException;
import com.liorshaya.policypilot.ai.service.chat.ToolResults;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.decision.service.Aggregates;
import com.liorshaya.policypilot.decision.service.CaseInvalidException;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.decision.service.DecisionView;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.ruleset.service.PublishedVersion;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The four tools of the answer prompt (Document 2, Tools available to the answer prompt; Document 4, Prompt 4):
 * {@code getDecision(applicationNumber)} reads the sandbox's latest stored decision of that application on the
 * session's version, {@code simulate(applicationNumber, overrides)} asks the engine what that decision's input would
 * decide with some fields changed, storing nothing, {@code getDecisionStats()} counts the outcomes, the deciding
 * rules and the flags of the sandbox's decisions on the version, and {@code listRules(tag?)} lists the version's
 * rules in evaluation order. All four are scoped to the turn's sandbox and version and held to its caps
 * ({@link CappedTool}). A decision and a simulation are answered as a {@code <tool_result>} the model can cite by
 * its id; statistics and a listing name rules instead, and supply those, because Document 4 has no marker for them.
 */
@Component
public class DecisionTools {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static final String GET_DECISION = "getDecision";
    static final String SIMULATE = "simulate";
    static final String GET_DECISION_STATS = "getDecisionStats";
    static final String LIST_RULES = "listRules";

    private static final String NUMBER_SCHEMA = """
            {"type":"object","properties":{"applicationNumber":{"type":"integer","minimum":1,\
            "description":"The application number, as the question writes it"}},\
            "required":["applicationNumber"],"additionalProperties":false}""";
    private static final String NO_ARGUMENTS_SCHEMA =
            "{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}";
    private static final String TAG_SCHEMA = """
            {"type":"object","properties":{"tag":{"type":"string",\
            "description":"One of the rule set's tags, to list only the rules carrying it"}},\
            "required":[],"additionalProperties":false}""";
    private static final String SIMULATE_SCHEMA = """
            {"type":"object","properties":{"applicationNumber":{"type":"integer","minimum":1,\
            "description":"The application number, as the question writes it"},\
            "overrides":{"type":"object","description":"The case fields to change, by name, with their new values"}},\
            "required":["applicationNumber","overrides"],"additionalProperties":false}""";

    private final DecisionService decisions;
    private final SecurityEvents events;

    public DecisionTools(DecisionService decisions, SecurityEvents events) {
        this.decisions = decisions;
        this.events = events;
    }

    /** The tools of one turn, bound to its sandbox, its version and its caps. */
    public List<ChatTool> forTurn(ChatTurn turn, PublishedVersion version, RuleSet ruleSet, UUID sandboxId) {
        return List.of(
                new CappedTool(GET_DECISION,
                        "Fetch the stored decision of an application by its number, with its trace",
                        NUMBER_SCHEMA, turn, false,
                        arguments -> getDecision(arguments, version, ruleSet, sandboxId, turn),
                        events::toolRejected),
                new CappedTool(SIMULATE,
                        "Re-evaluate a stored decision with some case fields changed; nothing is stored",
                        SIMULATE_SCHEMA, turn, true,
                        arguments -> simulate(arguments, version, ruleSet, sandboxId, turn), events::toolRejected),
                new CappedTool(GET_DECISION_STATS,
                        "Count the outcomes, the deciding rules and the flags of this session's decisions",
                        NO_ARGUMENTS_SCHEMA, turn, false,
                        arguments -> stats(arguments, version, ruleSet, sandboxId, turn), events::toolRejected),
                new CappedTool(LIST_RULES,
                        "List this version's rules in evaluation order, all of them or only those carrying one tag",
                        TAG_SCHEMA, turn, false,
                        arguments -> listRules(arguments, ruleSet, turn), events::toolRejected));
    }

    /** Document 4: outcome counts, top deciding rules and flag counts, over this sandbox's decisions alone. */
    private String stats(String arguments, PublishedVersion version, RuleSet ruleSet, UUID sandboxId, ChatTurn turn) {
        ToolArguments.none(arguments);
        Aggregates aggregates = decisions.stats(version.versionId(), sandboxId);
        ObjectNode body = JSON.createObjectNode();
        body.put("decisions", aggregates.decisions());
        body.put("errors", aggregates.errors());
        body.set("outcomes", JSON.valueToTree(aggregates.outcomes()));
        ArrayNode top = body.putArray("topDecidingRules");
        aggregates.topDecidingRules().forEach(rule ->
                top.addObject().put("ruleId", rule.ruleId()).put("count", rule.count()));
        body.set("flagCounts", JSON.valueToTree(aggregates.flagCounts()));
        List<String> named = aggregates.topDecidingRules().stream().map(Aggregates.TopRule::ruleId).toList();
        return ToolResults.named(GET_DECISION_STATS, withSupplied(body, ruleSet, turn, named));
    }

    /** Document 4: the version's rules, or only those carrying the tag, each supplied so an answer may cite it. */
    private String listRules(String arguments, RuleSet ruleSet, ChatTurn turn) {
        ObjectNode listing = RuleListing.of(ruleSet, ToolArguments.tag(arguments));
        return ToolResults.named(LIST_RULES, withSupplied(listing, ruleSet, turn, RuleListing.ruleIdsOf(listing)));
    }

    private String getDecision(String arguments, PublishedVersion version, RuleSet ruleSet, UUID sandboxId,
            ChatTurn turn) {
        int number = ToolArguments.applicationNumber(arguments);
        ObjectNode decision = stored(version, sandboxId, number).decision();
        ChatCitation citation = new ChatCitation("d:" + number, ChatCitation.Kind.DECISION, null,
                decision.path("decidingRuleId").asString(null), null, number, decision.path("outcome").asString(null),
                null);
        turn.supply(citation);
        return ToolResults.result(citation.id(), withSources(decision, ruleSet, turn));
    }

    private String simulate(String arguments, PublishedVersion version, RuleSet ruleSet, UUID sandboxId,
            ChatTurn turn) {
        int number = ToolArguments.applicationNumber(arguments, "overrides");
        ObjectNode overrides = ToolArguments.overrides(arguments, ruleSet);
        DecisionView stored = stored(version, sandboxId, number);
        ObjectNode simulation;
        try {
            simulation = decisions.simulate(version, decisions.inputOf(stored), overrides);
        } catch (CaseInvalidException e) {
            throw new ToolRefusedException("invalid_arguments", "The engine refused these overrides: "
                    + e.problems().stream().map(problem -> problem.path() + " " + problem.code())
                            .collect(Collectors.joining(", ")));
        }
        String detail = ToolResults.overridesText(overrides);
        ChatCitation citation = new ChatCitation("sim:d" + number + ":" + detail, ChatCitation.Kind.SIMULATION, null,
                simulation.path("decidingRuleId").asString(null), null, number,
                simulation.path("outcome").asString(null), detail);
        turn.supply(citation);
        return ToolResults.result(citation.id(), withSources(simulation, ruleSet, turn));
    }

    /**
     * The result with the sources it supplies listed for the model to cite, the rule that decided it and the paragraph
     * that rule quotes, each supplied to the turn (Document 4, Citation marker protocol).
     */
    private static ObjectNode withSources(ObjectNode result, RuleSet ruleSet, ChatTurn turn) {
        String decidingRuleId = result.path("decidingRuleId").asString(null);
        return withSupplied(result, ruleSet, turn, decidingRuleId == null ? List.of() : List.of(decidingRuleId));
    }

    /** The same, for a result that names several rules: each rule and the paragraph it quotes, once, in order. */
    private static ObjectNode withSupplied(ObjectNode result, RuleSet ruleSet, ChatTurn turn, List<String> ruleIds) {
        List<String> sources = ToolResults.sourcesOfEach(ruleSet, ruleIds);
        sources.forEach(turn::supply);
        ObjectNode cited = result.deepCopy();
        sources.forEach(cited.putArray("sources")::add);
        return cited;
    }

    /** The decision the application number names, or a refusal the model reads (RT-03: another sandbox's id). */
    private DecisionView stored(PublishedVersion version, UUID sandboxId, int number) {
        return decisions.ofApplication(version, sandboxId, number).orElseThrow(() ->
                new ToolRefusedException("not_found", "No decision of application " + number + " in this session."));
    }
}
