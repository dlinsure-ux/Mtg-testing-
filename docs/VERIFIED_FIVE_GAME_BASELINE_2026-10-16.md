# Verified five-game baseline — 2026-10-16

## Result
GitHub Actions run [#133](https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38970600356), run ID 38970600356, succeeded on commit `3785d64422d14d5f4ce3f0b88a24be23196b92f8`.

| Game | Final turn | Actions | Startup AI timeouts | In-game AI timeouts | Validation |
|---|---:|---:|---:|---:|---|
| 1 | 16 | 849 | 0 | 0 | VALID |
| 2 | 16 | 935 | 0 | 0 | VALID |
| 3 | 15 | 621 | 0 | 0 | VALID |
| 4 | 18 | 791 | 0 | 0 | VALID |
| 5 | 17 | 907 | 0 | 0 | VALID |

All five reached terminal results. Exact frozen baseline branch: `baseline/verified-five-game-upstream-fastpaths-3785d644`.

## Configuration
- Forge fork pinned to `KoalaTrapLord/Forge-Repo@edb28158f6d892f9eb43dc08f019aab567276027`.
- Commander AI Lab pinned to `KoalaTrapLord/commander-ai-lab@54cc5e9b2e9ae6266c64b5cdf11f3302f311c701`.
- Existing experimental combat and diagnostic patches remain in effect, with five-second AI decision budget.
- Lab Forge subprocess uses full tiered JIT level 4 and directly sets `-Dforge.ai.speculativeCombatGuard=true`.
- Backported upstream [Forge PR #11933](https://github.com/Card-Forge/forge/pull/11933) via `scripts/backport_forge_pr11933.py`.
- Backported upstream [Forge PR #11964](https://github.com/Card-Forge/forge/pull/11964) via `scripts/backport_forge_pr11964.py`.
- The workflow runs both backports before Forge compilation.

## Limitations and follow-up
This is a validated five-game series, not proof of zero timeouts across all seeds or opponent pods. Run #132, which included #11933 but not #11964, failed its first game with five timeouts; these were different random games, so the result does not prove #11964 alone caused the improvement.

1. Keep this baseline untouched. Do not merge PR #1.
2. Run repeated five-game series with fixed pods and additional random seeds; preserve strict validation.
3. Compare game-state and decision-level diagnostics for #132 versus #133 and isolate which changes account for improvement.
4. Evaluate upstream #11975 and #11981 separately only after further validation, as they alter data structures or combat behavior.
5. Continue collecting draw, tutor-target, dead-card, and pilot telemetry. Do not relax the five-second watchdog to manufacture success.
