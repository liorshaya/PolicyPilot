#!/usr/bin/env python3
"""CI stage 8 job summary (Document 6, Metrics and Reporting; Document 7, Daily routine and tracking).

Reads the artifacts the earlier stages uploaded and prints Markdown: line and branch coverage per package
(JaCoCo), the PIT mutation score, test counts (Surefire and Failsafe), Vitest coverage, and the performance
numbers the worklog copies (batch time, first-token time), which stay "n/a" until their tests exist.

Run: python3 scripts/ci/ci_summary.py <reports-dir>
"""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT_PACKAGE = "com/liorshaya/policypilot"


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


def main() -> int:
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

    out += ["### Performance numbers for the worklog", "", "| Metric | Value |", "| --- | --- |",
            "| Batch of 200 cases | n/a (test arrives on day 5) |",
            "| Single decision | n/a (test arrives on day 5) |",
            "| Chat first token | n/a (test arrives on day 9) |", ""]
    print("\n".join(out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
