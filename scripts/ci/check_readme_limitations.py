#!/usr/bin/env python3
"""README known limitations check, CI stage 1 (Document 7, Scope ladder: "each cut is written into the README's known
limitations the same day"; Document 6, CI stages).

Reads every struck-through box of docs/progress-checklist.md and the reason written after it, and requires the
"Known limitations" section of README.md to name each cut: a scope-ladder rung as "rung N", and the cuts that are not
rungs by the words the checklist uses for them. A box folded into day 10 is work, not a cut, and asks for nothing; a
reason this check does not know fails it, so a new kind of cut cannot slip past the README.

Run: python3 scripts/ci/check_readme_limitations.py   (exit 1 with every missing cut listed)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHECKLIST = ROOT / "docs" / "progress-checklist.md"
README = ROOT / "README.md"

STRUCK = re.compile(r"~~.+?~~\s*\((?P<reason>[^)]*)\)")
RUNGS = re.compile(r"\brungs? (?P<first>\d+)(?: and (?P<second>\d+))?")
# The cuts that are not rungs, by the words the checklist writes, and the words the README must use for each.
NAMED = {
    "reset": "reset",
    "provider badge": "provider badge",
    "evaluation runner": "evaluation runner",
    "not in the two-week version": "gate G2",
}
# Reasons that name no cut: the work was moved, not dropped.
NOT_CUTS = ("folded into day 10", "not scheduled as a pass")


def cuts_of(reason: str) -> set[str]:
    """What the README must name for one reason; empty for a box that was moved, not cut."""
    wanted = set()
    for rung in RUNGS.finditer(reason):
        wanted.update(f"rung {n}" for n in (rung["first"], rung["second"]) if n)
    for words, named in NAMED.items():
        if words in reason:
            wanted.add(named)
    if not wanted and not reason.startswith(NOT_CUTS):
        raise ValueError(f"a struck-through box with a reason this check does not know: ({reason})")
    return wanted


def limitations_section(readme: str) -> str:
    match = re.search(r"^## Known limitations\n(?P<body>.*?)(?=^## |\Z)", readme, re.S | re.M)
    if match is None:
        raise ValueError("README.md has no '## Known limitations' section")
    return match["body"]


def main() -> int:
    try:
        wanted = set()
        for line in CHECKLIST.read_text(encoding="utf-8").splitlines():
            for box in STRUCK.finditer(line):
                wanted |= cuts_of(box["reason"])
        section = limitations_section(README.read_text(encoding="utf-8"))
    except ValueError as problem:
        print(problem)
        return 1
    missing = sorted(cut for cut in wanted if not re.search(rf"\b{re.escape(cut)}\b", section))
    for cut in missing:
        print(f"README.md, Known limitations: nothing names '{cut}', which docs/progress-checklist.md cuts")
    if missing:
        return 1
    print(f"README known limitations: all {len(wanted)} cuts named")
    return 0


if __name__ == "__main__":
    sys.exit(main())
