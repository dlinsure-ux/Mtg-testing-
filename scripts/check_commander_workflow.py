#!/usr/bin/env python3
"""Fail-closed structural checks for the expensive Commander series workflow."""
from pathlib import Path
import re
import sys

def check(workflow: str) -> None:
    assert 'Run single diagnostic Forge game' in workflow
    start = workflow.index('      - name: Run single diagnostic Forge game')
    section = workflow[start:]
    loop = section.index('for ((game=1; game<=GAME_COUNT; game++)); do')
    body = section[loop:]
    creation = body.index('mkdir -p "results/game-$game" "diagnostics/game-$game"')
    seed_write = body.index('> "results/game-$game/replay-seed.txt"')
    assert creation < seed_write, "game directory must precede seed write"
    assert body.count('mkdir -p "results/game-$game" "diagnostics/game-$game"') == 1
    assert 'export FORGE_LAB_REPLAY_SEED=' in body
    assert 'timeout 900 xvfb-run' in body
    assert 'python3 scripts/validate_forge_game.py' in body
    assert 'if [ "$failed" -ne 0 ]; then' in body
    assert 'scripts/patch_forge_replay_seed.py' in workflow
    assert 'FORGE_REPLAY_SEED=' in workflow
    assert 'FORGE_LAB_REPLAY_SEED' in workflow
    assert 'scripts/check_commander_workflow.py' in workflow

if __name__ == "__main__":
    path = Path(sys.argv[1] if len(sys.argv) > 1 else ".github/workflows/commander-ai-lab-build.yml")
    check(path.read_text())
    print("Commander workflow structural checks passed")
