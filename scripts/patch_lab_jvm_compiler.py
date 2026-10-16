#!/usr/bin/env python3
"""Restore HotSpot's full tiered JIT for Forge subprocesses.

Upstream Commander AI Lab pins -XX:TieredStopAtLevel=1, disabling the
optimizing C2 compiler. Complex combat evaluation is CPU intensive.
This changes only JVM compilation policy, not game rules or AI heuristics.
"""
from pathlib import Path
import sys

source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    "lab/src/main/java/commanderailab/batch/BatchRunner.java"
)
before = 'cmd.add("-XX:TieredStopAtLevel=1");'
after = 'cmd.add("-XX:TieredStopAtLevel=4");\n        cmd.add("-Dforge.ai.speculativeCombatGuard=true");'
text = source.read_text()
if text.count(before) != 1:
    raise SystemExit("Expected exactly one upstream C1-only JVM flag; aborting")
source.write_text(text.replace(before, after, 1))
print("Enabled full tiered JIT for Forge subprocess (-XX:TieredStopAtLevel=4)")
