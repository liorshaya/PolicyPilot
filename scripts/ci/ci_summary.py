#!/usr/bin/env python3
"""CI stage 8 job summary (Document 6, Metrics and Reporting; Document 7, Daily routine and tracking).

Reads the artifacts the earlier stages uploaded and prints Markdown: line and branch coverage per package
(JaCoCo), the PIT mutation score, test counts (Surefire and Failsafe), Vitest coverage, and the performance numbers
the worklog copies. Each timing test prints what it measured as one line, "performance: <name> <n> ms", which its
report keeps; they are shown beside Document 6's target and the specification of the runner that measured them
(runner.txt, written by stage 5), so a regression shows as a trend rather than as a failed threshold on a slow runner
(Document 6, Performance and Determinism Tests).

Run:
  python3 scripts/ci/ci_summary.py <reports-dir>
  python3 scripts/ci/ci_summary.py --self-test
"""
import json
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT_PACKAGE = "com/liorshaya/policypilot"

# What each timing test prints, the row it is shown as, and Document 6's target for it
PERFORMANCE = (
    ("200-case batch median", "Batch of 200 cases, with persistence (median of 3)", "under 1 s"),
    ("single decision median", "Single decision through the API (median of 20)", "under 50 ms"),
    ("chat first token", "Chat first token, recorded model answering after 100 ms", "under 500 ms"),
    ("cached chat first token", "Chat first token, a scripted answer served from the cache", "under 500 ms"),
)
MEASURED = re.compile(r"performance: (.+?) (\d+) ms")


def ratio(covered: int, missed: int) -> str:
    total = covered + missed
    return "n/a" if total == 0 else f"{100.0 * covered / total:.1f}%"


def jacoco_rows(report: Path):
    tree = ET.parse(report)
    rows = []
    for package in tree.getroot().iter("package"):
        name = package.get("name", "")
        if not name.startswith(ROOT_PACKAGE):
            continue
        counters = {c.get("type"): (int(c.get("covered", 0)), int(c.get("missed", 0))) for c in package.findall("counter")}
        line = counters.get("LINE", (0, 0))
        branch = counters.get("BRANCH", (0, 0))
        if line == (0, 0):
            continue
        rows.append((name[len(ROOT_PACKAGE) + 1:].replace("/", ".") or "(root)", ratio(*line), ratio(*branch)))
    return sorted(rows)


def pit_score(report: Path) -> str:
    mutations = list(ET.parse(report).getroot().iter("mutation"))
    if not mutations:
        return "n/a (no mutations yet)"
    detected = sum(1 for m in mutations if m.get("detected") == "true")
    return f"{100.0 * detected / len(mutations):.1f}% ({detected} of {len(mutations)} mutants killed)"


def test_counts(files):
    tests = failures = errors = skipped = 0
    for file in files:
        try:
            suite = ET.parse(file).getroot()
        except ET.ParseError:
            continue
        tests += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0))
        errors += int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
    return tests, failures, errors, skipped


def vitest_summary(summary: Path) -> str:
    total = json.loads(summary.read_text(encoding="utf-8")).get("total", {})
    statements = total.get("statements", {}).get("pct", "n/a")
    branches = total.get("branches", {}).get("pct", "n/a")
    return f"statements {statements}%, branches {branches}%"


def measured(reports: Path) -> dict:
    """{name: milliseconds} of every performance line a test report kept (its system-out, or the output file)."""
    found = {}
    for report in sorted(reports.rglob("*-reports/*")):
        if report.suffix not in (".xml", ".txt"):
            continue
        for name, millis in MEASURED.findall(report.read_text(encoding="utf-8", errors="replace")):
            found[name] = int(millis)
    return found


def runner(reports: Path) -> str:
    """The sentence naming the runner the timings were measured on, as stage 5 wrote it down."""
    written = sorted(reports.rglob("runner.txt"))
    if not written:
        return "The runner that measured them is not recorded (no runner.txt)."
    facts = dict(line.split(": ", 1) for line in written[0].read_text(encoding="utf-8").splitlines() if ": " in line)
    return "Measured on " + ", ".join(f"{key} {value}" for key, value in facts.items()) + "."


