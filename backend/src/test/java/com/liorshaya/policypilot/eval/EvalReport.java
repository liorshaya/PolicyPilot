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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Document 4, Runner: "writes {@code docs/eval/<date>-<prompt-versions>.md} with the table above, per-policy rows,
 * and the list of mismatches with diffs"; Document 6: "its report has a {@code PASS}/{@code FAIL} line per target".
 * One column per provider, always both, so a provider that was not run is visibly absent rather than silently
 * missing --- the Ollama column exists "to show the local path works and to quantify the gap".
 *
 * <p>Each provider is scored in a run of its own, because the vector column is sized per profile, so a report is
 * written by one provider's run and completed by the other's: a dated report that already exists keeps the other
 * provider's column, verdicts and section, and the provider of this run replaces its own. The notes, per-policy rows
 * and mismatches of a run are its provider's, in that provider's section.
 */
final class EvalReport {

    /** Where a report is written, relative to {@code backend/}. */
    static final Path DIRECTORY = Path.of("..", "docs", "eval");
    /** The providers the report always has a column for, in this order. */
    static final List<String> PROVIDERS = List.of("openai", "ollama");

    /** A provider's column header, {@code openai (gpt-5.6-terra)}: the provider and, in brackets, its model. */
    private static final Pattern COLUMN = Pattern.compile("(\\S+)(?: \\((.+)\\))?");

    private final LocalDate date;
    private final Map<String, String> promptVersions;
    private final String provider;
    /** What an existing report holds for the other provider: its cells by metric, its section, its verdicts. */
    private final Map<String, Map<String, String>> carriedCells = new LinkedHashMap<>();
    private final Map<String, String> carriedSections = new LinkedHashMap<>();
    private final Map<String, String> carriedVerdicts = new LinkedHashMap<>();
    private final Map<String, Map<String, Metric.Score>> scores = new LinkedHashMap<>();
    private final Map<String, String> models = new LinkedHashMap<>();
    private final List<String> policies = new ArrayList<>();
    private final Map<String, Integer> mismatches = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();

    /** A report of one provider's run; {@code provider} is whose notes, rows and mismatches it collects. */
    EvalReport(LocalDate date, Map<String, String> promptVersions, String provider) {
        this.date = date;
        this.promptVersions = new LinkedHashMap<>(promptVersions);
        this.provider = provider;
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
        PROVIDERS.forEach(column -> out.append(" ").append(labelOf(column)).append(" |"));
        out.append(" Verdict |\n| --- | --- |");
        PROVIDERS.forEach(ignored -> out.append(" --- |"));
        out.append(" --- |\n");
        for (Metric metric : Metric.table()) {
            out.append("| ").append(metric.name()).append(" | ")
                    .append(metric.target() == null ? "reported" : String.format("%.2f", metric.target()))
                    .append(" |");
            for (String column : PROVIDERS) {
                out.append(" ").append(cellOf(column, metric.name())).append(" |");
            }
            out.append(" ").append(verdictOf(metric)).append(" |\n");
        }

        for (String column : PROVIDERS) {
            if (column.equals(provider)) {
                appendSection(out);
            } else if (carriedSections.containsKey(column)) {
                out.append("\n").append(carriedSections.get(column));
            }
        }
        return out.toString();
    }

    /** A provider as its column and its section name it: the provider and, when known, its model. */
    private String labelOf(String column) {
        return models.containsKey(column) ? column + " (" + models.get(column) + ")" : column;
    }

    private String cellOf(String column, String metric) {
        Metric.Score score = scoreOf(column, metric);
        if (score != null) {
            return score.asText();
        }
        return carriedCells.getOrDefault(column, Map.of()).getOrDefault(metric, "not run");
    }

