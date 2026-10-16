#!/usr/bin/env python3
"""Instrument pinned Forge FutureTask AI decision boundaries, fail closed."""
import argparse
from pathlib import Path

START = '        FutureTask<SpellAbility> future = new FutureTask<>(() -> {'
END = '            return null;\n        });'
TIMEOUT = '              if (e instanceof TimeoutException) {'

def patch(src: str) -> str:
    if 'FORGE_DECISION_PROFILE' in src or 'ForgeDecisionProfile.begin(' in src:
        raise ValueError('Already instrumented')
    if src.count(START) != 1 or src.count(END) != 1 or src.count(TIMEOUT) != 1:
        raise ValueError('Pinned AI worker/timeout anchors changed')
    src = src.replace(START, START + '''
            final ForgeDecisionProfile.Scope forgeDecisionScope =
                    ForgeDecisionProfile.begin(player.getName(), String.valueOf(game.getPhaseHandler().getPhase()));
            try {''', 1)
    src = src.replace(END, '''            return null;
            } finally {
                ForgeDecisionProfile.finish(forgeDecisionScope);
            }
        });''', 1)
    # Timeout snapshots are emitted by worker finally on cancellation/termination.
    # Avoid reading thread-local state from supervisor thread.
    return src

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    args = parser.parse_args()
    args.source.write_text(patch(args.source.read_text()))

if __name__ == '__main__':
    main()
