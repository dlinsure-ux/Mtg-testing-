#!/usr/bin/env python3
"""Opt-in Forge Lathiel counter-recipient heuristic. Fail closed on upstream drift."""
from pathlib import Path
import sys

path = Path(sys.argv[1])
source = path.read_text()
anchor = "        return ComputerUtilCard.getBestAI(filtered);\n    }\n\n    /**\n     * used for ExistingCounterType"
if source.count(anchor) != 1:
    raise SystemExit("Lathiel counter patch anchor missing or ambiguous")
replacement = """        // Opt-in Lathiel-only choice heuristic; leave all other commanders unchanged.
        if (Boolean.getBoolean("forge.ai.lathielCounterPilot")
                && ai.getCommanders().stream().anyMatch(c -> "Lathiel, the Bounteous Dawn".equals(c.getName()))
                && types.contains(CounterEnumType.P1P1)) {
            Card bestLathiel = null;
            int bestScore = Integer.MIN_VALUE;
            for (Card candidate : filtered) {
                int score = ComputerUtilCard.evaluateCreature(candidate);
                if (candidate.hasKeyword(Keyword.LIFELINK)) score += 120;
                if (candidate.hasKeyword(Keyword.FLYING)) score += 100;
                if (candidate.hasKeyword(Keyword.TRAMPLE)) score += 75;
                if (candidate.hasKeyword(Keyword.DOUBLE_STRIKE)) score += 110;
                if (candidate.getNetPower() <= 1 && candidate.isCreature()) score -= 65;
                if (score > bestScore) {
                    bestScore = score;
                    bestLathiel = candidate;
                }
            }
            if (bestLathiel != null) {
                System.err.println("LATHIEL_COUNTER_PILOT selected=" + bestLathiel.getName()
                        + " score=" + bestScore + " candidates=" + filtered.size());
                return bestLathiel;
            }
        }
        return ComputerUtilCard.getBestAI(filtered);
    }

    /**
     * used for ExistingCounterType"""
source = source.replace(anchor, replacement)
trigger_anchor = """                } else if (preferred) {
                    list = ComputerUtil.getSafeTargets(ai, sa, list);
                    choice = chooseBoonTarget(list, type);
                    if (choice == null && mandatory) {
                        choice = Aggregates.random(list);
                    }
                } else if (type.equals("P1P1")) {"""
trigger_replacement = """                } else if (preferred) {
                    list = ComputerUtil.getSafeTargets(ai, sa, list);
                    // Lathiel's end-step divided counter trigger enters this path, not chooseSingleCard.
                    if (Boolean.getBoolean("forge.ai.lathielCounterPilot")
                            && "Lathiel, the Bounteous Dawn".equals(source.getName())
                            && "P1P1".equals(type)) {
                        System.err.println("LATHIEL_COUNTER_TRIGGER entered candidates=" + list.size()
                                + " divided=" + divided + " remaining=" + left);
                        choice = null;
                        int bestScore = Integer.MIN_VALUE;
                        for (Card candidate : list) {
                            if (candidate.getController() != ai || !candidate.isCreature()) continue;
                            int score = ComputerUtilCard.evaluateCreature(candidate);
                            if (candidate.hasKeyword(Keyword.LIFELINK)) score += 120;
                            if (candidate.hasKeyword(Keyword.FLYING)) score += 100;
                            if (candidate.hasKeyword(Keyword.TRAMPLE)) score += 75;
                            if (candidate.hasKeyword(Keyword.DOUBLE_STRIKE)) score += 110;
                            if (candidate.getNetPower() <= 1) score -= 65;
                            if (score > bestScore) {
                                bestScore = score;
                                choice = candidate;
                            }
                        }
                        System.err.println("LATHIEL_COUNTER_TRIGGER selected="
                                + (choice == null ? "none" : choice.getName())
                                + " score=" + bestScore);
                    } else {
                        choice = chooseBoonTarget(list, type);
                    }
                    if (choice == null && mandatory) {
                        choice = Aggregates.random(list);
                    }
                } else if (type.equals("P1P1")) {"""
if source.count(trigger_anchor) != 1:
    raise SystemExit("Lathiel trigger patch anchor missing or ambiguous")
source = source.replace(trigger_anchor, trigger_replacement)
path.write_text(source)
print("Installed opt-in Lathiel counter recipient selection")
