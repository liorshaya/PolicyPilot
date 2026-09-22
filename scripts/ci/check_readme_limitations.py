#!/usr/bin/env python3
"""README known limitations check, CI stage 1 (Document 7, Scope ladder: "each cut is written into the README's known
limitations the same day"; Document 6, CI stages).

Keeps the "Known limitations" section of README.md and docs/progress-checklist.md in step, both ways:

- every struck-through box names its cut after it, "~~...~~ (rung 9)", and the README must name that cut;
- every box a restored plan brings back carries "(restored: rung 9)"; while it is open, the README must still name the
  cut, because the feature has not shipped;
- the README may name a cut only while a struck box or an open restored box still carries it, so the limitation leaves
  the README in the pull request that ticks the last box of its feature.

A cut is named as a scope-ladder rung, "rung N", or by the words the checklist uses for the cuts that are not rungs.
A struck box that was moved, not cut, asks for nothing; a reason this check does not know fails it, so a new kind of
cut cannot slip past the README.

Run: python3 scripts/ci/check_readme_limitations.py   (exit 1 with every mismatch listed)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHECKLIST = ROOT / "docs" / "progress-checklist.md"
README = ROOT / "README.md"

BOX = re.compile(r"^\s*- \[(?P<ticked>[ x])\] ")
STRUCK = re.compile(r"~~.+?~~\s*\((?P<reason>[^)]*)\)")
RESTORED = re.compile(r"\(restored: (?P<reason>[^)]*)\)")
RUNGS = re.compile(r"\brungs? (?P<first>\d+)(?: and (?P<second>\d+))?")
README_RUNGS = re.compile(r"\brung (?P<number>\d+)\b")
# The cuts that are not rungs, by the words the checklist writes, and the words the README must use for each.
NAMED = {
    "reset": "reset",
    "provider badge": "provider badge",
    "evaluation runner": "evaluation runner",
    "gate G2": "gate G2",
    "not in the two-week version": "gate G2",
}
# Reasons that name no cut: the work was moved, not dropped.
NOT_CUTS = ("folded into day 10", "not scheduled as a pass", "moved to day")


def cuts_of(reason: str) -> set[str]:
    """What the README must name for one reason; empty for a box that was moved, not cut."""
    wanted = set()
    for rung in RUNGS.finditer(reason):
        wanted.update(f"rung {n}" for n in (rung["first"], rung["second"]) if n)
    for words, named in NAMED.items():
        if words in reason:
            wanted.add(named)
    if not wanted and not reason.startswith(NOT_CUTS):
        raise ValueError(f"a box with a reason this check does not know: ({reason})")
    return wanted


def carried_cuts(checklist: str) -> set[str]:
    """The cuts the README must name: those of struck boxes and of restored boxes not yet ticked."""
    carried = set()
    for line in checklist.splitlines():
        box = BOX.match(line)
        if box is None:
            continue
        for struck in STRUCK.finditer(line):
            carried |= cuts_of(struck["reason"])
        for restored in RESTORED.finditer(line):
            if box["ticked"] == " ":
                carried |= cuts_of(restored["reason"])
            else:
                cuts_of(restored["reason"])  # a ticked box's reason must still be one this check knows
    return carried


def named_cuts(section: str) -> set[str]:
    """The cuts the README's known limitations name."""
    named = {f"rung {rung['number']}" for rung in README_RUNGS.finditer(section)}
    named.update(words for words in set(NAMED.values()) if re.search(rf"\b{re.escape(words)}\b", section))
    return named


def limitations_section(readme: str) -> str:
    match = re.search(r"^## Known limitations\n(?P<body>.*?)(?=^## |\Z)", readme, re.S | re.M)
    if match is None:
        raise ValueError("README.md has no '## Known limitations' section")
    return match["body"]


def main() -> int:
    try:
        carried = carried_cuts(CHECKLIST.read_text(encoding="utf-8"))
        named = named_cuts(limitations_section(README.read_text(encoding="utf-8")))
    except ValueError as problem:
        print(problem)
        return 1
    for cut in sorted(carried - named):
        print(f"README.md, Known limitations: nothing names '{cut}', which docs/progress-checklist.md still carries")
    for cut in sorted(named - carried):
        print(f"README.md, Known limitations: names '{cut}', but no box of docs/progress-checklist.md carries it")
    if carried != named:
        return 1
    print(f"README known limitations: the same {len(carried)} cuts as the checklist")
    return 0


if __name__ == "__main__":
    sys.exit(main())
