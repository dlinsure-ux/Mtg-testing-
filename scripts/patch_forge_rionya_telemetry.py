#!/usr/bin/env python3
"""Subscribe the pinned Forge Game event bus to RionyaTelemetry."""
from pathlib import Path
import sys

ANCHOR = "        phaseHandler = new PhaseHandler(this);"
PATCH = """        phaseHandler = new PhaseHandler(this);
        if (Boolean.getBoolean(\"forge.ai.rionyaTelemetry\")) {
            subscribeToEvents(new RionyaTelemetry(this));
            System.out.println(\"RIONYA_EVENT|type=telemetry_enabled|turn=0|phase=\");
        }"""

def patch(src: str) -> str:
    if "new RionyaTelemetry(this)" in src:
        raise ValueError("Already patched")
    if src.count(ANCHOR) != 1:
        raise ValueError("Pinned Game constructor anchor missing or ambiguous")
    return src.replace(ANCHOR, PATCH, 1)

if __name__ == "__main__":
    p = Path(sys.argv[1])
    p.write_text(patch(p.read_text()))
