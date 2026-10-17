#!/usr/bin/env python3
"""Summarize Forge profiling markers without confusing nested durations with wall time."""
import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

BLOCK = re.compile(r"^FORGE_BLOCK_PROFILE method=(\S+) elapsed_ms=(\d+) attackers=(\d+) player=(.+)$")
COMBAT = re.compile(r"^FORGE_COMBAT_PROFILE method=(\S+) elapsed_ms=(\d+) player=(.+?) card=(.+)$")
TIMEOUT = re.compile(r"^AI_DECISION_TIMEOUT player=(.+?) turn=(\d+) phase=(\S+) budget_seconds=(\d+) candidates=(\d+)$")

def summarize(source: str) -> dict:
    timings = defaultdict(list)
    attackers = defaultdict(list)
    timeout_rows = []
    timeout_frames = []
    for line in source.splitlines():
        match = BLOCK.fullmatch(line)
        if match:
            method, ms, count, player = match.groups()
            key = f"block:{method}"
            timings[key].append(int(ms))
            attackers[key].append(int(count))
            continue
        match = COMBAT.fullmatch(line)
        if match:
            method, ms, player, card = match.groups()
            timings[f"combat:{method}"].append(int(ms))
            continue
        match = TIMEOUT.fullmatch(line)
        if match:
            player, turn, phase, budget, candidates = match.groups()
            timeout_rows.append(dict(player=player, turn=int(turn), phase=phase,
                                     budget_seconds=int(budget), candidates=int(candidates)))
        if line.startswith("AI_TIMEOUT_STACK "):
            timeout_frames.append(line[len("AI_TIMEOUT_STACK "):])
    def percentile(values, p):
        values = sorted(values)
        return values[(len(values)-1)*p//100]
    methods = {}
    for key, values in sorted(timings.items()):
        methods[key] = dict(slow_call_count=len(values),
                            inclusive_slow_call_ms=sum(values),
                            max_slow_call_ms=max(values),
                            p50_slow_call_ms=percentile(values, 50),
                            p95_slow_call_ms=percentile(values, 95),
                            p99_slow_call_ms=percentile(values, 99),
                            min_logged_threshold_ms=100 if key.startswith("block:") else 250)
        if key in attackers:
            methods[key]["max_attackers_in_logged_calls"] = max(attackers[key])
    return dict(schema_version=1, methods=methods, timeout_count=len(timeout_rows),
                timeout_by_phase=dict(sorted(Counter(x["phase"] for x in timeout_rows).items())),
                timeout_by_player=dict(sorted(Counter(x["player"] for x in timeout_rows).items())),
                timeout_contexts=timeout_rows, timeout_stack_frame_count=len(timeout_frames),
                caveats=["Inclusive timings can overlap and cannot be added to obtain wall time.",
                         "Only threshold-exceeding calls are logged; percentiles are of logged slow calls, not all calls.",
                         "Timeout frames are not individually attributed to a decision by this parser."])

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = summarize(args.log.read_text(errors="replace"))
    result = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result)
    else:
        print(result, end="")

if __name__ == "__main__":
    main()
