package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.ai.CachedAnswer;
import com.liorshaya.policypilot.ai.ChatTool;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.rules.model.Language;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One answer as it streams (Document 4, Prompt 4): the gateway's pieces go through the marker resolver, what may be
 * shown goes through the denylist scan and on to the client, and the answer ends in one of three ways. A turn past the
 * tool caps ends with the fixed sentence and cites nothing; an answer that says the documents do not cover the
 * question cites nothing (Document 4: an answer that contains it must contain no markers); any other cites what the
 * resolver kept. Removed markers are counted as hallucinated citations.
 *
 * <p>A cached answer is replayed the same way, once its tool calls have run again and returned what they returned when
 * it was written (Document 4, Serving the scripted questions from the cache).
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

    /**
     * What an answer came to: the text shown, the ids it cites, what the call cost, the tool calls it made with what
     * they returned, and whether the turn ran past the tool caps.
     */
    public record Answer(String text, List<String> cited, TokenUsage usage, List<CachedAnswer.ToolStep> steps,
            boolean overrun) {

        public Answer {
            cited = List.copyOf(cited);
            steps = List.copyOf(steps);
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
        List<CachedAnswer.ToolStep> steps = Collections.synchronizedList(new ArrayList<>());
        TokenUsage usage = gateway.stream(spec, tools.stream().map(tool -> recorded(tool, steps)).toList(), piece -> {
            if (!turn.overrun()) {
                show(resolver.accept(piece), shown, spec, sink);
            }
        });
        if (!turn.overrun()) {
            show(resolver.finish(), shown, spec, sink);
        }
        return ended(resolver, shown.toString(), usage, steps, turn, language, notCoveredSentence, spec, sink);
    }

    /**
     * Replays a cached answer without the model: its tool calls run again through this turn's tools, and only when
     * every one returns what it returned when the answer was written is the text shown, through the same resolver and
     * denylist as a live answer. Empty, with nothing shown, when a call returns something else or names a tool this
     * turn does not offer; the turn has then run those calls, and the caller answers live with a fresh one.
     *
     * @throws AnswerWithheldException when what would be shown carries a secret; nothing more is sent
     */
    public Optional<Answer> replay(PromptSpec spec, CachedAnswer cached, List<ChatTool> tools, ChatTurn turn,
            Language language, String notCoveredSentence, ChatEvents sink) {
        for (CachedAnswer.ToolStep step : cached.steps()) {
            Optional<ChatTool> tool = tools.stream().filter(offered -> offered.name().equals(step.tool())).findFirst();
            if (tool.isEmpty() || !tool.get().call(step.arguments()).equals(step.result())) {
                return Optional.empty();
            }
        }
        MarkerResolver resolver = new MarkerResolver(turn::supplied);
        StringBuilder shown = new StringBuilder();
        show(resolver.accept(cached.text()), shown, spec, sink);
        show(resolver.finish(), shown, spec, sink);
        return Optional.of(ended(resolver, shown.toString(), TokenUsage.NONE, cached.steps(), turn, language,
                notCoveredSentence, spec, sink));
    }

    private Answer ended(MarkerResolver resolver, String text, TokenUsage usage, List<CachedAnswer.ToolStep> steps,
            ChatTurn turn, Language language, String notCoveredSentence, PromptSpec spec, ChatEvents sink) {
        if (resolver.dropped() > 0) {
            meters.counter("ai.citation.hallucinated", "prompt", spec.promptVersion()).increment(resolver.dropped());
        }
        if (turn.overrun()) {
            String sentence = (text.isEmpty() ? "" : " ") + toolLimit.get(language);
            sink.token(sentence);
            return new Answer(text + sentence, List.of(), usage, steps, true);
        }
        if (text.contains(notCoveredSentence)) {
            return new Answer(text, List.of(), usage, steps, false);
        }
        return new Answer(text, resolver.cited(), usage, steps, false);
    }

    /** The tool, with each call it runs kept with what it returned. */
    private static ChatTool recorded(ChatTool tool, List<CachedAnswer.ToolStep> steps) {
        return new ChatTool() {
            @Override
            public String name() {
                return tool.name();
            }

            @Override
            public String description() {
                return tool.description();
            }

            @Override
            public String inputSchema() {
                return tool.inputSchema();
            }

            @Override
            public String call(String argumentsJson) {
                String result = tool.call(argumentsJson);
                steps.add(new CachedAnswer.ToolStep(tool.name(), argumentsJson, result));
                return result;
            }
        };
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
