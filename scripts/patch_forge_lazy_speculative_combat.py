#!/usr/bin/env python3
"""Avoid constructing speculative Combat objects when an opponent has no legal attackers.

Semantics-preserving: attack eligibility is evaluated exactly as before, in order;
only the allocation of an unused Combat is deferred until the first eligible attacker.
"""
import argparse
from pathlib import Path

OLD = """            Combat combat = new Combat(opp);
            boolean containsAttacker = false;"""
NEW = """            Combat combat = null;
            boolean containsAttacker = false;"""
ATTACK = """                    combat.addAttacker(att, ai);
                    containsAttacker = true;"""
REPLACEMENT = """                    if (combat == null) {
                        combat = new Combat(opp);
                    }
                    combat.addAttacker(att, ai);
                    containsAttacker = true;"""

def patch(source):
    if source.count(OLD) != 1 or source.count(ATTACK) < 1:
        raise ValueError("Pinned Forge combat prediction anchor changed")
    start = source.index("public static int predictNextCombatsRemainingLife(")
    end = source.index("    public static boolean isETBprevented(", start)
    section = source[start:end]
    if section.count(OLD) != 1 or section.count(ATTACK) != 1:
        raise ValueError("Unexpected prediction implementation")
    section = section.replace(OLD, NEW, 1).replace(ATTACK, REPLACEMENT, 1)
    return source[:start] + section + source[end:]

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("source", type=Path)
    a = p.parse_args()
    a.source.write_text(patch(a.source.read_text()))
