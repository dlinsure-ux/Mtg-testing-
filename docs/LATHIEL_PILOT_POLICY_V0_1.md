# Lathiel pilot policy v0.1 — diagnosis and executable acceptance criteria

Status: SPECIFICATION ONLY. The current workflow uses Forge built-in AI, not a configurable custom pilot. This document does not alter decisions by itself. Do not claim pilot repaired until a runtime adapter implements these policies and a before/after regression verifies them.

## Verified baseline
Run 38590772642: five strictly valid games, Lathiel 0/5. Observed game 5: commander not cast; very low spell throughput. Individual legal actions, available mana, opening hands, tutor selections and missed triggers have NOT been verified. Avoid declaring any specific play a mistake without board/hand evidence.

## Priority rules for a future runtime adapter
1. Mulligan: use multiplayer London mulligans; first free, and repeated free mulligans for one-land hands per home rules. Never keep a one-land opening seven. Prefer 2-4 lands and at least one early accelerator or engine. Record opening hand and bottom choices.
2. Commander: cast Lathiel proactively when affordable and likely to survive or yield a productive end-step trigger; do not blindly jam into known removal or forgo necessary interaction. Track legal opportunities and reasons for deferral, including commander tax.
3. Lifegain: prioritize Soul Warden, Essence Warden, Suture Priest and lifelink enablers when they create repeatable triggers. Record life gained per turn and whether the end-step condition was met.
4. Counter targets: evaluate available targets for immediate damage, evasion (Abzan Falconer/Battle Priest), protection, and scaling (Kalonian Hydra/Managorger Hydra). Log legal targets and chosen target; do not assume empty target lists indicate missed choices.
5. Tutors: log each tutor's candidate set, selected target and reason. Prefer an immediate winning line or missing engine component, not generic mana fixing; respect mana, timing, and vulnerability. Heliod + Walking Ballista is only a lethal line if legal and sufficient resources are present.
6. Sequencing: deploy low-cost ramp and repeatable engines efficiently; preserve protection when a known threat exists; avoid passing with safe, high-value playable actions. Never equate low spell counts alone with AI error.
7. Combat: prioritize safe lifelink connections, meaningful counter growth, and lethal damage; record attack decisions, blocks and damage.
8. Card assessment: track drawn, cast, stranded, removed, and effective cards; do not call a card dead merely because it wasn't cast.

## Instrumentation required
Per turn: active player, phase, hand, lands/mana, available actions, spells cast, cards drawn by source, life changes, commander tax/casts, lifegain triggers, Lathiel trigger targets, tutor choices, combat and board state. Capture RNG seed where possible. Mark unavailable fields UNKNOWN, never zero.

## Regression gate
- Baseline and candidate pilot must run the same five-deck pod format with at least five independent validated games.
- Strict terminal winner agreement, zero unsupported cards, zero whole-game timeouts.
- Compare commander legal-cast opportunities vs actual casts; tutor decisions; lifegain end steps; cards stranded; draw sources; actionable passes.
- A 0/5 or 5/5 record alone is not proof of pilot quality.
- Keep original Forge AI as control. No pilot-fix claim until runtime source changes and telemetry demonstrate a changed decision.
