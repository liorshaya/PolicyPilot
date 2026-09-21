package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.ai.prompt.Sections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The conversation memory as the answer prompt reads it (Document 4, Streaming and memory; Document 2, RAG pipeline,
 * Memory): the last 10 turns of the session, oldest first, one question and its answer each, escaped like any user
 * text so a turn cannot close the section it sits in.
 */
public record ChatHistory(int turnCount, String text) {

    /** Document 4: "the last 10 turns". */
    public static final int TURNS = 10;

    /** One earlier exchange: what was asked and what was shown in answer. */
    public record Turn(String question, String answer) {}

    /** The window over the session's turns, oldest first. */
    public static ChatHistory of(List<Turn> turns) {
        List<Turn> window = turns.subList(Math.max(0, turns.size() - TURNS), turns.size());
        String text = window.stream()
                .map(turn -> "user: " + Sections.escape(turn.question()) + "\nassistant: "
                        + Sections.escape(turn.answer()))
                .collect(Collectors.joining("\n"));
        return new ChatHistory(window.size(), text);
    }
}
