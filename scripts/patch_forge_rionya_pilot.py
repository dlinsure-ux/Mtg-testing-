#!/usr/bin/env python3
"""Opt-in Rionya-aware Forge spell selection; fail closed on pinned source drift."""
from pathlib import Path
import sys

ANCHOR = '            for (final SpellAbility sa : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, player)) {'
PATCH = '''            System.out.println("RIONYA_PILOT property_value=" + Boolean.getBoolean("forge.ai.rionyaCommanderFirst"));
            // Pilot V2: unconditional entry probe at this decision point.
            // A missing probe indicates the runtime never reached this method.
            if (Boolean.getBoolean("forge.ai.rionyaCommanderFirst")) {
                System.out.println("RIONYA_PILOT decision_entry phase="
                        + game.getPhaseHandler().getPhase()
                        + " commander_in_command="
                        + player.getCardsIn(ZoneType.Command).stream()
                            .anyMatch(c -> "Rionya, Fire Dancer".equals(c.getName()))
                        + " commander_in_play="
                        + player.isCardInPlay("Rionya, Fire Dancer"));
            }
            // Record available resources without exposing hidden card identities.
            if (Boolean.getBoolean("forge.ai.rionyaCommanderFirst")) {
                System.out.println("RIONYA_PILOT resources hand_count=" + player.getCardsIn(ZoneType.Hand).size()
                        + " command_count=" + player.getCardsIn(ZoneType.Command).size()
                        + " candidate_count=" + all.size());
            }
            // Once-per-main-phase private state snapshot for deck evaluation.
            // Records our own hand and battlefield; never reveals opponents' hands.
            if (Boolean.getBoolean("forge.ai.rionyaCommanderFirst")
                    && (game.getPhaseHandler().is(PhaseType.MAIN1)
                        || game.getPhaseHandler().is(PhaseType.MAIN2))) {
                final String snapshotKey = "forge.ai.rionya.snapshot";
                final String snapshotId = String.valueOf(game.getPhaseHandler().getTurn())
                        + ":" + game.getPhaseHandler().getPhase();
                if (!snapshotId.equals(System.getProperty(snapshotKey))) {
                    System.setProperty(snapshotKey, snapshotId);
                    final String handNames = player.getCardsIn(ZoneType.Hand).stream()
                            .map(c -> c.getName()).collect(java.util.stream.Collectors.joining("|"));
                    final String boardNames = player.getCardsIn(ZoneType.Battlefield).stream()
                            .map(c -> c.getName()).collect(java.util.stream.Collectors.joining("|"));
                    System.out.println("RIONYA_STATE turn=" + game.getPhaseHandler().getTurn()
                            + " phase=" + game.getPhaseHandler().getPhase()
                            + " hand=" + handNames + " board=" + boardNames);
                }
            }
            // Rionya-specific decisions. Legality and payment remain Forge-owned.
            if (Boolean.getBoolean("forge.ai.rionyaCommanderFirst")
                    && (game.getPhaseHandler().is(PhaseType.MAIN1)
                        || game.getPhaseHandler().is(PhaseType.MAIN2))
                    && game.getStack().isEmpty()
                    && player.getCardsIn(ZoneType.Command).stream()
                        .anyMatch(c -> "Rionya, Fire Dancer".equals(c.getName()))) {
                System.out.println("RIONYA_PILOT branch_entered=commander_check phase="
                        + game.getPhaseHandler().getPhase());
            }
            if (Boolean.getBoolean("forge.ai.rionyaCommanderFirst")
                    && player.getCardsIn(ZoneType.Command).stream()
                        .anyMatch(c -> "Rionya, Fire Dancer".equals(c.getName()))
                    && game.getStack().isEmpty()
                    && (game.getPhaseHandler().is(PhaseType.MAIN1)
                        || game.getPhaseHandler().is(PhaseType.MAIN2))) {
                System.out.println("RIONYA_PILOT commander_search candidate_count=" + all.size());
                for (final SpellAbility candidate : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, player)) {
                    if (!"Rionya, Fire Dancer".equals(candidate.getHostCard().getName())
                            || !candidate.isSpell()
                            || candidate.getHostCard().getZone() == null
                            || candidate.getHostCard().getZone().getZoneType() != ZoneType.Command) continue;
                    candidate.setActivatingPlayer(player);
                    final AiPlayDecision commanderDecision = canPlayAndPayFor(candidate);
                    System.out.println("RIONYA_PILOT commander_decision=" + commanderDecision);
                    if (commanderDecision == AiPlayDecision.WillPlay) {
                        System.out.println("RIONYA_PILOT commander_cast_priority=true phase="
                                + game.getPhaseHandler().getPhase());
                        return candidate;
                    }
                }
            }
            // Once Rionya is in play, prioritize affordable cantrips and mana
            // rituals in main phase 1, before the beginning-of-combat trigger.
            // Do not burn defensive removal/counters merely for spell count.
            if (Boolean.getBoolean("forge.ai.rionyaCommanderFirst")
                    && player.isCardInPlay("Rionya, Fire Dancer")
                    && game.getPhaseHandler().is(PhaseType.MAIN1)
                    && game.getStack().isEmpty()) {
                // Only spend precombat spells for Rionya if another creature can be copied.
                final boolean hasCopyTarget = player.getCreaturesInPlay().stream()
                    .anyMatch(c -> !"Rionya, Fire Dancer".equals(c.getName()));
                final java.util.List<String> precombat = java.util.Arrays.asList(
                    "Expedite", "Crimson Wisps", "Renegade Tactics", "Warlord's Fury",
                    "Ancestral Anger", "Crash Through", "Reckless Impulse",
                    "Wrenn's Resolve", "Faithless Looting");
                System.out.println("RIONYA_PILOT copy_target_available=" + hasCopyTarget);
                // Let Forge evaluate rituals based on mana and payoffs rather than
                // blindly burning them for spell count.
                for (final SpellAbility candidate : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, player)) {
                    if (!hasCopyTarget || !candidate.isSpell()
                            || !precombat.contains(candidate.getHostCard().getName())) continue;
                    candidate.setActivatingPlayer(player);
                    final AiPlayDecision precombatDecision = canPlayAndPayFor(candidate);
                    System.out.println("RIONYA_PILOT precombat_decision=" + precombatDecision
                            + " card=" + candidate.getHostCard().getName());
                    if (precombatDecision == AiPlayDecision.WillPlay) {
                        System.out.println("RIONYA_PILOT precombat_spell=" + candidate.getHostCard().getName());
                        return candidate;
                    }
                }
            }
'''
def patch(src: str) -> str:
    if "RIONYA_PILOT" in src:
        raise ValueError("Already patched")
    if src.count(ANCHOR) != 1:
        raise ValueError("Pinned decision anchor missing or ambiguous")
    return src.replace(ANCHOR, PATCH + ANCHOR, 1)

if __name__ == "__main__":
    p = Path(sys.argv[1])
    p.write_text(patch(p.read_text()))
