# Rionya Fire Dancer — card-aware pilot specification (v0.1)
Status: strategy requirements only; not yet wired into Forge AI. No tests launched.

## Nonnegotiable gameplay priorities
- Cast Rionya as soon as legally affordable in a useful main phase, except when immediate survival requires interaction. Never treat her as an ordinary five-mana creature.
- Rionya copies **one other creature you control** at the beginning of combat. Cast cheap instants/sorceries BEFORE that trigger; each spell cast this turn adds one token. The copied tokens gain haste and are exiled at the beginning of the next end step.
- A copy of a creature with an ETB trigger triggers that ETB. A copy of a creature with an attack trigger must be declared as an attacker to trigger it. Tokens copied from a creature that entered this turn have haste from Rionya.
- Never count opponents' spells toward Rionya. Never assume casting a spell after the beginning-of-combat trigger increases an already-created batch.
- Save defensive interaction when required; do not spend protection/counters merely to inflate the copy count.
- Do not intentionally rely on an infinite loop.

## Every card: strategic role and decisions
| Card | Role | Pilot intent |
|---|---|---|
| Rionya, Fire Dancer | Commander/copy engine | Cast promptly; sequence spells before beginning of combat; select highest-impact other creature |
| Krark, the Thumbless | Spell copy/return | Cast early if it improves spell economy; account for coin-flip variance |
| Storm-Kiln Artist | Treasure per instant/sorcery | Develop early; copy when extra bodies/treasure synergies outweigh ETB damage |
| Firebrand Archer | Spell damage | Develop early; cast cheap spells while present |
| Guttersnipe | Spell damage | Prefer spells with multiple opponents alive |
| Professional Face-Breaker | Treasure/card advantage | Attack when profitable; don't sacrifice treasure needed for commander |
| Generator Servant | Fast mana/haste | Use to accelerate Rionya when legal; note haste only applies to creature spell paid with its mana |
| Scourge of Valkas | Dragon ETB damage | High-value Rionya copy target; compute Dragon count and target damage correctly |
| Fanatic of Mogis | Devotion ETB damage | High-value mass damage copy target; count red devotion accurately |
| Terror of the Peaks | Creature-entry damage | High-value payoff for every Rionya token; protect if possible |
| Humble Defector | Draw engine | Tap before copying when useful; copied tokens can tap for cards with haste; avoid gifting permanent unnecessarily |
| Coercive Recruiter | Creature theft on Pirate ETB | Copies of Recruiter are Pirates and can steal opposing creatures; leverage haste and attacks |
| Solemn Simulacrum | Ramp ETB | Copy for repeated basic land ramp; death-draw only when it actually dies |
| Devilish Valet | Entry-trigger power doubler | Tokens entering double Valet's power; copies of Valet are temporary attackers; verify Forge SVar Double |
| Witty Roastmaster | Creature-entry damage | High priority to have in play when token batch enters; copies multiply triggers |
| Treasonous Ogre | Life-for-red-mana | Spend life only for meaningful acceleration or winning lines; respect lethal risk |
| Runaway Steam-Kin | Red-spell mana engine | Build counters with red spells; use mana to cast commander or payoff |
| Skirk Prospector | Goblin sacrifice mana | Sacrifice only Goblins; prioritize useful acceleration |
| Goblin Chainwhirler | ETB board ping | Copy to clear small blockers/opponent creatures |
| Redoubled Stormsinger | Copy/spell payoff | Confirm actual Oracle/Forge behavior before assigning priority |
| Tectonic Giant | Attack trigger/value | Copies attack immediately if legal; choose draw or damage by board |
| Ardent Elementalist | Instant/sorcery recursion ETB | Copy to recur powerful spells; don't loop without a real payoff |
| Sol Ring | Fast mana | Cast early; accelerate Rionya |
| Arcane Signet | Colored mana | Cast early; fix red |
| Ruby Medallion | Red spell discount | Cast early before chains |
| Fellwar Stone | Mana rock | Check opponents' colors before relying on red |
| Chaos Warp | Flexible removal | Hold for threats, not trivial storm count |
| Wild Magic Surge | Removal | Hold for problematic permanents |
| Abrade | Creature/artifact removal | Choose mode for actual threat |
| Vandalblast | Artifact wipe | Prefer overload for valuable multi-target swing |
| Pyroblast | Blue interaction | Only use when legal/valuable |
| Red Elemental Blast | Blue interaction | Only use when legal/valuable |
| Tibalt's Trickery | Stack interaction | Use defensively with replacement risk |
| Bolt Bend | Redirect | Preserve for targeted interaction; discount when applicable |
| Untimely Malfunction | Flexible interaction | Choose legal high-impact mode |
| Return the Favor | Copy/redirect | Preserve for powerful stack interactions |
| Flare of Duplication | Copy instant/sorcery | Sacrifice red creature only for meaningful payoff |
| Brotherhood's End | Board/artifact sweeper | Choose mode based on board; avoid destroying own engine unnecessarily |
| Rite of Flame | Ritual | Use to cast Rionya early or assemble precombat spell chain |
| Pyretic Ritual | Ritual | Accelerate commander/turn |
| Desperate Ritual | Ritual | Accelerate commander/turn |
| Seething Song | Ritual | Strong commander acceleration; preserve when no useful payoff |
| Mana Geyser | Large ritual | Prefer when opponents have many tapped lands |
| Battle Hymn | Creature-count ritual | Use after token creation if still useful; before combat only if existing creature count supports it |
| Expedite | Cantrip/haste | Precombat spell count; grant haste to a useful creature |
| Crimson Wisps | Cantrip/haste | Precombat spell count; ensure target legality |
| Renegade Tactics | Cantrip/evasion | Target relevant blocker; precombat |
| Warlord's Fury | Cantrip/first strike | Precombat before attack |
| Ancestral Anger | Cantrip/pump | Precombat; select profitable attacker |
| Crash Through | Cantrip/trample | Precombat for lethal/evasion |
| Reckless Impulse | Impulse draw | Cast when mana remains to use exiled cards |
| Wrenn's Resolve | Impulse draw | Cast when mana remains to use exiled cards |
| Light Up the Stage | Impulse draw | Prefer spectacle when achievable |
| Faithless Looting | Draw/discard | Improve hand; use flashback appropriately |
| Big Score | Draw/treasure | Prefer on opponent end step unless needed for precombat spell count |
| Unexpected Windfall | Draw/treasure | Preserve mana and card to discard |
| Thrill of Possibility | Instant draw | Hold until needed; precombat if it fuels copying |
| Demand Answers | Draw | Choose discard/sacrifice cost deliberately |
| Past in Flames | Recursion/storm | Use with graveyard spells and mana to replay them |
| Underworld Breach | Graveyard spell recursion | Plan escape costs and mana; don't exile needed win pieces |
| Bonus Round | Spell doubling | Use on a turn with follow-up instants/sorceries; remember symmetrical effect |
| Reforge the Soul | Wheel | Refill depleted hand; miracle when appropriate |
| Wheel of Misfortune | Wheel | Choose number with life/survival and opponents in mind |
| Grapeshot | Storm finisher | Cast after other spells; count storm and allocate damage |
| War Room | Card draw land | Draw when mana and life allow |
| Spinerock Knoll | Hideaway land | Track damage condition and stored spell |
| Forgotten Cave | Cycling land | Cycle when flooded; play when mana-starved |
| Myriad Landscape | Ramp land | Activate with spare mana for basics |
| Scavenger Grounds | Graveyard hate land | Preserve until opposing graveyard threat; watch self-graveyard |
| Rogue's Passage | Evasion land | Use on high-impact attacker |
| Dwarven Ruins | Burst mana land | Sacrifice for critical turn, not automatically |
| Crystal Vein | Burst mana land | Preserve until acceleration is worthwhile |
| Mountain (28) | Basic red mana | Play land drops consistently; don't sacrifice mana development for marginal spells |

## Target priority depends on board state
- With Terror of the Peaks / Witty Roastmaster already on board: token creation can be a direct damage engine; estimate all simultaneous entry triggers.
- Copy Scourge of Valkas / Fanatic of Mogis for ETB damage, Coercive Recruiter for theft, Solemn for ramp, Ardent Elementalist for recursion.
- Copy Humble Defector for draw only if tapping and ownership rules make it profitable.
- Copy attack-trigger creatures only if they can attack and the triggers are worth more than ETB damage.
- Never blindly copy a mana dork or small attacker over an available lethal target.

## Mandatory future instrumentation
Log RIONYA_CAST_DECISION (mana, tax, legal, chosen/declined reason), PRECOMBAT_SPELL (name, count, mana remaining), RIONYA_TRIGGER (copy target, spell count, copies, damage estimate), CARD_DECISION (cast/hold reason), and failed target legality.
Separate Forge engine card-implementation errors from pilot errors. Verify each card's Forge support, including Devilish Valet and Redoubled Stormsinger.

## Acceptance
This file is a specification, not evidence of implemented behavior. Test in one isolated diagnostic game and audit decisions before any five-game series. Use a newly sampled fixed-for-series random pod when the user authorizes series testing.
