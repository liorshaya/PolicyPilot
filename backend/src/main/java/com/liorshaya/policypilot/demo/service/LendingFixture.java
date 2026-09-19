package com.liorshaya.policypilot.demo.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The demo policy and its rule set as the image carries them: {@code fixtures/policies/consumer-lending/policy.he.md} and
 * {@code ruleset.v1.json} and {@code cases-200.json}, copied byte for byte into the backend resources (the image is built from {@code backend/};
 * CI stage 1 compares the copies, Document 6, Versioning). The title and language are the rule set's.
 */
public record LendingFixture(String title, String language, String text, String ruleSetJson,
        String casesJson) {

    static final String POLICY = "fixtures/consumer-lending/policy.he.md";
    static final String RULESET = "fixtures/consumer-lending/ruleset.v1.json";
    static final String CASES = "fixtures/consumer-lending/cases-200.json";

    public static LendingFixture load() {
        String ruleSetJson = read(RULESET);
        JsonNode ruleSet = JsonMapper.builder().build().readTree(ruleSetJson);
        return new LendingFixture(ruleSet.required("name").stringValue(), ruleSet.required("language").stringValue(),
                read(POLICY), ruleSetJson, read(CASES));
    }

    private static String read(String resource) {
        try (InputStream in = LendingFixture.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
