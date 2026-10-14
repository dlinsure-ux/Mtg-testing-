#!/usr/bin/env python3
"""Fail closed before Forge build if a selected deck is malformed or has known unsupported cards."""
from pathlib import Path
import sys

DECKS = [Path(p) for p in sys.argv[1:]]
KNOWN_UNSUPPORTED = {"smaug, wicked worm", "miss highwater"}
errors = []
if len(DECKS) != 4:
    errors.append("expected exactly four deck paths")
for path in DECKS:
    if not path.is_file():
        errors.append(f"{path}: missing deck")
        continue
    section = None
    main = commander = 0
    seen = set()
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.strip()
        if not line:
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line.lower()
            continue
        if section not in ("[main]", "[commander]"):
            continue
        parts = line.split(" ", 1)
        if len(parts) != 2 or not parts[0].isdigit() or int(parts[0]) < 1:
            errors.append(f"{path}:{number}: invalid card line {line!r}")
            continue
        quantity, name = int(parts[0]), parts[1].strip()
        if name.casefold() in KNOWN_UNSUPPORTED:
            errors.append(f"{path}:{number}: known unsupported Forge card: {name}")
        if name.casefold() in seen and name.casefold() not in {"forest", "island", "mountain", "plains", "swamp", "wastes"}:
            errors.append(f"{path}:{number}: duplicate nonbasic card: {name}")
        seen.add(name.casefold())
        if section == "[main]":
            main += quantity
        else:
            commander += quantity
    if main + commander != 100 or commander != 1:
        errors.append(f"{path}: {main} main + {commander} commander; expected 99 + 1")
    print(f"{path}: {main} main + {commander} commander")
if errors:
    print("\n".join("PREFLIGHT ERROR: " + error for error in errors), file=sys.stderr)
    sys.exit(1)
print("Static deck preflight passed (runtime card database verification still required).")
