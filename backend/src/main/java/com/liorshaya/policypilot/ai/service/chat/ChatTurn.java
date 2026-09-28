package com.liorshaya.policypilot.ai.service.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * What one turn of the chat has supplied and spent (Document 4, Prompt 4): the ids the answer may cite, the chunks
 * retrieved and those the tools added, the citation each tool result becomes and what the engine decided in it, the
 * calls the tools ran, each reported as it ends, and the caps of Document 5 (four calls, one {@code simulate}). A turn
 * past a cap is overrun, and the answer ends with the fixed sentence.
 */
public final class ChatTurn {

    /** Document 5, Tool call volume. */
    public static final int MAX_CALLS = 4;
    public static final int MAX_SIMULATIONS = 1;

    private final Set<String> supplied = ConcurrentHashMap.newKeySet();
    private final Map<String, ChatCitation> fromTools = new ConcurrentHashMap<>();
    private final Map<String, ToolCallReport.Decided> decided = new ConcurrentHashMap<>();
    private final List<ToolCallRecord> calls = new ArrayList<>();
    private final Consumer<ToolCallReport> reports;
    private int simulations;
    private volatile boolean overrun;

    /** A turn whose calls nobody is told of as they end. */
    public ChatTurn(Set<String> retrieved) {
        this(retrieved, report -> { });
    }

    /** @param reports told of each call as it ends (Document 2, the {@code tool} event) */
    public ChatTurn(Set<String> retrieved, Consumer<ToolCallReport> reports) {
        supplied.addAll(retrieved);
        this.reports = reports;
    }

    /**
     * One call a tool ran: its name, the arguments as the model wrote them, and how it ended: the id its result may be
     * cited by, or {@code refused:<reason>}.
     */
    public record ToolCallRecord(String tool, String arguments, String outcome) {}

    /** Whether another call of this kind fits the caps; the first one that does not marks the turn overrun. */
    public synchronized boolean admit(boolean simulation) {
        if (calls.size() >= MAX_CALLS || (simulation && simulations >= MAX_SIMULATIONS)) {
            overrun = true;
            return false;
        }
        if (simulation) {
            simulations++;
        }
        return true;
    }

    /** Records a call as it ends and reports it, with its time and what the engine decided in it. */
    public void record(ToolCallRecord call, long micros) {
        synchronized (this) {
            calls.add(call);
        }
        reports.accept(ToolCallReport.of(call, micros, decided.get(call.outcome())));
    }

    /** A tool result the answer may now cite. */
    public void supply(ChatCitation citation) {
        supplied.add(citation.id());
        fromTools.put(citation.id(), citation);
    }

    /** A decision or a simulation a tool result holds, and what the engine decided in it. */
    public void supply(ChatCitation citation, ToolCallReport.Decided what) {
        supply(citation);
        decided.put(citation.id(), what);
    }

    /** A source a tool result names, cited like a retrieved chunk: the deciding rule or the paragraph it quotes. */
    public void supply(String id) {
        supplied.add(id);
    }

    public boolean supplied(String id) {
        return supplied.contains(id);
    }

    public Optional<ChatCitation> fromTool(String id) {
        return Optional.ofNullable(fromTools.get(id));
    }

    public synchronized List<ToolCallRecord> calls() {
        return List.copyOf(calls);
    }

    public boolean overrun() {
        return overrun;
    }
}
