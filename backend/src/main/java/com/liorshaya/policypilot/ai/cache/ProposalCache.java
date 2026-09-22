package com.liorshaya.policypilot.ai.cache;

import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.entity.CachedResponseEntity;
import com.liorshaya.policypilot.ai.repository.CachedResponseRepository;
import com.liorshaya.policypilot.common.Hashes;
import java.time.Clock;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The response cache that serves the scripted demo steps without a model call (Document 2, Cached demo outputs;
 * Document 4, Version discipline). The key is the hash of what produced the answer — the prompt name and version,
 * the model, and the rendered prompts — so bumping a prompt version misses on purpose and a demo never shows an
 * answer that a newer prompt would not produce.
 */
@Component
public class ProposalCache {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** The answer is stored as one field of a JSON object, because it may be text and the column is jsonb. */
    private static final String FIELD = "text";

    private final CachedResponseRepository responses;
    private final Clock clock;

    public ProposalCache(CachedResponseRepository responses, Clock clock) {
        this.responses = responses;
        this.clock = clock;
    }

    /** The key a call is cached under. */
    public static String keyOf(PromptSpec spec, String model) {
        return Hashes.sha256Hex(String.join(
                "\u001f", spec.promptName(), spec.promptVersion(), model, spec.system(), spec.user()));
    }

    /** What the model answered last time, exactly as it answered it. */
    @Transactional(readOnly = true)
    public Optional<String> find(String key) {
        return responses.findById(key)
                .map(CachedResponseEntity::response)
                .map(stored -> JSON.readTree(stored).path(FIELD).asString());
    }

    @Transactional
    public void put(String key, String promptName, String answer) {
        ObjectNode stored = JSON.createObjectNode();
        stored.put(FIELD, answer);
        responses.save(new CachedResponseEntity(key, promptName, stored.toString(), clock.instant()));
    }

    /** The whole stored object, for a caller that keeps more than the text beside it (the chat's tool calls). */
    @Transactional(readOnly = true)
    public Optional<JsonNode> findStored(String key) {
        return responses.findById(key).map(CachedResponseEntity::response).map(JSON::readTree);
    }

    /**
     * Stores an object that carries the answer's text under {@code text} and whatever its caller keeps beside it,
     * unless something is already stored under the key: the first answer kept stands.
     */
    @Transactional
    public void putIfAbsent(String key, String promptName, ObjectNode stored) {
        if (!stored.path(FIELD).isString()) {
            throw new IllegalArgumentException("a stored answer carries its text under \"" + FIELD + "\"");
        }
        if (!responses.existsById(key)) {
            responses.save(new CachedResponseEntity(key, promptName, stored.toString(), clock.instant()));
        }
    }
}
