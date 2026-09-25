#!/usr/bin/env python3
"""The README's setup block, run on a fresh clone (Work Plan day 16: "The README's setup commands run on a clean clone
in a fresh container, timed against the 5-minute line"; Brief, Definition of Done line 1).

The block is the first fenced block under "## Setup" in README.md. The compose smoke job makes its first command, the
clone, itself, at the commit under test, and then runs this script inside the clone for the rest of the block, as
written and in order. Where the README asks a person to fill in the values, right after `cp .env.example .env`, the
script writes the values the job generated into their lines of .env: a throwaway access code and cookie secret, and a
placeholder provider key (nothing in the run calls the provider). A value is never printed.

Run:
  python3 scripts/ci/readme_setup.py --values <file>   run the block after the clone; <file> holds NAME=value lines
  python3 scripts/ci/readme_setup.py --self-test
"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLONE = "git clone https://github.com/liorshaya/PolicyPilot.git && cd PolicyPilot"
COPY = "cp .env.example .env"
COMMENT = re.compile(r"\s+#.*$")


def setup_block(readme: str) -> list[str]:
    """The commands of the first fenced block under "## Setup", without their comments."""
    section = readme.split("\n## Setup\n", 1)[1].split("\n## ", 1)[0]
    block = section.split("```", 2)[1].split("\n")[1:]  # the first line is the fence's language
    commands = [COMMENT.sub("", line).strip() for line in block]
    return [command for command in commands if command and not command.startswith("#")]


def read_values(path: Path) -> dict[str, str]:
    """The NAME=value lines of the job's file."""
    pairs = (line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines() if "=" in line)
    return {name.strip(): value.strip() for name, value in pairs}


def fill(env: Path, values: dict[str, str]) -> None:
    """Each NAME= line of .env that a value is given for, with that value; every other line as it was."""
    lines = env.read_text(encoding="utf-8").split("\n")
    missing = set(values)
    for index, line in enumerate(lines):
        name = line.split("=", 1)[0]
        if "=" in line and not line.startswith("#") and name in values:
            lines[index] = f"{name}={values[name]}"
            missing.discard(name)
    if missing:
        raise SystemExit(f".env has no line for {', '.join(sorted(missing))}")
    env.write_text("\n".join(lines), encoding="utf-8")


def run(root: Path, values: dict[str, str]) -> None:
    commands = setup_block((root / "README.md").read_text(encoding="utf-8"))
    if commands[0] != CLONE:
        raise SystemExit(f"the README's setup block starts with {commands[0]!r}, not the clone {CLONE!r}")
    if COPY not in commands:
        raise SystemExit(f"the README's setup block has no {COPY!r}, after which a person fills in the values")
    print(f"+ {CLONE}   (made by the job, at the commit under test)", flush=True)
    for command in commands[1:]:
        print(f"+ {command}", flush=True)
        subprocess.run(["bash", "-euo", "pipefail", "-c", command], cwd=root, check=True)
        if command == COPY:
            fill(root / ".env", values)
            print(f"  .env: {', '.join(sorted(values))} filled in with the job's throwaway values", flush=True)


def self_test() -> int:
    """The README's block as this script reads it, and .env filled from a copy of .env.example."""
    failures = []
    commands = setup_block((ROOT / "README.md").read_text(encoding="utf-8"))
    if commands[:1] != [CLONE]:
        failures.append(f"the block's first command is {commands[:1]}, not the clone")
    if COPY not in commands or "make up" not in commands or commands.index(COPY) > commands.index("make up"):
        failures.append(f"the block does not copy .env.example before make up: {commands}")
    if any("#" in command for command in commands):
        failures.append(f"a comment was kept: {commands}")
    with tempfile.TemporaryDirectory() as tmp:
        env = Path(tmp) / ".env"
        shutil.copyfile(ROOT / ".env.example", env)
        before = env.read_text(encoding="utf-8").split("\n")
        fill(env, {"OPENAI_API_KEY": "placeholder", "POLICYPILOT_ACCESS_CODE": "abcdefgh"})
        after = env.read_text(encoding="utf-8").split("\n")
        changed = [(old, new) for old, new in zip(before, after) if old != new]
        if len(before) != len(after) or changed != [("OPENAI_API_KEY=", "OPENAI_API_KEY=placeholder"),
                                                    ("POLICYPILOT_ACCESS_CODE=", "POLICYPILOT_ACCESS_CODE=abcdefgh")]:
            failures.append(f"filling .env changed {changed}")
        try:
            fill(env, {"NOT_IN_THE_FILE": "x"})
            failures.append("a name .env does not have was not reported")
        except SystemExit:
            pass
    for failure in failures:
        print("SELF-TEST FAILED:", failure)
    if not failures:
        print("readme setup self-test: ALL OK")
    return 1 if failures else 0


def main() -> int:
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    if len(sys.argv) == 3 and sys.argv[1] == "--values":
        run(ROOT, read_values(Path(sys.argv[2])))
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())
