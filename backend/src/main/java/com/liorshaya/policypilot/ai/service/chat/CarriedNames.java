package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.rag.service.QuestionSignals;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The names a conversation carries into a follow-up (Document 4, Retrieval Pipeline, Follow-up): the rule ids, field
 * names and decision number of the latest of the last 10 turns' questions that names any. A later turn whose question
 * names nothing keeps of them only the rule ids its answer cites, and the decision when its answer cites a decision
 * or a simulation, so an answer that cites none of them ends the carry.
 */
public final class CarriedNames {

    private CarriedNames() {}

    /** One earlier exchange: what was asked and what the answer shown for it cited. */
    public record Turn(String question, List<ChatCitation> cited) {

        public Turn {
            cited = List.copyOf(cited);
        }
    }

    /** What the session's turns, oldest first, carry into the next question. */
    public static QuestionSignals of(List<Turn> turns, RuleSet ruleSet) {
        QuestionSignals carried = QuestionSignals.NOTHING;
        for (Turn turn : turns.subList(Math.max(0, turns.size() - ChatHistory.TURNS), turns.size())) {
            QuestionSignals asked = QuestionSignals.of(turn.question(), ruleSet);
            carried = asked.namesSomething()
                    ? QuestionSignals.naming(asked.ruleIds(), asked.fieldNames(), asked.namesADecision())
                    : kept(carried, turn.cited());
        }
        return carried;
    }

    /** What is left of the names after a turn that named nothing: what its answer still cites. */
    private static QuestionSignals kept(QuestionSignals carried, List<ChatCitation> cited) {
        Set<String> ruleIds = cited.stream().filter(citation -> citation.kind() == ChatCitation.Kind.RULE)
                .map(ChatCitation::ruleId).filter(Objects::nonNull).filter(carried.ruleIds()::contains)
                .collect(Collectors.toSet());
        boolean decision = carried.namesADecision() && cited.stream()
                .anyMatch(citation -> citation.kind() == ChatCitation.Kind.DECISION
                        || citation.kind() == ChatCitation.Kind.SIMULATION);
        return QuestionSignals.naming(ruleIds, Set.of(), decision);
    }
}
