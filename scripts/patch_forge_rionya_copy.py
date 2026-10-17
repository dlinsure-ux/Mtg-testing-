#!/usr/bin/env python3
"""Rionya copy-choice policy for pinned Forge CopyPermanentAi."""
from pathlib import Path
import sys
ANCHOR = '        return ComputerUtilCard.getBestAI(options);'
TARGET_ANCHOR = '                list.remove(choice);'
PATCH = '''        if (Boolean.getBoolean("forge.ai.rionyaCommanderFirst")
                && "Rionya, Fire Dancer".equals(sa.getHostCard().getName())) {
            Card best = null;
            int bestScore = Integer.MIN_VALUE;
            for (Card c : options) {
                if (!c.isCreature() || c.getController() != ai
                        || "Rionya, Fire Dancer".equals(c.getName())) continue;
                int score = ComputerUtilCard.evaluateCreature(c);
                switch (c.getName()) {
                    case "Scourge of Valkas": score += 1400; break;
                    case "Fanatic of Mogis": score += 1150; break;
                    case "Coercive Recruiter": score += ai.getOpponents().getCreaturesInPlay().isEmpty() ? 0 : 1200; break;
                    case "Ardent Elementalist": score += ai.getCardsIn(ZoneType.Graveyard).stream().anyMatch(x -> x.isInstant() || x.isSorcery()) ? 950 : 0; break;
                    case "Solemn Simulacrum": score += 850; break;
                    case "Goblin Chainwhirler": score += 700; break;
                    case "Humble Defector": score += 650; break;
                    case "Tectonic Giant": score += 550; break;
                    default: break;
                }
                if (score > bestScore) { bestScore = score; best = c; }
            }
            if (best != null) {
                System.err.println("RIONYA_PILOT copy_target=" + best.getName()
                        + " score=" + bestScore);
                return best;
            }
        }
'''
def patch(src):
    if "RIONYA_PILOT" in src: raise ValueError("Already patched")
    if src.count(ANCHOR) != 1: raise ValueError("Pinned copy-choice anchor changed")
    src = src.replace(ANCHOR, PATCH+ANCHOR, 1)
    if src.count(TARGET_ANCHOR) != 1:
        raise ValueError("Pinned Rionya target-loop anchor changed")
    # The actual card uses a targeted CopyPermanent trigger. Patch that path.
    target_patch = PATCH.replace(
        '            for (Card c : options) {',
        '            for (Card c : list) {'
    ).replace('                return best;', '                choice = best;')
    target_patch = target_patch.replace('ai.get', 'aiPlayer.get').replace(' != ai', ' != aiPlayer')
    src = src.replace(TARGET_ANCHOR, target_patch + TARGET_ANCHOR, 1)
    return src
if __name__ == "__main__":
    p=Path(sys.argv[1]); p.write_text(patch(p.read_text()))
