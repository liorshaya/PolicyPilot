#!/usr/bin/env python3
"""Invisible character check, CI stage 1 (Document 5, Supply Chain and Build Security: source integrity; Document 6,
CI Pipeline).

No tracked file may carry an invisible or bidi control character: Unicode category Cc (the controls) or Cf (the format
characters: bidi overrides and isolates, zero-width characters, the byte order mark), newline and tab aside. Such a
character makes the code a reviewer reads differ from the code that runs (Trojan Source, CVE-2021-42574), so a test
that needs one writes it as an escape or builds it from its code point.

Every file in the index is read, so a staged file is checked before it is committed; a file that is not UTF-8 text is
binary and skipped. Line endings are read as they are stored: a carriage return that ends a line is a CRLF line ending
(the Maven wrapper's Windows script needs them), and one anywhere else is a control character like any other.

Run: python3 scripts/ci/check_invisible_characters.py   (exit 1 with every hit listed)
"""
import subprocess
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ALLOWED = {"\n", "\t"}
INVISIBLE = {"Cc", "Cf"}


def tracked() -> list[str]:
    """The paths in the index, staged files included."""
    listing = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True).stdout
    return [name for name in listing.decode("utf-8").split("\0") if name]


def hits(text: str) -> list[tuple[int, int, str]]:
    """Every invisible character of a text as (line, column, character), both counted from 1."""
    found = []
    lines = text.split("\n")
    for line_no, line in enumerate(lines, 1):
        if line_no < len(lines) and line.endswith("\r"):
            line = line[:-1]  # the carriage return of a CRLF line ending
        for column, char in enumerate(line, 1):
            if char not in ALLOWED and unicodedata.category(char) in INVISIBLE:
                found.append((line_no, column, char))
    return found


def main() -> int:
    names = tracked()
    problems = 0
    for name in names:
        path = ROOT / name
        if not path.is_file():  # deleted in the working tree but not yet in the index
            continue
        try:
            text = path.read_bytes().decode("utf-8")
        except UnicodeDecodeError:
            continue
        for line, column, char in hits(text):
            print(f"{name}:{line}:{column}: U+{ord(char):04X} {unicodedata.name(char, 'unnamed control')}")
            problems += 1
    if problems:
        print(f"{problems} invisible character(s): write each as an escape or build it from its code point")
        return 1
    print(f"no invisible characters in {len(names)} tracked files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
