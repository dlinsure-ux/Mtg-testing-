# Kwia Vigorbloom compatibility investigation

Status: **unresolved; do not report the deck as validated.**

## Reproduced failure
GitHub Actions run 38668071238, single-game job, completed a 22-turn simulation but strict validation failed because Forge reported:
- `An unsupported card was requested: "Kwia Vigorbloom" from "null".`
- `An unsupported card was requested: "Kwia Vigorbloom" from "[N.A.]".`

## Source inspection
The pinned Forge fork at `edb28158f6d892f9eb43dc08f019aab567276027` has no `kwia_vigorbloom.txt` in its recursive repository tree under `forge-gui/res/cardsfolder`. This confirms missing card script in the pinned source, not merely a deck spelling typo.

## Required implementation
Add the official Reality Fracture card script with mana cost {3}{G}{W}{W}, legendary Elder Sphinx 6/6, flying, vigilance, lifelink, ward {2}, and a life-gained trigger **once each turn** that creates a named colorless Lotus artifact token. The Lotus must have '{T}, Sacrifice this token: Add three mana of any one color.' Confirm the token's exact Forge script and registration conventions before adding it. Do not silently approximate this ability or substitute another card.

## Release gate
1. Add Forge card and token definitions in an isolated, reversible build patch; preserve pinned source and historical baseline.
2. Prove card registration, trigger once per turn, distinct turns, token mana activation, and no duplicate tokens from multiple life-gain events.
3. Run single-game smoke test; require zero unsupported-card errors and strict validator PASS.
4. Only then resume multi-game testing. The canonical 100-card deck must remain unchanged.
