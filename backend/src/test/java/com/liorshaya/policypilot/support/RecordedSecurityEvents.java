package com.liorshaya.policypilot.support;

import com.liorshaya.policypilot.common.SecurityEvents;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * The security events a service raised, for a test that checks the service raises them (Document 5, Security logging).
 * The events themselves still count and log as they do in the API; this only keeps the model-output events a test asks
 * about as one line each, in the order they came. What each event logs is {@code SecurityEventsTest}'s.
 */
public final class RecordedSecurityEvents extends SecurityEvents {

    private final List<String> raised = new ArrayList<>();

    public RecordedSecurityEvents() {
        super(new SimpleMeterRegistry(), "salt".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void validationFailed(String prompt, String version, int attempt, Collection<String> codes) {
        super.validationFailed(prompt, version, attempt, codes);
        raised.add("ai.validation.failed " + prompt + "/" + version + " attempt=" + attempt + " codes="
                + String.join(",", new TreeSet<>(codes)));
    }

    /** Each event raised, as {@code ai.validation.failed author/v2 attempt=1 codes=DSL_SCHEMA}. */
    public List<String> raised() {
        return List.copyOf(raised);
    }
}
