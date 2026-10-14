#!/usr/bin/env python3
"""Fail closed on malformed pilot specifications; never claim runtime integration."""
import json
import sys
from pathlib import Path

def validate(path):
    d = json.loads(Path(path).read_text())
    assert d.get("schema_version") == 1
    assert d.get("pilot") == "lathiel"
    assert isinstance(d.get("version"), str) and d["version"]
    assert d.get("status") in {"specification_only", "runtime_integrated"}
    assert isinstance(d.get("runtime_integrated"), bool)
    if d["status"] == "runtime_integrated":
        raise ValueError("Runtime integration cannot be certified by config alone; use a verified Forge adapter regression suite")
    priorities = d.get("priorities")
    assert isinstance(priorities, dict)
    assert isinstance(priorities.get("tutor"), list) and all(isinstance(x,str) and x for x in priorities["tutor"])
    return d

if __name__ == "__main__":
    for path in sys.argv[1:] or ["pilots/lathiel/v0.1.json"]:
        d=validate(path)
        print(f"VALID PILOT SPEC {d['pilot']} {d['version']} (NOT ACTIVE IN FORGE)")
