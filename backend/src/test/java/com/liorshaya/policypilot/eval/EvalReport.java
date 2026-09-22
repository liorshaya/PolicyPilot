package com.liorshaya.policypilot.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Document 4, Runner: "writes {@code docs/eval/<date>-<prompt-versions>.md} with the table above, per-policy rows,
 * and the list of mismatches with diffs"; Document 6: "its report has a {@code PASS}/{@code FAIL} line per target".
 * One column per provider, always both, so a provider that was not run is visibly absent rather than silently
 * missing --- the Ollama column exists "to show the local path works and to quantify the gap".
 */
final class EvalReport {

    /** Where a report is written, relative to {@code backend/}. */
    static final Path DIRECTORY = Path.of("..", "docs", "eval");
    /** The providers the report always has a column for, in this order. */
    static final List<String> PROVIDERS = List.of("openai", "ollama");

    private final LocalDate date;
    private final Map<String, String> promptVersions;
    private final Map<String, Map<String, Metric.Score>> scores = new LinkedHashMap<>();
    private final Map<String, String> models = new LinkedHashMap<>();
    private final List<String> policies = new ArrayList<>();
    private final Map<String, Integer> mismatches = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();

    EvalReport(LocalDate date, Map<String, String> promptVersions) {
        this.date = date;
        this.promptVersions = new LinkedHashMap<>(promptVersions);
    }

    /** Records one provider's score for one metric; a metric never recorded prints as not run. */
    EvalReport score(String provider, String metric, Metric.Score score) {
        scores.computeIfAbsent(provider, ignored -> new LinkedHashMap<>()).put(metric, score);
        return this;
    }

    /** Names the model a provider's column was produced by, for the header. */
    EvalReport model(String provider, String model) {
        models.put(provider, model);
        return this;
    }

    EvalReport policyRow(String row) {
        policies.add(row);
        return this;
    }

    /** One mismatch; the same one over several runs of a policy is counted, not repeated. */
    EvalReport mismatch(String line) {
        mismatches.merge(line, 1, Integer::sum);
        return this;
    }

    /** A line under the table: a reading Document 4 leaves open, a count that needs saying, a column not run. */
    EvalReport note(String line) {
        notes.add(line);
        return this;
    }

    /** {@code docs/eval/<date>-<prompt-versions>.md}, as Document 4 names it. */
    Path fileName() {
        String versions = String.join("-", promptVersions.entrySet().stream()
                .map(entry -> entry.getKey() + entry.getValue()).toList());
        return DIRECTORY.resolve(date + "-" + versions + ".md");
    }

    String markdown() {
        StringBuilder out = new StringBuilder();
        out.append("# Evaluation run, ").append(date).append("\n\n");
        out.append("Document 4, \"Evaluation Set and Metrics\". Prompt versions: ")
                .append(String.join(", ", promptVersions.entrySet().stream()
                        .map(entry -> "`" + entry.getKey() + "` " + entry.getValue()).toList()))
                .append(". Targets are the strong model's; the Ollama column is reported without targets, to show"
                        + " the local path works and to quantify the gap.\n\n");

        out.append("| Metric | Target |");
        PROVIDERS.forEach(provider -> out.append(" ").append(provider)
                .append(models.containsKey(provider) ? " (" + models.get(provider) + ")" : "").append(" |"));
        out.append(" Verdict |\n| --- | --- |");
        PROVIDERS.forEach(ignored -> out.append(" --- |"));
        out.append(" --- |\n");
        for (Metric metric : Metric.table()) {
            out.append("| ").append(metric.name()).append(" | ")
                    .append(metric.target() == null ? "reported" : String.format("%.2f", metric.target()))
                    .append(" |");
            for (String provider : PROVIDERS) {
                Metric.Score score = scoreOf(provider, metric.name());
                out.append(" ").append(score == null ? "not run" : score.asText()).append(" |");
            }
            out.append(" ").append(metric.verdict(scoreOf(PROVIDERS.getFirst(), metric.name()))).append(" |\n");
        }

        if (!notes.isEmpty()) {
            out.append("\n");
            notes.forEach(note -> out.append("- ").append(note).append("\n"));
        }
        if (!policies.isEmpty()) {
            out.append("\n## Per policy\n\n| Policy | Rules | Matched | Recall | Precision | Provenance | Cases |\n")
                    .append("| --- | --- | --- | --- | --- | --- | --- |\n");
            policies.forEach(row -> out.append(row).append("\n"));
        }
        if (!mismatches.isEmpty()) {
            out.append("\n## Mismatches\n\n");
            mismatches.forEach((line, runs) -> out.append("- ").append(line)
                    .append(runs > 1 ? " _(" + runs + " runs)_" : "").append("\n"));
        }
        return out.toString();
    }

    private Metric.@Nullable Score scoreOf(String provider, String metric) {
        return scores.getOrDefault(provider, Map.of()).get(metric);
    }

    Path write() {
        Path file = fileName();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, markdown(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }
}