def performance_lines(reports: Path) -> list:
    numbers = measured(reports)
    lines = ["### Performance numbers for the worklog", "", runner(reports), "",
             "| Metric | Measured | Target (Document 6) |", "| --- | --- | --- |"]
    for name, row, target in PERFORMANCE:
        value = f"{numbers[name]} ms" if name in numbers else "not measured in this run"
        lines.append(f"| {row} | {value} | {target} |")
    return lines + [""]


def self_test() -> int:
    """The performance section over a small tree of reports: every number found, one missing, the runner named."""
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        failsafe = root / "reports-integration/failsafe-reports"
        failsafe.mkdir(parents=True)
        (failsafe / "TEST-a.DecisionPerformanceIT.xml").write_text(
            '<testsuite tests="2"><testcase name="batch"><system-out><![CDATA[x\nperformance: 200-case batch median '
            '87 ms\n]]></system-out></testcase><testcase name="single"><system-out><![CDATA[performance: single '
            'decision median 12 ms]]></system-out></testcase></testsuite>', encoding="utf-8")
        (failsafe / "a.ChatStreamIT-output.txt").write_text("performance: chat first token 143 ms\n",
                                                             encoding="utf-8")
        (root / "reports-integration/runner.txt").write_text("cpu: AMD EPYC 7763\ncores: 4\nmemory: 15 GiB\n",
                                                               encoding="utf-8")
        text = "\n".join(performance_lines(root))
        expected = [
            "Measured on cpu AMD EPYC 7763, cores 4, memory 15 GiB.",
            "| Batch of 200 cases, with persistence (median of 3) | 87 ms | under 1 s |",
            "| Single decision through the API (median of 20) | 12 ms | under 50 ms |",
            "| Chat first token, recorded model answering after 100 ms | 143 ms | under 500 ms |",
            "| Chat first token, a scripted answer served from the cache | not measured in this run | under 500 ms |",
        ]
        failures = [line for line in expected if line not in text.split("\n")]
        for failure in failures:
            print("SELF-TEST FAILED, missing:", failure)
        if not failures:
            print("ci summary self-test: ALL OK")
        return 1 if failures else 0


def main() -> int:
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    reports = Path(sys.argv[1] if len(sys.argv) > 1 else "reports")
    out = ["## CI summary", ""]

    jacoco = sorted(reports.rglob("jacoco.xml"), key=lambda p: ("integration" not in str(p), str(p)))
    if jacoco:
        source = "merged unit and integration" if "integration" in str(jacoco[0]) else "unit only"
        out += [f"### Backend coverage per package ({source})", "", "| Package | Line | Branch |", "| --- | --- | --- |"]
        rows = jacoco_rows(jacoco[0])
        out += [f"| {name} | {line} | {branch} |" for name, line, branch in rows] or ["| (no classes yet) | n/a | n/a |"]
    else:
        out += ["### Backend coverage per package", "", "n/a (no JaCoCo report)"]
    out.append("")

    pit = list(reports.rglob("mutations.xml"))
    out += ["### Mutation score (engine and rules)", "", pit_score(pit[0]) if pit else "n/a (no PIT report)", ""]

    unit = test_counts(reports.rglob("surefire-reports/TEST-*.xml"))
    integration = test_counts(reports.rglob("failsafe-reports/TEST-*.xml"))
    out += ["### Test counts", "", "| Level | Tests | Failures | Errors | Skipped |", "| --- | --- | --- | --- | --- |",
            f"| Unit, architecture, conformance | {unit[0]} | {unit[1]} | {unit[2]} | {unit[3]} |",
            f"| Integration and contract | {integration[0]} | {integration[1]} | {integration[2]} | {integration[3]} |", ""]

    vitest = list(reports.rglob("coverage-summary.json"))
    out += ["### Web app coverage", "", vitest_summary(vitest[0]) if vitest else "n/a (no Vitest summary)", ""]

    out += performance_lines(reports)
    print("\n".join(out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
