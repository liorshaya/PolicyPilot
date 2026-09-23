package com.liorshaya.policypilot.support;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The committed {@code fixtures/} tree, the same files the Python reference runs (Document 6, Fixtures and
 * builders). Tests run from {@code backend/}, so the tree is its sibling directory.
 */
public final class Fixtures {

    private static final Path ROOT = Path.of("..", "fixtures");
    private static final RuleSetMapper MAPPER = new RuleSetMapper();

    private Fixtures() {}

    /** A fixture file, by its path relative to the fixtures root. */
    public static Path path(String relative) {
        Path path = ROOT.resolve(relative);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("fixture not found: " + path.toAbsolutePath().normalize());
        }
        return path;
    }

    /** A fixture JSON file, parsed with the production limits and exact decimals. */
    public static JsonNode json(String relative) {
        return MAPPER.readTree(read(path(relative)));
    }

    /** A fresh, mutable copy of the published lending rule set, version 1. */
    public static ObjectNode lendingV1() {
        return (ObjectNode) json("policies/consumer-lending/ruleset.v1.json");
    }

    /** The lending policy as it is written, for a test that pastes it the way the web app does. */
    public static String lendingPolicyText() {
        return text("policies/consumer-lending/policy.he.md");
    }

    /** A fixture text file as it is written, by its path relative to the fixtures root. */
    public static String text(String relative) {
        return read(path(relative));
    }

    /** The nine paragraphs of the lending policy, split on blank lines as the reference splits them. */
    public static List<String> lendingParagraphs() {
        return paragraphs("policies/consumer-lending/policy.he.md");
    }

    /** A policy text file split into paragraphs: blocks separated by a blank line, trimmed, empty ones dropped. */
    public static List<String> paragraphs(String relative) {
        return Arrays.stream(read(path(relative)).split("\n\n")).map(String::strip).filter(p -> !p.isEmpty()).toList();
    }

    /** The conformance fixtures C-01 to C-31, in name order. */
    public static List<Path> conformanceCases() {
        return list("conformance", "C-");
    }

    /** The validator fixtures, one {@code invalid-<CODE>.json} per code, in name order. */
    public static List<Path> invalidRuleSets() {
        return list("conformance", "invalid-");
    }

    /** The slugs of the labeled evaluation policies under {@code eval/policies/}, in name order. */
    public static List<String> evaluationPolicies() {
        try (Stream<Path> directories = Files.list(ROOT.resolve("eval/policies"))) {
            return directories.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The policy text of an evaluation policy, {@code policy.<lang>.md}, relative to the fixtures root. */
    public static String evaluationPolicyText(String slug) {
        return list("eval/policies/" + slug, "policy.").stream()
                .map(p -> "eval/policies/" + slug + "/" + p.getFileName())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no policy text for " + slug));
    }

    /** The rule set of a conformance or invalid fixture: inline, or a path relative to the fixtures root. */
    public static JsonNode ruleSetOf(JsonNode fixture) {
        JsonNode ruleSet = fixture.get("ruleset");
        return ruleSet.isString() ? json(ruleSet.stringValue()) : ruleSet;
    }

    /** The JSON of a fixture file given by its path. */
    public static JsonNode json(Path path) {
        return MAPPER.readTree(read(path));
    }

    private static List<Path> list(String directory, String prefix) {
        try (Stream<Path> files = Files.list(ROOT.resolve(directory))) {
            return files.filter(p -> p.getFileName().toString().startsWith(prefix)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
