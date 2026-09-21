package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.ai.ChatTool;
import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.rules.model.Language;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.RecordedGateway.Streamed;
import com.liorshaya.policypilot.support.RecordedGateway.ToolCall;
import com.liorshaya.policypilot.support.Requirement;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * One answer as it streams (Document 4, Prompt 4): the resolver's text goes to the client, the denylist watches it,
 * and the answer ends with its citations, with none when it refuses, or with the fixed sentence when the turn ran past
 * the tool caps (Document 5, Tool call volume). The model is the recorded gateway; the sentences are Document 4's.
 */
@Requirement({"FR-13", "FR-14"})
class AnswerComposerTest {

    private static final String NOT_COVERED_HE =
            "המסמכים אינם עוסקים בשאלה הזו; אפשר לשאול על כלל, על סעיף או על מספר בקשה.";
    private static final String TOOL_LIMIT_HE =
            "השאלה דורשת יותר בדיקות ממה שתשובה אחת רשאית לבצע; אפשר לשאול על בקשה אחת או על שינוי אחד בכל פעם.";

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final SecurityEvents events = new SecurityEvents(meters, "salt".getBytes(StandardCharsets.UTF_8));
    private final List<String> shown = new ArrayList<>();
    private final ChatEvents sink = new Collecting(shown);

    // Document 4, Marker resolution. Expected: the text without [[p:9]], which no chunk supplied, p:2 cited, and one
    // hallucinated citation counted
    @Test
    void showsWhatTheResolverKeepsAndCitesTheSuppliedMarkers() {
        ChatTurn turn = new ChatTurn(Set.of("p:2"));

        AnswerComposer.Answer answer = composer(Streamed.text("Term: 84 months.[[p:2]] Rate: 12%.[[p:9]]"))
                .compose(spec(), List.of(), turn, Language.HE, NOT_COVERED_HE, sink);

        assertThat(answer.text()).isEqualTo("Term: 84 months.[[p:2]] Rate: 12%.");
        assertThat(String.join("", shown)).isEqualTo(answer.text());
        assertThat(answer.cited()).containsExactly("p:2");
        assertThat(answer.usage()).isEqualTo(new TokenUsage(1_000, 200));
        assertThat(meters.counter("ai.citation.hallucinated", "prompt", "v1").count()).isEqualTo(1.0);
    }

    // Document 5: more than four calls ends the turn with the fixed sentence. Expected: the Hebrew sentence of
    // tool-limit.yml as the answer, sent to the client, and nothing cited
    @Test
    void aTurnPastTheCapsEndsWithTheFixedSentence() {
        ChatTurn turn = new ChatTurn(Set.of("p:2"));
        ToolCall call = new ToolCall("getDecision", "{\"applicationNumber\":17}");

        AnswerComposer.Answer answer = composer(Streamed.after("Referred.[[d:17]]", call, call, call, call, call))
                .compose(spec(), List.of(decisionTool(turn)), turn, Language.HE, NOT_COVERED_HE, sink);

        assertThat(answer.text()).isEqualTo(TOOL_LIMIT_HE);
        assertThat(String.join("", shown)).isEqualTo(TOOL_LIMIT_HE);
        assertThat(answer.cited()).isEmpty();
    }

    // Document 4, Citation marker protocol: "an answer that contains it must contain no markers". Expected: the model's
    // refusal shown as written, and nothing cited
    @Test
    void anAnswerThatSaysTheDocumentsAreSilentCitesNothing() {
        ChatTurn turn = new ChatTurn(Set.of("p:2"));

        AnswerComposer.Answer answer = composer(Streamed.text(NOT_COVERED_HE + " The term is covered.[[p:2]]"))
                .compose(spec(), List.of(), turn, Language.HE, NOT_COVERED_HE, sink);

        assertThat(answer.text()).startsWith(NOT_COVERED_HE);
        assertThat(answer.cited()).isEmpty();
    }

    // RT-02: the denylist scan stops an answer that carries a secret. Expected: withheld, the secret never shown, and
    // the event counted
    @Test
    void anAnswerCarryingASecretIsWithheldBeforeTheSecretIsShown() {
        ChatTurn turn = new ChatTurn(Set.of());

        assertThatThrownBy(() -> composer(Streamed.text("The code is testcode, as you asked."))
                .compose(spec(), List.of(), turn, Language.HE, NOT_COVERED_HE, sink))
                .isInstanceOf(AnswerWithheldException.class);
        assertThat(String.join("", shown)).doesNotContain("testcode");
        assertThat(meters.counter(SecurityEvents.OUTPUT_DENYLIST, "prompt", "answer/v1", "pattern", "secret").count())
                .isEqualTo(1.0);
    }

    private AnswerComposer composer(Streamed answer) {
        return new AnswerComposer(RecordedGateway.streaming(answer), new OutputDenylist(List.of("testcode")), events,
                meters, FixedSentences.toolLimit());
    }

    private ChatTool decisionTool(ChatTurn turn) {
        return new CappedTool("getDecision", "fetch", "{}", turn, false, arguments -> {
            ChatCitation citation = new ChatCitation("d:17", ChatCitation.Kind.DECISION, null, "R-330", null, 17,
                    "refer", null);
            turn.supply(citation);
            return ToolResults.result("d:17", JsonMapper.builder().build().createObjectNode().put("outcome", "refer"));
        }, (tool, reason) -> events.toolRejected(tool, reason));
    }

    private static PromptSpec spec() {
        return new PromptSpec("answer", "v1", ModelRole.FAST, "system", "user " + UUID.randomUUID(), null, 0.3, 1200,
                Duration.ofSeconds(60), 1);
    }

    /** The client, as far as the composer can tell: every token it was sent. */
    private record Collecting(List<String> tokens) implements ChatEvents {

        @Override
        public void token(String text) {
            tokens.add(text);
        }

        @Override
        public void citations(List<ChatCitation> citations) {
            throw new UnsupportedOperationException("the use case sends the citations");
        }

        @Override
        public void usage(TokenUsage usage, int toolCalls) {
            throw new UnsupportedOperationException("the use case sends the usage");
        }

        @Override
        public void done(UUID messageId) {
            throw new UnsupportedOperationException("the use case stores the answer");
        }
    }
}
