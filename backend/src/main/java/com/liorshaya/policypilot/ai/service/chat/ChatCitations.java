package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rag.service.Citation;
import com.liorshaya.policypilot.rag.service.CitationBuilder;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@code citations} event of an answer (Document 4, Marker resolution): each cited id, in the order the answer
 * first cited it, as a paragraph or rule link checked against the version (Document 5: provenance re-verified at
 * citation time) or as the decision or simulation a tool supplied this turn.
 */
public final class ChatCitations {

    private ChatCitations() {}

    public static List<ChatCitation> of(List<String> cited, ChatTurn turn, RuleSet ruleSet,
            List<PolicyVersionRef.Paragraph> paragraphs) {
        CitationBuilder chunks = new CitationBuilder(ruleSet, paragraphs);
        List<ChatCitation> citations = new ArrayList<>();
        for (String id : cited) {
            Optional<ChatCitation> fromTool = turn.fromTool(id);
            if (fromTool.isPresent()) {
                citations.add(fromTool.get());
            } else {
                chunks.cite(List.of(id)).stream().map(ChatCitations::fromChunk).forEach(citations::add);
            }
        }
        return citations;
    }

    private static ChatCitation fromChunk(Citation citation) {
        return new ChatCitation(citation.id(),
                citation.kind() == Citation.Kind.PARAGRAPH ? ChatCitation.Kind.PARAGRAPH : ChatCitation.Kind.RULE,
                citation.paragraph(), citation.ruleId(), citation.label(), null, null, null);
    }
}
