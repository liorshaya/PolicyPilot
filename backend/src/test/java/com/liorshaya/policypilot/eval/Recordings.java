package com.liorshaya.policypilot.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What a live pass left behind (Document 6, Recordings): every model call saved under
 * {@code fixtures/eval/recordings/<provider>/<prompt>/<version>/} with its rendered prompts and the raw response.
 * The runner reads them by what they were asked about rather than by their hash, because a report is written per
 * policy and a hash says nothing about which policy it belongs to.
 */
record Recordings(List<JsonNode> calls) {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Where a live pass writes, relative to {@code backend/}. */
    static final Path ROOT = Path.of("..", "fixtures", "eval", "recordings");

    /** Every call of one prompt version, in file order, or none when that pass has not been run. */
    static Recordings of(String provider, String prompt, String version) {
        Path directory = ROOT.resolve(provider).resolve(prompt).resolve(version);
        if (!Files.isDirectory(directory)) {
            return new Recordings(List.of());
        }
        List<JsonNode> calls = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                calls.add(JSON.readTree(Files.readString(file, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Recordings(calls);
    }

    /**
     * The calls whose rendered prompt carries every one of these lines. A policy is named by all of its
     * paragraphs and not by one of them, because the four lending variants open with the same sentence and a
     * single line would score one policy's runs against another's label.
     */
    Recordings about(List<String> lines) {
        return new Recordings(calls.stream()
                .filter(call -> {
                    String rendered = call.path("request").path("user").asString("");
                    return lines.stream().allMatch(rendered::contains);
                })
                .toList());
    }

    /** The runs of one call, as {@code .run1.json} to {@code .run10.json} name them, oldest first. */
    List<JsonNode> responses() {
        return calls.stream().map(call -> parse(call.required("response"))).toList();
    }

    /** The rendered user prompt of each call, in the order of {@link #responses()}. */
    List<String> prompts() {
        return calls.stream().map(call -> call.path("request").path("user").asString("")).toList();
    }

    boolean isEmpty() {
        return calls.isEmpty();
    }

    int size() {
        return calls.size();
    }

    /** The model that answered, as the recording names it; every call of one pass names the same one. */
    String model() {
        return calls.isEmpty() ? "" : calls.getFirst().path("request").path("model").asString("");
    }

    private static JsonNode parse(JsonNode response) {
        return response.isString() ? JSON.readTree(response.asString()) : response;
    }
}
