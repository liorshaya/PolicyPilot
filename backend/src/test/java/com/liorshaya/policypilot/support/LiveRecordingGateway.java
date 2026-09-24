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
import java.util.List;
import java.util.function.Consumer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The live gateway of a recording pass (Document 6, AI Layer Testing: recordings are made by live passes and replayed
 * by every other test): it writes every answer under {@code fixtures/eval/recordings/<provider>/<prompt>/<version>/},
 * keyed by the hash of the prompt it answered, and prints what each call spent. It asks nothing that could take the
 * pass past its budget: before each call it adds a high estimate of the call to what is spent, since the
 * application's own guard refuses only once the day's total has reached the budget, one call too late. The estimate
 * is the prompt's characters over a characters-per-token figure lower than any prompt of the pass measured, plus an
 * output allowance higher than any answer measured.
 */
public final class LiveRecordingGateway implements LlmGateway {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path RECORDINGS = Path.of("..", "fixtures", "eval", "recordings");

    private final String provider;
    private final LlmGateway live;
    private final String model;
    private final long budget;
    private final double charactersPerToken;
    private final int outputAllowance;
    private long spentIn;
    private long spentOut;

    public LiveRecordingGateway(String provider, LlmGateway live, String model, long budget,
            double charactersPerToken, int outputAllowance) {
        this.provider = provider;
        this.live = live;
        this.model = model;
        this.budget = budget;
        this.charactersPerToken = charactersPerToken;
        this.outputAllowance = outputAllowance;
    }

    @Override
    public <T> Completion<T> complete(PromptSpec spec, Class<T> type) {
        long estimate = (long) ((spec.system().length() + spec.user().length()) / charactersPerToken) + outputAllowance;
        if (spentIn + spentOut + estimate > budget) {
            throw new IllegalStateException("not asking " + spec.promptName() + "/" + spec.promptVersion() + ": "
                    + spent() + " spent, and the call could take " + estimate + " more of the " + budget);
        }
        Completion<T> answer = live.complete(spec, type);
        write(spec, String.valueOf(answer.value()));
        spentIn += answer.usage().inputTokens();
        spentOut += answer.usage().outputTokens();
        System.out.printf("%s/%s: %d input + %d output tokens%s%n", spec.promptName(), spec.promptVersion(),
                answer.usage().inputTokens(), answer.usage().outputTokens(), answer.cacheHit() ? ", cached" : "");
        return answer;
    }

    @Override
    public void forget(PromptSpec spec) {
        live.forget(spec);
    }

    @Override
    public TokenUsage stream(PromptSpec spec, List<ChatTool> tools, Consumer<String> tokens) {
        throw new UnsupportedOperationException("a recording pass records structured prompts only");
    }

    /** What every call so far spent, as the provider reported it. */
    public String spent() {
        return spentIn + " input + " + spentOut + " output = " + (spentIn + spentOut) + " tokens";
    }

    /** A provider's recordings, what {@code RecordedGateway.replaying} is given to replay that provider's answers. */
    public static Path directoryOf(String provider) {
        return RECORDINGS.resolve(provider);
    }

    /** Where a provider's answer to a prompt is written, and where the replaying gateway looks for it. */
    public static Path fileOf(String provider, PromptSpec spec) {
        return directoryOf(provider).resolve(spec.promptName()).resolve(spec.promptVersion())
                .resolve(hashOf(spec) + ".json");
    }

    private static String hashOf(PromptSpec spec) {
        return Hashes.sha256Hex(spec.system() + "\u001f" + spec.user());
    }

    private void write(PromptSpec spec, String response) {
        ObjectNode recording = JSON.createObjectNode();
        ObjectNode request = recording.putObject("request");
        request.put("prompt", spec.promptName());
        request.put("version", spec.promptVersion());
        request.put("model", model);
        request.put("inputHash", hashOf(spec));
        request.put("system", spec.system());
        request.put("user", spec.user());
        recording.put("response", response);
        Path file = fileOf(provider, spec);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, recording.toPrettyString());
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the recording", e);
        }
    }
}
