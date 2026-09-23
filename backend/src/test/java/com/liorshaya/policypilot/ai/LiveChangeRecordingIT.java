package com.liorshaya.policypilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.adapter.SpringAiEmbeddingGateway;
import com.liorshaya.policypilot.ai.prompt.DslCheatSheet;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.ai.service.ChangeService;
import com.liorshaya.policypilot.ai.service.Proposal;
import com.liorshaya.policypilot.common.Hashes;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.RecordedEmbeddingGateway;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The first live runs of the change pipeline (Work Plan day 12; Document 6: every new AI path gets one live run before
 * it is trusted). Tagged {@code live}, so CI never runs it; it needs a real {@code OPENAI_API_KEY}, and each method is
 * run on its own, the embedding first:
 *
 * <pre>{@code
 * OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test='LiveChangeRecordingIT#theChangeRequestsAreEmbedded' \
 *     -Dlive.tag= -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
 * OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test='LiveChangeRecordingIT#theScriptedRequestIsProposed' \
 *     -Dlive.tag= -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
 * }</pre>
 *
 * <p>The startup embedding of the seeded version uses the offline fake, as in every API test; only the texts and the
 * prompts a method names reach the provider.
 */
@Tag("live")
@TestPropertySource(properties = {"spring.ai.openai.api-key=${OPENAI_API_KEY}",
        "policypilot.ai.daily-token-budget=400000"})
@Isolated
class LiveChangeRecordingIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** The corpus name of the change requests' vectors, beside the questions' and each policy's. */
    private static final String CORPUS = "changes";
    private static final Path RECORDINGS = Path.of("..", "fixtures", "eval", "recordings", "openai", "change");

    @Autowired
    private SpringAiEmbeddingGateway provider;

    @Autowired
    private LlmGateway gateway;

    @Autowired
    private PolicyPilotProperties properties;

    /**
     * Every text the change tests send, embedded by the real provider and written to the embedding recordings, so
     * candidate selection runs offline on the vectors the provider returned (fixtures/eval/recordings/README.md).
     */
    @Test
    void theChangeRequestsAreEmbedded() {
        List<String> texts = ChangeRequests.texts();
        List<float[]> vectors = provider.embedAll(texts);

        ObjectNode file = JSON.createObjectNode();
        file.put("provider", "openai").put("model", "text-embedding-3-small").put("dimension", provider.dimension())
                .put("corpus", CORPUS);
        ArrayNode embeddings = file.putArray("embeddings");
        for (int i = 0; i < texts.size(); i++) {
            embeddings.add(RecordedEmbeddingGateway.entry(texts.get(i), vectors.get(i)));
        }
        try {
            Files.writeString(RecordedEmbeddingGateway.RECORDINGS.resolve(CORPUS + ".json"),
                    file.toPrettyString() + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the recording", e);
        }

        // the file just written replays every text, which is what the offline tests will ask of it
        assertThat(RecordedEmbeddingGateway.replaying(provider.dimension()).embedAll(texts)).hasSize(texts.size());
    }

    /**
     * The scripted request (CR-1) on the seeded lending version, asked of the strong model through the service itself,
     * so a repair it needs is asked and recorded too. The candidates are the ones candidate selection gives the
     * request on the recorded vectors (ChangeRequests.scriptedCandidates), which is what the offline tests check
     * before they replay this recording.
     */
    @Test
    void theScriptedRequestIsProposed() {
        ChangeService service = new ChangeService(new RecordingModel(gateway, properties.ai().models().strong()),
                new PromptRegistry(PromptRegistry.PROMPTS, Map.of()), new DslCheatSheet());

        Proposal proposal = service.propose(ChangeRequests.lendingBase(), ChangeRequests.scripted(),
                ChangeRequests.scriptedCandidates(), stage -> { });

        System.out.println("change/v1 on CR-1: " + proposal.repairs() + " repairs, valid " + proposal.valid()
                + ", refused " + proposal.refused());
        System.out.println(proposal.answer().toPrettyString());
        // Work Plan day 12, Done when: the scripted request proposes patches to R-170 and R-410, pending
        assertThat(proposal.valid()).isTrue();
        assertThat(proposal.answer().required("patches").valueStream()
                .map(patch -> patch.path("ruleId").asString(""))).contains("R-170", "R-410");
    }

    /**
     * The live gateway, writing every answer it gives under the hash of the prompt it answered and printing what each
     * call spent; {@code LiveChangePassIT} records the other five requests through it.
     */
    static final class RecordingModel implements LlmGateway {

        private final LlmGateway live;
        private final String model;
        private long spentIn;
        private long spentOut;

        RecordingModel(LlmGateway live, String model) {
            this.live = live;
            this.model = model;
        }

        @Override
        public <T> Completion<T> complete(PromptSpec spec, Class<T> type) {
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
            throw new UnsupportedOperationException("the change prompt is not streamed");
        }

        /** What every call so far spent, as the provider reported it. */
        String spent() {
            return spentIn + " input + " + spentOut + " output = " + (spentIn + spentOut) + " tokens";
        }

        /** Where the answer to a prompt is written, and where the replaying gateway looks for it. */
        static Path fileOf(PromptSpec spec) {
            return RECORDINGS.resolve(spec.promptVersion()).resolve(hashOf(spec) + ".json");
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
            Path file = fileOf(spec);
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, recording.toPrettyString());
            } catch (IOException e) {
                throw new UncheckedIOException("could not write the recording", e);
            }
        }
    }
}
