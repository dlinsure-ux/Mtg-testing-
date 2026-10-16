#!/usr/bin/env python3
"""Replace tail-recursive chump blocker walk with equivalent iterative walk.

Preserves order, danger checks, attacker removal, and block assignments.
No search cap, no changed evaluation heuristic.
"""
import argparse
from pathlib import Path

START = "    private void makeChumpBlocks(final Combat combat, List<Card> attackers) {"
END = "    // Block creatures with \"can't be blocked except by two or more creatures\""

def patch(source):
    if source.count(START) != 1 or source.count(END) != 1:
        raise ValueError("Pinned Forge chump-block anchors changed")
    a = source.index(START)
    b = source.index(END, a)
    original = source[a:b]
    # Refuse to apply to an unknown implementation.
    if original.count("makeChumpBlocks(combat, attackers);") != 3:
        raise ValueError("Unexpected recursive branches in pinned implementation")
    revised = original.replace(
        "    private void makeChumpBlocks(final Combat combat, List<Card> attackers) {",
        "    private void makeChumpBlocks(final Combat combat, List<Card> attackers) {\n        chumpLoop: while (true) {",
        1,
    )
    revised = revised.replace("            makeChumpBlocks(combat, attackers);\n            return;", "            continue;", 2)
    revised = revised.replace("                            makeChumpBlocks(combat, attackers);\n                            return;", "                            continue chumpLoop;", 1)\n    revised = revised.replace("        makeChumpBlocks(combat, attackers);\n    }", "        }\n    }", 1)
    if "makeChumpBlocks(combat, attackers);" in revised:
        raise ValueError("Tail recursion not fully eliminated")
    return source[:a] + revised + source[b:]

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("source", type=Path)
    args = p.parse_args()
    args.source.write_text(patch(args.source.read_text()))
