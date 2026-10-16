#!/usr/bin/env python3
"""Conservatively bound repeated speculative counterattack simulations.

Only changes AI heuristic search, never combat rules or legal actions.
The untested blockers remain held back rather than optimistically sent to attack.
"""
import argparse
from pathlib import Path

ANCHOR = """            for (Card c : blockers) {
                if (vigilantes.contains(c)) {"""
REPLACEMENT = """            for (Card c : blockers) {
                // A completed nested simulation may consume seconds on wide boards.
                // Stop further speculative calls; unexamined blockers stay home.
                if (ForgeDecisionProfile.speculativeCombatBudgetExceeded()) {
                    System.err.println("FORGE_SPECULATIVE_COMBAT_GUARD player=" + ai.getName()
                            + " remaining_blockers=" + (blockers.size() - blockers.indexOf(c)));
                    break;
                }
                if (vigilantes.contains(c)) {"""

def patch(source: str) -> str:
    if "FORGE_SPECULATIVE_COMBAT_GUARD" in source:
        raise ValueError("Already patched")
    if source.count(ANCHOR) != 1:
        raise ValueError("Pinned Forge counterattack loop changed")
    return source.replace(ANCHOR, REPLACEMENT, 1)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    args.source.write_text(patch(args.source.read_text()))
