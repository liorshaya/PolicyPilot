package com.liorshaya.policypilot.support;

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

    private final Path recordings;
    private final Deque<Supplier<String>> scripted;
    private final List<PromptSpec> asked = new ArrayList<>();

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

    private String replay(PromptSpec spec) {
        if (recordings == null) {
            throw new IllegalStateException("no answer left for " + spec.promptName() + " attempt " + spec.attempt());
        }
        Path file = recordings
                .resolve(spec.promptName())
                .resolve(spec.promptVersion())
                .resolve(Hashes.sha256Hex(spec.system() + "\u001f" + spec.user()) + ".json");
        if (!Files.exists(file)) {
            throw new IllegalStateException("no recording at " + file
                    + "; a new prompt version or a new input needs one live run to create it (Document 6)");
        }
        try {
            JsonNode recording = JSON.readTree(Files.readString(file));
            return recording.get("response").asString();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
