#!/usr/bin/env python3
"""Profile pinned Forge speculative counterattacks, including repeated inputs.

No cache is used: hypothetical combat can mutate temporary state, and caching
without a validated game-state key would risk changing AI decisions.
"""
from pathlib import Path
import sys

ANCHOR = """            block.assignBlockersForCombat(combat, excludedBlockers);"""
REPLACEMENT = """            final long forgeSpeculativeStarted = System.nanoTime();
            final int forgeSpeculativeAttackers = combat.getAttackers().size();
            final int forgeSpeculativeExcluded = excludedBlockers == null ? 0 : excludedBlockers.size();
            try {
                // A single hypothetical blocker search on a wide token board can
                // exceed the AI decision timeout (run #143: 7.0 seconds).
                // Opt-in: only counterattack predictions, never real combat.
                // Without speculative blockers the predicted damage is
                // conservative; AI may choose to hold attackers back.
                if (Boolean.getBoolean("forge.ai.speculativeCombatGuard")
                        && forgeSpeculativeAttackers >= 12) {
                    ForgeDecisionProfile.recordSkippedSpeculation();
                    System.err.println("FORGE_SPECULATIVE_BLOCK_SKIPPED attackers="
                            + forgeSpeculativeAttackers + " defender=" + ai.getName());
                } else {
                    block.assignBlockersForCombat(combat, excludedBlockers);
                }
            } finally {
                final long forgeSpeculativeMs = (System.nanoTime() - forgeSpeculativeStarted) / 1000000L;
                if (forgeSpeculativeMs >= 100L) {
                    System.err.println("FORGE_SPECULATIVE_BLOCK_PROFILE elapsed_ms=" + forgeSpeculativeMs
                            + " attackers=" + forgeSpeculativeAttackers
                            + " excluded_blockers=" + forgeSpeculativeExcluded
                            + " defender=" + ai.getName()
                            + " attacker_player=" + opp.getName()
                            + " serious=" + serious + " check_diff=" + checkDiff);
                }
            }"""

def patch(source):
    start = source.index("public static int predictNextCombatsRemainingLife(")
    end = source.index("    public static boolean isETBprevented(", start)
    section = source[start:end]
    if section.count(ANCHOR) != 1 or "FORGE_SPECULATIVE_BLOCK_PROFILE" in section:
        raise ValueError("Pinned speculative combat anchor missing, changed or already patched")
    return source[:start] + section.replace(ANCHOR, REPLACEMENT, 1) + source[end:]

if __name__ == "__main__":
    p = Path(sys.argv[1])
    p.write_text(patch(p.read_text()))
