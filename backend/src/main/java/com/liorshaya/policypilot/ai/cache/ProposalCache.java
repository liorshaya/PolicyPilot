package com.liorshaya.policypilot.ai.cache;

import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.entity.CachedResponseEntity;
import com.liorshaya.policypilot.ai.repository.CachedResponseRepository;
import com.liorshaya.policypilot.common.Hashes;
import java.time.Clock;
import java.util.Optional;
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

    @Transactional(readOnly = true)
    public Optional<String> find(String key) {
        return responses.findById(key).map(CachedResponseEntity::response);
    }

    @Transactional
    public void put(String key, String promptName, String responseJson) {
        responses.save(new CachedResponseEntity(key, promptName, responseJson, clock.instant()));
    }
}
