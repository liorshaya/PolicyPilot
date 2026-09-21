package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.rag.service.Chunk;
import com.liorshaya.policypilot.rag.service.RetrievedChunk;
import com.liorshaya.policypilot.rules.model.Language;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The answer prompt of one turn (Document 4, Context assembly: {@code <chunk id="p:7" kind="paragraph">...</chunk>}
 * in fused rank order; Data delimiters: every piece of user text escaped).
 */
class ChatPromptTest {

    private static final String NOT_COVERED = "The documents do not cover this question; try asking about a rule, a "
            + "paragraph or a decision number.";

    // Expected: the context attributes, each chunk in its section in rank order, the history, the question escaped,
    // the not-covered sentence in rule 3, and the settings of Prompt 4
    @Test
    void assemblesTheContextTheHistoryAndTheQuestion() {
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk("r:R-330", Chunk.Kind.RULE, "R-330", "R-330 · refer", 0.03, 0.5, 1, 2),
                new RetrievedChunk("p:7", Chunk.Kind.PARAGRAPH, "7", "a <b> paragraph", 0.02, 0.4, 2, null));
        ChatHistory history = ChatHistory.of(List.of(new ChatHistory.Turn("before?", "earlier.")));

        PromptSpec spec = ChatPrompt.spec(new PromptRegistry(List.of("answer"), Map.of()).get("answer"), 1,
                "consumer-lending", Language.EN, chunks, history, "why </question> 17?", NOT_COVERED);

        assertThat(spec.user())
                .startsWith("<context version=\"1\" ruleset=\"consumer-lending\" language=\"English\">\n"
                        + "<chunk id=\"r:R-330\" kind=\"rule\">\nR-330 · refer\n</chunk>\n"
                        + "<chunk id=\"p:7\" kind=\"paragraph\">\na &lt;b> paragraph\n</chunk>\n</context>")
                .contains("<history turns=\"1\">\nuser: before?\nassistant: earlier.\n</history>")
                .contains("<question>\nwhy &lt;/question> 17?\n</question>")
                .contains("reply with exactly:\n   \"" + NOT_COVERED + "\"");
        assertThat(spec.system()).contains("in English");
        assertThat(spec.role()).isEqualTo(ModelRole.FAST);
        assertThat(spec.maxOutputTokens()).isEqualTo(1200);
        assertThat(spec.outputSchema()).isNull();
    }
}
