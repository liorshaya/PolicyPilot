package com.liorshaya.policypilot.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.AnswerCache;
import com.liorshaya.policypilot.ai.CachedAnswer;
import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.entity.ModelCallEntity;
import com.liorshaya.policypilot.ai.repository.ModelCallRepository;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Requirement;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The cache of the scripted chat answers (Document 2, Gateways: {@code AnswerCache}; Document 4, Serving the scripted
 * questions from the cache): an answer is kept with its tool calls and their results under the key of every cached
 * call, the first one kept stands, and serving one writes the ledger row of a cache hit. Each test renders a prompt of
 * its own, so no test can find another's entry; the ledger is append-only, so each reads back its own prompt version.
 */
@Requirement({"FR-13", "NFR-6"})
class ScriptedAnswerCacheIT extends ApiIntegrationTest {

    private static final CachedAnswer REFERRED = new CachedAnswer("Referred.[[d:17]]", List.of(
            new CachedAnswer.ToolStep("getDecision", "{\"applicationNumber\":17}",
                    "<tool_result id=\"d:17\">{\"outcome\":\"refer\"}</tool_result>")));

    @Autowired
    private AnswerCache cache;

    @Autowired
    private ModelCallRepository calls;

    @Autowired
    private PolicyPilotProperties properties;

    @Autowired
    private MeterRegistry meters;

    // Document 4: "The entry holds the text shown and every tool call with its arguments and the exact result".
    // Expected: what was kept, found again as it went in
    @Test
    void aKeptAnswerIsFoundWithItsToolCallsAndTheirResults() {
        PromptSpec spec = spec("v1", "user " + UUID.randomUUID());

        cache.keep(spec, REFERRED);

        assertThat(cache.find(spec)).contains(REFERRED);
    }

    // Document 4: "the first such answer is kept and never overwritten". Expected: the first answer found
    @Test
    void theFirstAnswerKeptStands() {
        PromptSpec spec = spec("v1", "user " + UUID.randomUUID());

        cache.keep(spec, REFERRED);
        cache.keep(spec, new CachedAnswer("Approved.", List.of()));

        assertThat(cache.find(spec)).contains(REFERRED);
    }

    // Document 4: the key is "the hash of the prompt name and version, the model and the rendered prompts, so the
    // retrieved chunks and the history are part of it". Expected: another rendered prompt and another prompt version
    // both miss
    @Test
    void anotherRenderedPromptOrPromptVersionMisses() {
        String user = "user " + UUID.randomUUID();
        cache.keep(spec("v1", user), REFERRED);

        assertThat(cache.find(spec("v1", user + " and one more turn of history"))).isEmpty();
        assertThat(cache.find(spec("v2", user))).isEmpty();
    }

    // Document 4: a served answer "spends no tokens and writes a ledger row marked as a cache hit". Expected: one row
    // of this prompt version, on the fast model the answer prompt names, no tokens, no latency, VALID, a cache hit; and
    // the hit counted
    @Test
    void servingAnAnswerWritesALedgerRowMarkedAsACacheHit() {
        String version = "v1-" + UUID.randomUUID().toString().substring(0, 8);
        double hits = meters.counter("policypilot.ai.cache.hit", "prompt", "answer").count();

        cache.served(spec(version, "user"));

        List<ModelCallEntity> written = calls.findByPromptNameAndPromptVersionOrderByAtAsc("answer", version);
        assertThat(written).hasSize(1);
        ModelCallEntity call = written.getFirst();
        assertThat(call.model()).isEqualTo(properties.ai().models().fast());
        assertThat(call.cacheHit()).isTrue();
        assertThat(call.inputTokens()).isZero();
        assertThat(call.outputTokens()).isZero();
        assertThat(call.latencyMs()).isZero();
        assertThat(call.validationResult()).isEqualTo(ModelCallLedger.Results.VALID);
        assertThat(meters.counter("policypilot.ai.cache.hit", "prompt", "answer").count()).isEqualTo(hits + 1);
    }

    private static PromptSpec spec(String version, String user) {
        return new PromptSpec("answer", version, ModelRole.FAST, "system", user, null, 0.3, 1200,
                Duration.ofSeconds(60), 1);
    }
}
