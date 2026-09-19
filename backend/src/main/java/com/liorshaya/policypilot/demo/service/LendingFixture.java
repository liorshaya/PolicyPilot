package com.liorshaya.policypilot.demo.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The demo policy as the image carries it: {@code fixtures/policies/consumer-lending/policy.he.md} and
 * {@code ruleset.v1.json}, copied byte for byte into the backend resources (the image is built from {@code backend/};
 * CI stage 1 compares the copies, Document 6, Versioning). The title and language are the rule set's.
 */
public record LendingFixture(String title, String language, String text) {

    static final String POLICY = "fixtures/consumer-lending/policy.he.md";
    static final String RULESET = "fixtures/consumer-lending/ruleset.v1.json";

    public static LendingFixture load() {
        JsonNode ruleset = JsonMapper.builder().build().readTree(read(RULESET));
        return new LendingFixture(ruleset.required("name").stringValue(), ruleset.required("language").stringValue(),
                read(POLICY));
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
