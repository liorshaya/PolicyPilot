package com.liorshaya.policypilot.support;

import com.liorshaya.policypilot.ai.ChatTool;
import com.liorshaya.policypilot.ai.Completion;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.common.Hashes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The model in every test (Document 6, AI Layer Testing): nothing here ever reaches a provider. It answers either
 * from the recordings a live run left behind, keyed by the hash of the rendered prompt, or from a list of answers
 * a test hands it, which is how the adversarial shapes — invalid JSON, a document that breaks a semantic check,
 * a provider that fails — are exercised without a model.
 *
 * <p>A replaying gateway fails on a miss, so a new prompt version or a new input needs one live run to create its
 * recordings rather than silently answering something else.
 */
public final class RecordedGateway implements LlmGateway {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** How many characters a replayed stream sends per token. */
    private static final int PIECE = 7;

    private final Path recordings;
    private final Deque<Supplier<String>> scripted;
    private final List<PromptSpec> asked = new ArrayList<>();
    private final Deque<Streamed> streams = new ArrayDeque<>();
    private final List<String> toolResults = new ArrayList<>();

    private RecordedGateway(Path recordings, Deque<Supplier<String>> scripted) {
        this.recordings = recordings;
        this.scripted = scripted;
    }

    /** Replays what a live run recorded under {@code fixtures/eval/recordings/<provider>/<prompt>/<version>/}. */
    public static RecordedGateway replaying(Path recordings) {
        return new RecordedGateway(recordings, new ArrayDeque<>());
    }

    /** Answers the given texts in order, one per call; a call past the end fails the test. */
    public static RecordedGateway answering(String... answers) {
        Deque<Supplier<String>> scripted = new ArrayDeque<>();
        for (String answer : answers) {
            scripted.add(() -> answer);
        }
        return new RecordedGateway(null, scripted);
    }

    /** Answers with what the suppliers produce, so a test can make one call throw the way a provider would. */
    public static RecordedGateway scripted(List<Supplier<String>> answers) {
        return new RecordedGateway(null, new ArrayDeque<>(answers));
    }

    /** Streams the answers given, in order, one per call; each runs its tool calls first, as a model would. */
    public static RecordedGateway streaming(Streamed... answers) {
        RecordedGateway gateway = new RecordedGateway(null, new ArrayDeque<>());
        gateway.streams.addAll(List.of(answers));
        return gateway;
    }

    /** One streamed answer: the tool calls the model makes, in order, then its text. */
    public record Streamed(List<ToolCall> calls, String text) {

        public Streamed {
            calls = List.copyOf(calls);
        }

        /** An answer that calls no tool. */
        public static Streamed text(String text) {
            return new Streamed(List.of(), text);
        }

        /** An answer written after the tool calls given. */
        public static Streamed after(String text, ToolCall... calls) {
            return new Streamed(List.of(calls), text);
        }
    }

    /** A call the model makes: the tool's name and the arguments as it wrote them. */
    public record ToolCall(String tool, String arguments) {}

    /** What the tools returned to the model, in the order they ran, over every streamed call. */
    public List<String> toolResults() {
        return List.copyOf(toolResults);
    }

    /**
     * Replays a streamed answer: its tool calls through the tools the turn offered, then its text in pieces of
     * {@value #PIECE} characters, so a marker is split across tokens the way a provider splits it. A call to a tool
     * the turn did not offer fails the test.
     */
    @Override
    public TokenUsage stream(PromptSpec spec, List<ChatTool> tools, Consumer<String> tokens) {
        asked.add(spec);
        Streamed answer = streams.isEmpty() ? replayStream(spec) : streams.removeFirst();
        for (ToolCall call : answer.calls()) {
            ChatTool tool = tools.stream().filter(offered -> offered.name().equals(call.tool())).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "the model called " + call.tool() + ", which this turn did not offer"));
            toolResults.add(tool.call(call.arguments()));
        }
        int[] codePoints = answer.text().codePoints().toArray();
        for (int from = 0; from < codePoints.length; from += PIECE) {
            tokens.accept(new String(codePoints, from, Math.min(PIECE, codePoints.length - from)));
        }
        return new TokenUsage(1_000, 200);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Completion<T> complete(PromptSpec spec, Class<T> type) {
        asked.add(spec);
        if (type != String.class) {
            throw new IllegalArgumentException("the recorded gateway answers text; the caller parses it");
        }
        String answer = scripted.isEmpty() ? replay(spec) : scripted.removeFirst().get();
        return (Completion<T>) Completion.fromProvider(answer, new TokenUsage(1_000, 500));
    }

    /** Every spec the gateway was asked, in order: what a test asserts the rendered prompt on. */
    public List<PromptSpec> asked() {
        return List.copyOf(asked);
    }

    /** The last rendered user prompt, which is what the red-team fixtures look inside. */
    public String lastUserPrompt() {
        if (asked.isEmpty()) {
            throw new IllegalStateException("the gateway was never called");
        }
        return asked.getLast().user();
    }

    /** Writes what was asked beside the recordings, so a miss can be diffed against the recorded prompt. */
    private static void dump(PromptSpec spec) {
        try {
            Files.writeString(Path.of("target", "missed-prompt.txt"), spec.system() + "\u001f" + spec.user());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Streamed replayStream(PromptSpec spec) {
        JsonNode recording = recording(spec);
        List<ToolCall> calls = new ArrayList<>();
        recording.path("steps").forEach(step -> calls.add(
                new ToolCall(step.required("tool").asString(), step.required("arguments").asString())));
        return new Streamed(calls, recording.required("response").asString());
    }

    private String replay(PromptSpec spec) {
        return recording(spec).get("response").asString();
    }

    private JsonNode recording(PromptSpec spec) {
        if (recordings == null) {
            throw new IllegalStateException("no answer left for " + spec.promptName() + " attempt " + spec.attempt());
        }
        Path file = recordings
                .resolve(spec.promptName())
                .resolve(spec.promptVersion())
                .resolve(Hashes.sha256Hex(spec.system() + "\u001f" + spec.user()) + ".json");
        if (!Files.exists(file)) {
            dump(spec);
            throw new IllegalStateException("no recording at " + file
                    + "; a new prompt version or a new input needs one live run to create it (Document 6)");
        }
        try {
            return JSON.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
