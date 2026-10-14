# Forge simulation release gates

## Verified recovery baseline
- Branch: `baseline/verified-five-game-210e5db`
- Commit: `210e5db24ae4c920a7c1948a5bd2ed74432623de`
- Successful GitHub Actions run: `38590772642`
- Preserve this branch. It is a historical recovery point, **not** proof that later modifications pass.

## Gates for future changes
1. Work on an experiment branch; do not edit the baseline.
2. Static lint and Python regression tests must pass.
3. Verify exact Forge, Lab, harness, pilot, deck SHA256 hashes in run artifacts.
4. Run one game; require Lab and Forge to agree on exactly one terminal winner, no in-game timeouts and no unsupported cards.
5. Only then run the full five-game pod; any invalid game fails the series.
6. Compare decision telemetry with the golden pilot cases before declaring a pilot improvement.
7. If a gate fails, stop and return to baseline; never relabel an invalid game as valid.

## Not yet enforced
GitHub branch protection requires repository settings/API permissions not available to this connector. The pilot's Forge runtime adapter and decision hooks do not yet exist. Golden cases are **specification checks only** until those hooks are instrumented. Do not claim these gates are fully enforced.
