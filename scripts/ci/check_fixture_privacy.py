#!/usr/bin/env python3
"""Fixture privacy check, CI stage 1 (Document 6, Fixtures and Test Data).

Rejects any fixture that contains an email address, a phone number or a nine-digit number that could read as
an identity number. Everything under fixtures/ is synthetic by construction; this check keeps it that way.

An embedding recording (fixtures/eval/recordings/<provider>/embedding/) is checked by its texts: its vectors are
base64 of float32, whose digit runs are the encoding of numbers, not anything a person wrote, and would otherwise
read as identity numbers by chance. For the same reason a SHA-256 digest (64 hex characters, the input hash of a
model recording) is left out of the line it is on: a digit run inside it is part of a hash, not a number.

Run: python3 scripts/ci/check_fixture_privacy.py   (exit 1 with every hit listed)
"""
import json
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
SHA256_HEX = re.compile(r"(?<![0-9A-Fa-f])[0-9a-f]{64}(?![0-9A-Fa-f])")


def lines_of(path: Path) -> list[str]:
    """The lines to scan: a file's own lines, or the texts of an embedding recording, one per entry."""
    text = path.read_text(encoding="utf-8")
    if path.suffix == ".json" and "embedding" in path.relative_to(FIXTURES).parts[:4]:
        return [entry["text"] for entry in json.loads(text)["embeddings"]]
    return text.splitlines()


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
        for number, line in enumerate(lines_of(path), start=1):
            line = SHA256_HEX.sub("", line)
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
