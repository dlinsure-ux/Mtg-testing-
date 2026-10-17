#!/usr/bin/env python3
"""Time pinned Forge counterattack predictions; do not alter their decisions."""
import argparse
from pathlib import Path

CALL = "ComputerUtil.predictNextCombatsRemainingLife(ai, playAggro, pilotsNonAggroDeck, 0, notNeededAsBlockers)"
BASE = "lastAcceptableBaselineLife = " + CALL + ";"
CUR = "int currentBaselineLife = " + CALL + ";"

def patch(source):
    if source.count(BASE) != 1 or source.count(CUR) != 1:
        raise ValueError("Pinned counterattack calls missing or ambiguous")
    source = source.replace(BASE, """final long forgeBaselineStarted = ForgeDecisionProfile.beginCounterattackPrediction();
                    try {
                        """ + BASE + """
                    } finally {
                        ForgeDecisionProfile.endCounterattackPrediction(forgeBaselineStarted);
                    }""", 1)
    source = source.replace(CUR, """final long forgeCounterattackStarted = ForgeDecisionProfile.beginCounterattackPrediction();
                int currentBaselineLife;
                try {
                    currentBaselineLife = """ + CALL + """;
                } finally {
                    ForgeDecisionProfile.endCounterattackPrediction(forgeCounterattackStarted);
                }""", 1)
    return source

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    args.source.write_text(patch(args.source.read_text()))
