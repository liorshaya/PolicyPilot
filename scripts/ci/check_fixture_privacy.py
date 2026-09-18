#!/usr/bin/env python3
"""Fixture privacy check, CI stage 1 (Document 6, Fixtures and Test Data).

Rejects any fixture that contains an email address, a phone number or a nine-digit number that could read as
an identity number. Everything under fixtures/ is synthetic by construction; this check keeps it that way.

Run: python3 scripts/ci/check_fixture_privacy.py   (exit 1 with every hit listed)
"""
import re
import sys
from pathlib import Path

FIXTURES = Path(__file__).resolve().parents[2] / "fixtures"
TEXT_SUFFIXES = {".json", ".md", ".py", ".txt", ".yml", ".yaml", ".csv"}

PATTERNS = {
    "email address": re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"),
    "phone number": re.compile(r"(?<![\w.])(?:\+972[-\s]?|0)(?:[23489]|5\d|7\d)[-\s]?\d{3}[-\s]?\d{4}(?![\w.])"),
    "nine-digit number": re.compile(r"(?<![\d.])\d{9}(?![\d.])"),
}


def main() -> int:
    if not FIXTURES.is_dir():
        print(f"fixtures directory not found: {FIXTURES}")
        return 1
    hits = []
    scanned = 0
    for path in sorted(FIXTURES.rglob("*")):
        if not path.is_file() or path.suffix not in TEXT_SUFFIXES:
            continue
        scanned += 1
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            for kind, pattern in PATTERNS.items():
                for match in pattern.finditer(line):
                    hits.append((path.relative_to(FIXTURES.parent), number, kind, match.group(0)))
    for rel, number, kind, text in hits:
        print(f"{rel}:{number}: {kind}: {text}")
    if hits:
        print(f"FAIL: {len(hits)} possible personal data value(s) in {scanned} fixture file(s)")
        return 1
    print(f"fixture privacy OK ({scanned} files scanned)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
