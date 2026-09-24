package com.liorshaya.policypilot.demo.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A policy the seed loads as protected rows, as the image carries it: its text and its rule set, and for the lending
 * policy its 200 cases, copied byte for byte from {@code fixtures/} into the backend resources (the image is built from
 * {@code backend/}; CI stage 1 compares the copies, Document 6, Versioning). The domain, title and language are the
 * rule set's.
 *
 * @param casesJson the case fixture set, or null for a domain seeded without cases
 */
public record DemoFixture(String domain, String title, String language, String text, String ruleSetJson,
        @Nullable String casesJson) {

    /** The demo policy: {@code fixtures/policies/consumer-lending/}. */
    public static DemoFixture lending() {
        return load("fixtures/consumer-lending/", "ruleset.v1.json", "cases-200.json");
    }

    /**
     * The second domain (Document 2, Second domain; decided 2026-09-27, day 15): the municipal tax discount policy,
     * the labeled {@code fixtures/eval/policies/arnona-discount-seniors/}, seeded without cases.
     */
    public static DemoFixture secondDomain() {
        return load("fixtures/arnona-discount-seniors/", "expected.ruleset.json", null);
    }

    /** Every seeded policy, in the order the seed loads them: the demo's first. */
    public static List<DemoFixture> seeded() {
        return List.of(lending(), secondDomain());
    }

    private static DemoFixture load(String directory, String ruleSet, @Nullable String cases) {
        String ruleSetJson = read(directory + ruleSet);
        JsonNode document = JsonMapper.builder().build().readTree(ruleSetJson);
        return new DemoFixture(document.required("id").stringValue(), document.required("name").stringValue(),
                document.required("language").stringValue(), read(directory + "policy.he.md"), ruleSetJson,
                cases == null ? null : read(directory + cases));
    }

    private static String read(String resource) {
        try (InputStream in = DemoFixture.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
