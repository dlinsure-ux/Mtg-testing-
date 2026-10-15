#!/usr/bin/env python3
"""Apply the pinned Commander AI Lab alternate-win parser patch, fail closed."""
from pathlib import Path

TARGET = Path("lab/src/main/java/commanderailab/batch/BatchRunner.java")
OLD = "has won because (.+)"
NEW = "has won (?:because|due to effect of) (.+)"


def apply(source: str) -> tuple[str, str]:
    if source.count(OLD) == 1 and source.count(NEW) == 0:
        return source.replace(OLD, NEW), "patched"
    if source.count(OLD) == 0 and source.count(NEW) == 1:
        return source, "already patched"
    raise ValueError("Unexpected upstream winner parser; refusing ambiguous patch")


def main() -> None:
    original = TARGET.read_text()
    updated, status = apply(original)
    if updated != original:
        TARGET.write_text(updated)
    assert TARGET.read_text().count(NEW) == 1
    print(f"Lab outcome parser: {status}")


if __name__ == "__main__":
    main()
