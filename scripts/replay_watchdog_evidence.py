#!/usr/bin/env python3
"""Replay an existing five-game evidence artifact without executing or modifying Forge."""
import json
import sys
from pathlib import Path
from scripts.game_progress_watchdog import progress_markers

root = Path(sys.argv[1])
results = []
for n in range(1, 6):
    folder = root / 'results' / f'game-{n}'
    report = folder / 'validation-report.json'
    if not report.is_file():
        raise SystemExit(f'MISSING validation report for game {n}')
    validated = json.loads(report.read_text())
    if validated.get('classification') != 'VALID':
        raise SystemExit(f'INVALID historical game {n}')
    logs = [folder / 'smoke-test.log', folder / 'forge-sim-debug.log']
    found = [p for p in logs if p.is_file() and p.stat().st_size]
    if not found:
        raise SystemExit(f'MISSING game logs for game {n}')
    observed = [(p, progress_markers(p.read_text(errors='replace'))) for p in found]
    if not any(m['last_turn'] is not None for _, m in observed):
        raise SystemExit(f'NO TURN progression in game {n}: {observed}')
    if not any(m['terminal_result'] for _, m in observed):
        raise SystemExit(f'NO TERMINAL result in game {n}: {observed}')
    results.append({'game': n, 'logs': [str(p) for p, _ in observed], 'markers': [m for _, m in observed]})
print(json.dumps({'classification': 'PASS', 'replayed_games': len(results), 'games': results}, indent=2))
