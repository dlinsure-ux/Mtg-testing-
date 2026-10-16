#!/usr/bin/env python3
"""Apply bounded aggregate timing to the pinned Forge blocker-search method.

Fail closed on upstream changes; preserve original method body and return behavior.
"""
import argparse
from pathlib import Path

SIGNATURE = "    private void makeGoodBlocks(final Combat combat) {"
MARKER = "FORGE_BLOCK_AGGREGATE"
FIELDS = """    // Diagnostic counters only; do not influence AI choices or game state.
    private static final java.util.concurrent.atomic.AtomicLong forgeBlockCalls = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong forgeBlockTotalNs = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong forgeBlockMaxNs = new java.util.concurrent.atomic.AtomicLong();

"""

def patch(source: str) -> str:
    if MARKER in source:
        raise ValueError("Already instrumented; refusing double wrapping")
    if source.count(SIGNATURE) != 1:
        raise ValueError("Pinned Forge blocker method signature missing or ambiguous")
    start = source.index(SIGNATURE)
    opening = source.index("{", start)
    depth = 0
    end = None
    for i in range(opening, len(source)):
        if source[i] == "{":
            depth += 1
        elif source[i] == "}":
            depth -= 1
            if depth == 0:
                end = i
                break
    if end is None:
        raise ValueError("Unbalanced Forge blocker method")
    original = source[opening + 1:end]
    wrapped = """
        final long forgeBlockStarted = System.nanoTime();
        final int forgeAttackerCount = attackersLeft.size();
        try {""" + original + """
        } finally {
            final long forgeBlockElapsedNs = System.nanoTime() - forgeBlockStarted;
            final long forgeBlockCallCount = forgeBlockCalls.incrementAndGet();
            forgeBlockTotalNs.addAndGet(forgeBlockElapsedNs);
            forgeBlockMaxNs.accumulateAndGet(forgeBlockElapsedNs, Math::max);
            final long forgeBlockElapsedMs = forgeBlockElapsedNs / 1000000L;
            if (forgeBlockElapsedMs >= 100L) {
                System.err.println("FORGE_BLOCK_PROFILE method=makeGoodBlocks elapsed_ms="
                        + forgeBlockElapsedMs + " attackers=" + forgeAttackerCount
                        + " player=" + ai.getName());
            }
            // Bounded log volume: one cumulative snapshot per 32 completed calls.
            if ((forgeBlockCallCount & 31L) == 0L) {
                System.err.println("FORGE_BLOCK_AGGREGATE method=makeGoodBlocks calls="
                        + forgeBlockCallCount + " inclusive_total_ms="
                        + forgeBlockTotalNs.get() / 1000000L + " max_ms="
                        + forgeBlockMaxNs.get() / 1000000L);
            }
        }
    """
    return source[:start] + FIELDS + source[start:opening + 1] + wrapped + source[end:]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    src = args.source.read_text()
    args.source.write_text(patch(src))

if __name__ == "__main__":
    main()
