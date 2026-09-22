package com.liorshaya.policypilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The report Document 4 asks the runner to write: "one Markdown report with one column per provider", the metric
 * table with its targets, and, from Document 6, "a {@code PASS}/{@code FAIL} line per target". The expected values
 * are those two sentences and Document 4's own metric table, which {@link Metric#table()} carries row for row.
 */
class EvalReportTest {

    private static final Map<String, String> VERSIONS = Map.of("author", "v1");

    // Document 4: "one Markdown report with one column per provider". Expected: both providers named in the header
    // of every report, whether or not either was run
    @Test
    void theReportHasAColumnPerProvider() {
        String markdown = new EvalReport(LocalDate.of(2026, 9, 23), VERSIONS).markdown();

        String header = markdown.lines().filter(line -> line.startsWith("| Metric |")).findFirst().orElseThrow();
        assertThat(header).contains("openai").contains("ollama");
    }

    // Document 4: the Ollama column "exists to show the local path works and to quantify the gap". Expected: a
    // provider that was not run reads "not run", never 0.00, which would read as a column that failed
    @Test
    void aProviderWithNoRunIsReportedAsNotRunRatherThanZero() {
        EvalReport report = new EvalReport(LocalDate.of(2026, 9, 23), VERSIONS)
                .score("openai", "Rule recall", Metric.Score.of(9, 10));

        String recall = rowOf(report.markdown(), "Rule recall");

        assertThat(recall).contains("0.90 (9 of 10)").contains("not run");
        assertThat(recall).doesNotContain("0.00");
    }

    // Document 6: "its report has a PASS/FAIL line per target". Expected: the strong model's column decides it,
    // against Document 4's own target of 0.90 for rule recall
    @Test
    void everyTargetGetsAPassOrFailFromTheStrongModelsColumn() {
        EvalReport report = new EvalReport(LocalDate.of(2026, 9, 23), VERSIONS)
                .score("openai", "Rule recall", Metric.Score.of(9, 10))
                .score("openai", "Rule precision", Metric.Score.of(8, 10));

        String markdown = report.markdown();

        assertThat(rowOf(markdown, "Rule recall")).endsWith("PASS |");
        assertThat(rowOf(markdown, "Rule precision")).endsWith("FAIL |");
    }

    // Document 4: the Ollama column is "reported with the same metrics and no targets". Expected: a score far
    // below every target never turns the verdict of a run that has no openai column into a pass or a fail
    @Test
    void theOllamaColumnNeverDecidesAVerdict() {
        EvalReport report = new EvalReport(LocalDate.of(2026, 9, 23), VERSIONS)
                .score("ollama", "Rule recall", Metric.Score.of(1, 10));

        assertThat(rowOf(report.markdown(), "Rule recall")).contains("0.10 (1 of 10)").endsWith("not run |");
    }

    // Document 4's table has thirteen metrics. Expected: the report carries every one, named as the document
    // names it, so a metric cannot go missing without the table changing
    @Test
    void everyMetricOfDocumentFourHasItsRow() {
        String markdown = new EvalReport(LocalDate.of(2026, 9, 23), VERSIONS).markdown();

        List<String> named = Metric.table().stream().map(Metric::name).toList();
        assertThat(named).hasSize(13);
        named.forEach(metric -> assertThat(markdown).contains("| " + metric + " |"));
    }

    // Document 4, Runner: "writes docs/eval/<date>-<prompt-versions>.md". Expected: that name exactly
    @Test
    void theReportIsNamedByItsDateAndPromptVersions() {
        EvalReport report = new EvalReport(LocalDate.of(2026, 9, 23),
                new java.util.LinkedHashMap<>(Map.of("author", "v1")));

        assertThat(report.fileName().getFileName().toString()).isEqualTo("2026-09-23-authorv1.md");
        assertThat(report.fileName().getParent()).isEqualTo(EvalReport.DIRECTORY);
    }

    // Document 4, Runner: the report carries "per-policy rows, and the list of mismatches with diffs". Expected:
    // both sections present when there is anything to put in them, and absent when there is not
    @Test
    void perPolicyRowsAndMismatchesAppearWhenThereAreAny() {
        assertThat(new EvalReport(LocalDate.of(2026, 9, 23), VERSIONS).markdown())
                .doesNotContain("## Per policy").doesNotContain("## Mismatches");

        String markdown = new EvalReport(LocalDate.of(2026, 9, 23), VERSIONS)
                .policyRow("| consumer-lending | 19 | 14 | 0.78 | 0.74 | 1.00 | 0.95 |")
                .mismatch("R-010: the set value differs on 21 of 21 cases")
                .markdown();

        assertThat(markdown).contains("## Per policy").contains("| consumer-lending | 19 | 14 |")
                .contains("## Mismatches").contains("R-010: the set value differs on 21 of 21 cases");
    }

    private static String rowOf(String markdown, String metric) {
        return markdown.lines().filter(line -> line.startsWith("| " + metric + " |")).findFirst().orElseThrow();
    }
}
