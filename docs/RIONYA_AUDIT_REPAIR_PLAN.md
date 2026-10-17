# Rionya audit repair plan

Run #164 completed a valid Forge game (790 actions, turn 20, zero AI timeouts), but postgame audit failed with a Python SyntaxError. Subsequent commits repaired malformed newline escapes and added a preflight Python syntax check; rerun verification is required.

Architecture references: Forge AI documentation (Card-Forge/forge docs/AI.md); KoalaTrapLord/commander-ai-lab issue 83 (decision snapshots); LlamaAdam/commander-builder docs/architecture.md (runner/parser/analyzer separation). These are design references, not verified compatible code.

1. Make gameplay validation independent of analytics failures; preserve all game artifacts even when reporting fails.
2. Add offline fixtures for four-player turn numbering, card draws including Monarch, spells cast, lands, and Rionya copy targets. Report unknown draw identities honestly.
3. Confirm the Rionya opt-in flag inside the Forge child JVM and emit markers for decision branch reached, candidate accepted/rejected, and final copy target. Verify output capture.
4. Prefer structured JSONL events from Forge's game event and zone-change APIs instead of guessing from text logs. Record turn, seat, phase, card identity, zone, action, target, hand and mana snapshots, and outcome. Preserve privacy of opponents' hidden cards.
5. Aggregate cards drawn, played, stranded, copied, and damage contribution with sample sizes; compare identical deck versions against fixed opponents.
6. Test one game end-to-end before five-game fixed-pod testing. Keep the 100-card deck unchanged while repairing telemetry.

Acceptance: preflight passes; complete valid game; reliable turn attribution; honest draw completeness; pilot decision marker verified or explicitly unverified; artifact preserved even if analytics fails.