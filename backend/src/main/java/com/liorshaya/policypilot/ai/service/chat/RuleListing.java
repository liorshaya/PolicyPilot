package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.rules.model.Action;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * What {@code listRules(tag?)} answers (Document 4, Tools available to the answer prompt: "Rule ids, labels,
 * priorities, outcomes"). The rules come back in the order the engine evaluates them, by ascending priority, so an
 * answer that reads the list reads it the way a decision is actually made; a rule that does not decide is listed by
 * what it does instead, the field it derives or the flag it attaches, and a disabled rule says so, because it is
 * never evaluated and must not be read as one that still decides.
 */
public final class RuleListing {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private RuleListing() {}

    /** @param tag one of a rule's tags, or {@code null} for every rule of the version */
    public static ObjectNode of(RuleSet ruleSet, @Nullable String tag) {
        ObjectNode listing = JSON.createObjectNode();
        if (tag != null) {
            listing.put("tag", tag);
        }
        ArrayNode rules = listing.putArray("rules");
        ruleSet.rules().stream()
                .filter(rule -> tag == null || (rule.tags() != null && rule.tags().contains(tag)))
                .sorted(Comparator.comparingInt(Rule::priority).thenComparing(Rule::id))
                .forEach(rule -> rules.add(lineOf(rule)));
        return listing;
    }

    /** The rule ids a listing names, which the turn supplies as sources so the answer may cite them. */
    public static List<String> ruleIdsOf(ObjectNode listing) {
        List<String> ids = new ArrayList<>();
        listing.required("rules").forEach(rule -> ids.add(rule.required("id").asString()));
        return ids;
    }

    private static ObjectNode lineOf(Rule rule) {
        ObjectNode line = JSON.createObjectNode();
        line.put("id", rule.id());
        line.put("label", rule.label());
        line.put("priority", rule.priority());
        for (Action action : rule.actions()) {
            switch (action) {
                case Action.Decide decide -> {
                    line.put("outcome", decide.outcome().json());
                    line.put("terminal", decide.isTerminal());
                }
                case Action.SetField set -> line.withArray("sets").add(set.field());
                case Action.Flag flag -> line.withArray("flags").add(flag.code());
            }
        }
        if (rule.tags() != null && !rule.tags().isEmpty()) {
            rule.tags().forEach(line.putArray("tags")::add);
        }
        if (!rule.isEnabled()) {
            line.put("enabled", false);
        }
        return line;
    }
}
