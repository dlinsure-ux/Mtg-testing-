#!/usr/bin/env python3
"""Opt-in conservative guard for experimental A/B comparisons.

Default-off means original Forge attack heuristics remain intact.
Enable only with -Dforge.ai.speculativeCombatGuard=true.
"""
import argparse
from pathlib import Path

ANCHOR = """            for (Card c : blockers) {
                if (vigilantes.contains(c)) {"""
REPLACEMENT = """            for (Card c : blockers) {
                // A completed nested simulation may consume seconds on wide boards.
                // Stop further speculative calls; unexamined blockers stay home.
                if (Boolean.getBoolean("forge.ai.speculativeCombatGuard")\n                        && ForgeDecisionProfile.speculativeCombatBudgetExceeded()) {
                    ForgeDecisionProfile.recordSkippedSpeculation();
                    break;
                }
                if (vigilantes.contains(c)) {"""

def patch(source: str) -> str:
    if "FORGE_SPECULATIVE_COMBAT_GUARD" in source:
        raise ValueError("Already patched")
    if source.count(ANCHOR) != 1:
        raise ValueError("Pinned Forge counterattack loop changed")
    source = source.replace(ANCHOR, REPLACEMENT, 1)
    # Preserve defensive behavior: skip baseline prediction if decision time is
    # already exhausted, without skipping the later blocker selection logic.
    baseline = "lastAcceptableBaselineLife = ComputerUtil.predictNextCombatsRemainingLife(ai, playAggro, pilotsNonAggroDeck, 0, notNeededAsBlockers);"
    if source.count(baseline) != 1:
        raise ValueError("Pinned baseline prediction anchor changed")
    source = source.replace(baseline, """if (!Boolean.getBoolean("forge.ai.speculativeCombatGuard")\n                        || !ForgeDecisionProfile.speculativeCombatBudgetExceeded()) {
                    """ + baseline + """
                } else {
                    ForgeDecisionProfile.recordSkippedSpeculation();
                }""", 1)
    return source

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    args.source.write_text(patch(args.source.read_text()))
