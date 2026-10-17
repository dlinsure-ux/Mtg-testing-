# Rionya telemetry acceptance contract

## Purpose
Game completion is not equivalent to a valid deck-performance audit. Keep gameplay validation and telemetry validation separate. Do not recommend deck cuts from incomplete evidence.

## Captured today
- Forge debug log: turn/phase, casts, land plays, Rionya trigger targets, and incomplete draw-event counts.
- AI decision log: commander candidate decisions, precombat decisions, and copy-target scores.
- AI decision-point snapshots: own hand card names, battlefield card names, life, and creature count, in main phases when the instrumented method is reached.
- Offline reports: per-game JSON, board timeline, distinct-turn card exposure, and completeness gate.

## Missing authoritative events
1. Opening seven and all London mulligan transitions, including cards returned to library.
2. Each draw with exact card identity, source, and turn, from a central card-zone transition rather than inferred hand deltas.
3. All zone movements, including discards, exile, graveyard, token creation/deletion and temporary copy cleanup.
4. Turn-start and turn-end snapshots independent of whether the AI spell-choice method is called.
5. Actual mana payments, remaining mana, card playability and explicit rejection reasons.
6. Combat damage and noncombat damage by source, life changes, counters and token identity.
7. Opponent public battlefield states and interaction outcomes.

## Why not instrument these blindly?
The pinned Forge 2.0.14 Java source is downloaded during the GitHub Actions build, not stored in this repository. Exact central-event hook signatures must be inspected and compile-tested against that source. Guessing an API would risk breaking the harness. Current AI snapshots are not a substitute.

## Acceptance gates before deck evaluation
- Verify Java build and unit tests on the pinned source.
- Verify actual child-JVM property and that both pilot and state markers appear in retained logs.
- Require nonempty opening-hand/mulligan records and named draw events.
- Require state snapshots at turn boundaries and coherent zone-transition counts.
- Require game-by-game source and timestamp/turn for all card-level conclusions.
- Record coverage metrics; fail closed when coverage is incomplete.
- No automatic game runs. The workflow must remain manual dispatch only.
- No deck cuts without distinguishing pilot mistakes, game-state constraints and deck construction.

## Next implementation step
Patch the actual Forge 2.0.14 game event and zone transition listeners in a reproducible source-specific script, with fixtures and a compile-only validation workflow. Do not infer identities from hidden or unobserved game events.
