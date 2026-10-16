#!/usr/bin/env python3
"""Profile expensive AI combat destruction checks, without altering decisions."""
import argparse
from pathlib import Path

ANCHOR = """    public static boolean canDestroyBlocker(Player ai, Card blocker, Card attacker, final Combat combat,
            final boolean withoutAbilities, final boolean predictAbilities) {"""

def patch(source):
    if source.count(ANCHOR) != 1:
        raise ValueError("Pinned canDestroyBlocker implementation signature changed")
    start = source.index(ANCHOR)
    opening = source.index("{", start + len(ANCHOR) - 1)
    depth = 1
    pos = opening + 1
    while depth:
        if source[pos] == "{":
            depth += 1
        elif source[pos] == "}":
            depth -= 1
        pos += 1
    body = source[opening + 1:pos - 1]
    # Java try/finally preserves all returns and thrown exceptions.
    replacement = """{
        final long forgeDestroyStarted = System.nanoTime();
        try {""" + body + """
        } finally {
            final long forgeDestroyElapsed = System.nanoTime() - forgeDestroyStarted;
            if (forgeDestroyElapsed >= 250000000L) {
                System.err.println("FORGE_DESTROY_BLOCKER_SLOW elapsed_ms="
                        + forgeDestroyElapsed / 1000000L);
            }
        }
    }"""
    return source[:opening] + replacement + source[pos:]

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    args.source.write_text(patch(args.source.read_text()))
