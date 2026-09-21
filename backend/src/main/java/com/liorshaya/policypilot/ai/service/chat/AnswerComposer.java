package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.ai.ChatTool;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.rules.model.Language;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;

/**
 * One answer as it streams (Document 4, Prompt 4): the gateway's pieces go through the marker resolver, what may be
 * shown goes through the denylist scan and on to the client, and the answer ends in one of three ways. A turn past the
 * tool caps ends with the fixed sentence and cites nothing; an answer that says the documents do not cover the
 * question cites nothing (Document 4: an answer that contains it must contain no markers); any other cites what the
 * resolver kept. Removed markers are counted as hallucinated citations.
 */
public final class AnswerComposer {

    private final LlmGateway gateway;
    private final OutputDenylist denylist;
    private final SecurityEvents events;
    private final MeterRegistry meters;
    private final Map<Language, String> toolLimit;

    public AnswerComposer(LlmGateway gateway, OutputDenylist denylist, SecurityEvents events, MeterRegistry meters,
            Map<Language, String> toolLimit) {
        this.gateway = gateway;
        this.denylist = denylist;
        this.events = events;
        this.meters = meters;
        this.toolLimit = Map.copyOf(toolLimit);
    }

    /** What an answer came to: the text shown, the ids it cites, and what the call cost. */
    public record Answer(String text, List<String> cited, TokenUsage usage) {

        public Answer {
            cited = List.copyOf(cited);
        }
    }

    /**
     * Streams one answer to {@code sink}.
     *
     * @param notCoveredSentence the fixed sentence of the language, which the answer may use to refuse
     * @throws AnswerWithheldException when what would be shown carries a secret; nothing more is sent
     */
    public Answer compose(PromptSpec spec, List<ChatTool> tools, ChatTurn turn, Language language,
            String notCoveredSentence, ChatEvents sink) {
        MarkerResolver resolver = new MarkerResolver(turn::supplied);
        StringBuilder shown = new StringBuilder();
        TokenUsage usage = gateway.stream(spec, tools, piece -> {
            if (!turn.overrun()) {
                show(resolver.accept(piece), shown, spec, sink);
            }
        });
        if (!turn.overrun()) {
            show(resolver.finish(), shown, spec, sink);
        }
        if (resolver.dropped() > 0) {
            meters.counter("ai.citation.hallucinated", "prompt", spec.promptVersion()).increment(resolver.dropped());
        }
        String text = shown.toString();
        if (turn.overrun()) {
            String sentence = (text.isEmpty() ? "" : " ") + toolLimit.get(language);
            sink.token(sentence);
            return new Answer(text + sentence, List.of(), usage);
        }
        if (text.contains(notCoveredSentence)) {
            return new Answer(text, List.of(), usage);
        }
        return new Answer(text, resolver.cited(), usage);
    }

    private void show(String piece, StringBuilder shown, PromptSpec spec, ChatEvents sink) {
        if (piece.isEmpty()) {
            return;
        }
        shown.append(piece);
        if (denylist.leaks(shown.toString())) {
            events.outputDenylisted(spec.promptName() + "/" + spec.promptVersion(), "secret");
            throw new AnswerWithheldException();
        }
        sink.token(piece);
    }
}
