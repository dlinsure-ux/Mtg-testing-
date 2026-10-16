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
    import re
    # Match each tail-call branch by its own indentation, not substring replacement.
    nested_pattern = r"(?m)^( {28})makeChumpBlocks\(combat, attackers\);\n\1return;"
    ordinary_pattern = r"(?m)^( {12})makeChumpBlocks\(combat, attackers\);\n\1return;"
    revised, n1 = re.subn(nested_pattern, r"\1continue chumpLoop;", revised)
    revised, n2 = re.subn(ordinary_pattern, r"\1continue;", revised)
    tail_pattern = r"(?m)^ {8}makeChumpBlocks\(combat, attackers\);\n {4}\}"
    revised, n3 = re.subn(tail_pattern, "        }\n    }", revised)
    if (n1, n2, n3) != (1, 1, 1):
        raise ValueError(f"Unexpected tail-call layout: nested={n1}, ordinary={n2}, tail={n3}")
    if "makeChumpBlocks(combat, attackers);" in revised:
        raise ValueError("Tail recursion not fully eliminated")
    return source[:a] + revised + source[b:]

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("source", type=Path)
    args = p.parse_args()
    args.source.write_text(patch(args.source.read_text()))
