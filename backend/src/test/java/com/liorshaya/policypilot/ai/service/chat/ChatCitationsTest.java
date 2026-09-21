package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The citations event (Document 4, Marker resolution: paragraph or rule links, decision links, simulation details),
 * in the order the answer first cited each source. Paragraph and rule details are the lending fixture's.
 */
class ChatCitationsTest {

    private static final RuleSet LENDING = new RuleSetMapper().toRuleSet(Fixtures.lendingV1());

    // Expected: paragraph 2, the decision the tool supplied, R-330 with its label and paragraph 7, in that order, and
    // p:99, which the version has not, dropped
    @Test
    void citesChunksAndToolResultsInTheOrderTheAnswerCitedThem() {
        ChatTurn turn = new ChatTurn(Set.of("p:2", "r:R-330"));
        ChatCitation decision = new ChatCitation("d:17", ChatCitation.Kind.DECISION, null, "R-330", null, 17, "refer",
                null);
        turn.supply(decision);

        List<ChatCitation> citations = ChatCitations.of(List.of("p:2", "d:17", "r:R-330", "p:99"), turn, LENDING,
                paragraphs());

        assertThat(citations).containsExactly(
                new ChatCitation("p:2", ChatCitation.Kind.PARAGRAPH, 2, null, null, null, null, null),
                decision,
                new ChatCitation("r:R-330", ChatCitation.Kind.RULE, 7, "R-330", "בדיקת חתם: אירוע אשראי אחד ללא ערב",
                        null, null, null));
    }

    // A source a tool result names is supplied like a retrieved chunk and cited as the version's paragraph. Expected:
    // p:7, which retrieval did not supply, citable once the tool supplied it, as paragraph 7
    @Test
    void aSourceAToolResultNamesIsCitedAsTheVersionsParagraph() {
        ChatTurn turn = new ChatTurn(Set.of("p:2"));

        turn.supply("p:7");

        assertThat(turn.supplied("p:7")).isTrue();
        assertThat(ChatCitations.of(List.of("p:7"), turn, LENDING, paragraphs()))
                .containsExactly(new ChatCitation("p:7", ChatCitation.Kind.PARAGRAPH, 7, null, null, null, null, null));
    }

    private static List<PolicyVersionRef.Paragraph> paragraphs() {
        List<PolicyVersionRef.Paragraph> paragraphs = new ArrayList<>();
        List<String> texts = Fixtures.lendingParagraphs();
        for (int i = 0; i < texts.size(); i++) {
            paragraphs.add(new PolicyVersionRef.Paragraph(UUID.randomUUID(), i + 1, texts.get(i)));
        }
        return paragraphs;
    }
}
