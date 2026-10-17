# Commander Forge Reliability Roadmap (2026-10-16)

## Immutable comparison baseline
- Successful run #133, SHA 3785d64422d14d5f4ce3f0b88a24be23196b92f8.
- Branch baseline/verified-five-game-upstream-fastpaths-3785d644. Do not rewrite.
- Five valid games, zero AI timeouts in that series only.

## Failures and confidence
- #134: first two games valid, third invalid with two AI decision timeouts. Same game code as #133; randomness or state may explain difference. Root cause NOT verified.
- #135 and #136: test harness failed before gameplay, seed file parent directory absent. NOT AI regressions.
- #137/#138: check final statuses and artifacts before making any conclusions.
- New changes after #138: preflight guard, helper for per-game directories and RNG seed, unit tests, shellcheck-safe export. Require a passing preflight AND five-game series before calling validated.

## Code locations
- scripts/prepare_commander_game.py: default fresh 63-bit cryptographic random seed, optional SEED_BASE + game for fixed-seed regression. Writes replay-seed.txt and replay-metadata.json before launching.
- scripts/patch_forge_replay_seed.py: opt-in Forge MyRandom initial provider and Commander AI Lab subprocess system property. Outside explicit seed, Forge uses SecureRandom.
- scripts/check_commander_workflow.py: static workflow invariants; tests/test_commander_workflow.py, tests/test_prepare_commander_game.py.
- .github/workflows/commander-ai-lab-build.yml: game loop, JFR and jcmd captures, strict validator.

## Required follow-up, in order
1. Confirm latest preflight passes including actionlint and unit tests; fix only reproducible failures.
2. Confirm five-game harness runs; do not conflate infrastructure failures with AI decision timeouts.
3. Run one fixed seed twice at identical source SHAs, compare turn/action/winner and decision sequences. A single MyRandom seed is NOT proof of determinism if other RNG sources or concurrent scheduling exist.
4. Collect per-decision timings and JFR/thread dumps from the exact slow decision. Profile expensive methods before modifying combat AI.
5. Fix targeted hot path without relaxing five-second timeout, changing legality, or weakening validator. Run saved regression seeds plus fresh RNG series.
6. Implement Lathiel pilot adapter ONLY after reliability stabilizes. pilots/lathiel/v0.1.json is specification_only, runtime_integrated=false; never claim JSON changes alter play.
7. Require 100 fresh-RNG games with zero decision timeouts and zero invalid outcomes plus replay regression suite. Keep failed-game artifacts.

## Safety
- Do not merge PR #1.
- Preserve original 99-card deck + separate commander, true RNG for normal tests, 5-second AI budget, and explicit VOID only for crash/deadlock.
- Avoid claims of completion based on passing preflight alone.