    /** The strong model's column decides; a run of another provider keeps the verdict the report already had. */
    private String verdictOf(Metric metric) {
        String strong = PROVIDERS.getFirst();
        if (!provider.equals(strong) && carriedVerdicts.containsKey(metric.name())) {
            return carriedVerdicts.get(metric.name());
        }
        return metric.verdict(scoreOf(strong, metric.name()));
    }

    private void appendSection(StringBuilder out) {
        if (notes.isEmpty() && policies.isEmpty() && mismatches.isEmpty()) {
            return;
        }
        out.append("\n## ").append(labelOf(provider)).append("\n");
        if (!notes.isEmpty()) {
            out.append("\n");
            notes.forEach(note -> out.append("- ").append(note).append("\n"));
        }
        if (!policies.isEmpty()) {
            out.append("\n### Per policy\n\n| Policy | Rules | Matched | Recall | Precision | Provenance | Cases |\n")
                    .append("| --- | --- | --- | --- | --- | --- | --- |\n");
            policies.forEach(row -> out.append(row).append("\n"));
        }
        if (!mismatches.isEmpty()) {
            out.append("\n### Mismatches\n\n");
            mismatches.forEach((line, runs) -> out.append("- ").append(line)
                    .append(runs > 1 ? " _(" + runs + " runs)_" : "").append("\n"));
        }
    }

    /**
     * Takes from an existing report what belongs to the other provider: its column's cells, its model, its section,
     * and the verdicts, which only the strong model's column decides. This run's own provider is never carried.
     */
    private void carry(String existing) {
        List<String> lines = existing.lines().toList();
        List<String> columns = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("| ")) {
                continue;
            }
            List<String> cells = cellsOf(line);
            if (cells.getFirst().equals("Metric")) {
                columns.clear();
                for (String header : cells.subList(2, 2 + PROVIDERS.size())) {
                    Matcher column = COLUMN.matcher(header);
                    if (column.matches()) {
                        columns.add(column.group(1));
                        if (column.group(2) != null && !column.group(1).equals(provider)) {
                            models.putIfAbsent(column.group(1), column.group(2));
                        }
                    }
                }
            } else if (columns.size() == PROVIDERS.size() && cells.size() == 3 + PROVIDERS.size()) {
                String metric = cells.getFirst();
                for (int i = 0; i < columns.size(); i++) {
                    if (!columns.get(i).equals(provider)) {
                        carriedCells.computeIfAbsent(columns.get(i), ignored -> new LinkedHashMap<>())
                                .put(metric, cells.get(2 + i));
                    }
                }
                carriedVerdicts.put(metric, cells.getLast());
            }
        }
        StringBuilder section = null;
        String owner = null;
        for (String line : lines) {
            if (line.startsWith("## ")) {
                if (owner != null) {
                    carriedSections.put(owner, section.toString());
                }
                Matcher heading = COLUMN.matcher(line.substring(3));
                owner = heading.matches() && PROVIDERS.contains(heading.group(1))
                        && !heading.group(1).equals(provider) ? heading.group(1) : null;
                section = new StringBuilder();
            }
            if (owner != null) {
                section.append(line).append("\n");
            }
        }
        if (owner != null) {
            carriedSections.put(owner, section.toString());
        }
    }

    private static List<String> cellsOf(String row) {
        String inner = row.substring(1, row.lastIndexOf('|'));
        return List.of(inner.split("\\|", -1)).stream().map(String::strip).toList();
    }

    private Metric.@Nullable Score scoreOf(String provider, String metric) {
        return scores.getOrDefault(provider, Map.of()).get(metric);
    }

    /** Writes the report to {@link #DIRECTORY}, completing the one of the same name if another provider began it. */
    Path write() {
        return write(DIRECTORY);
    }

    Path write(Path directory) {
        Path file = directory.resolve(fileName().getFileName());
        try {
            if (Files.exists(file)) {
                carry(Files.readString(file, StandardCharsets.UTF_8));
            }
            Files.createDirectories(directory);
            Files.writeString(file, markdown(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }
}
