package com.liorshaya.policypilot.ai.adapter;

import com.liorshaya.policypilot.ai.AnswerCache;
import com.liorshaya.policypilot.ai.CachedAnswer;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.cache.ProposalCache;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The {@link AnswerCache} of the chat (Document 2, Cached demo outputs): the scripted answers in the response cache
 * beside the author's, under the same key, each stored as its text with its tool calls, and the ledger row a served
 * answer leaves, a cache hit on the model the prompt's role names that spent nothing.
 */
@Component
public class ScriptedAnswerCache implements AnswerCache {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ProposalCache cache;
    private final ModelCallLedger ledger;
    private final PolicyPilotProperties properties;
    private final ChatModel chat;
    private final MeterRegistry meters;

    public ScriptedAnswerCache(ProposalCache cache, ModelCallLedger ledger, PolicyPilotProperties properties,
            ChatModel chat, MeterRegistry meters) {
        this.cache = cache;
        this.ledger = ledger;
        this.properties = properties;
        this.chat = chat;
        this.meters = meters;
    }

    @Override
    public Optional<CachedAnswer> find(PromptSpec spec) {
        return cache.findStored(keyOf(spec)).map(ScriptedAnswerCache::answerOf);
    }

    @Override
    public void keep(PromptSpec spec, CachedAnswer answer) {
        ObjectNode stored = JSON.createObjectNode().put("text", answer.text());
        ArrayNode steps = stored.putArray("steps");
        answer.steps().forEach(step -> steps.addObject()
                .put("tool", step.tool())
                .put("arguments", step.arguments())
                .put("result", step.result()));
        cache.putIfAbsent(keyOf(spec), spec.promptName(), stored);
    }

    @Override
    public void served(PromptSpec spec) {
        ledger.record(spec, SpringAiLlmGateway.modelFor(spec.role(), properties),
                SpringAiLlmGateway.providerOf(chat), TokenUsage.NONE, Duration.ZERO, ModelCallLedger.Results.VALID,
                true);
        meters.counter("policypilot.ai.cache.hit", "prompt", spec.promptName()).increment();
    }

    private String keyOf(PromptSpec spec) {
        return ProposalCache.keyOf(spec, SpringAiLlmGateway.modelFor(spec.role(), properties));
    }

    private static CachedAnswer answerOf(JsonNode stored) {
        List<CachedAnswer.ToolStep> steps = new ArrayList<>();
        stored.path("steps").forEach(step -> steps.add(new CachedAnswer.ToolStep(step.required("tool").asString(),
                step.required("arguments").asString(), step.required("result").asString())));
        return new CachedAnswer(stored.required("text").asString(), steps);
    }
}
