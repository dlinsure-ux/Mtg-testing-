# Forge AI performance: evidence-backed long-term plan (2026-10-15)

## Evidence inspected

- GitHub Actions run 38821663401, artifact 12012687900: extracted actual `forge/forge-sim-debug.log`, `diagnostics/game-1/profile-3344.jfr`, `thread-2-3344.txt`, validation report and JFR-start log.
- Build and Lab process succeeded; game ended but validator correctly classified INVALID: three AI decision timeouts (Shroofus turn 28 COMBAT_BEGIN; Winota turn 31 COMBAT_BEGIN; Shroofus turn 32 COMBAT_BEGIN).
- The 85 `FORGE_BLOCK_PROFILE` records at >=100ms sum to 52,951ms of **cumulative invocation time**, max 2,893ms; this is not wall-clock or CPU time and may overlap. Three slow `FORGE_COMBAT_PROFILE` records sum to 1,995ms.
- JFR duration 112 seconds, 1,536 execution samples: `Card.isValid` on 1,208 sample stacks; `Card.hasProperty` 1,177; `CardProperty.cardHasProperty` 1,172; `CardPredicates.lambda$restriction$20` 1,032. Nested frames are not independent samples. 1,328 samples came from `pool-3-thread-1`, 207 from `Game AI Eval`, and 1 from JFR tasks; therefore **do not attribute all samples to AI decision time**.
- Timeout stack 1: `CardState.updateSpellAbilities -> ComputerUtilCombat.predictToughnessBonusOfAttacker -> canDestroyBlocker`. Timeout stack 2: `Card.updateStaticAbilities -> CostAdjustment.adjust -> CostPayment.canPayAdditionalCosts -> ComputerUtilMana.groupSourcesByManaColor -> payManaCost`. Timeout stack 3: `ComputerUtilCombat.canDestroyBlockerBeforeFirstStrike -> AiBlockController.makeGoodBlocks -> ComputerUtil.predictNextCombatsRemainingLife -> AiAttackController.notNeededAsBlockers`.
- JFR was started successfully on the real Forge JVM (PID 3344); thread snapshot shows `Game AI Eval` in `StaticAbilityMustTarget.getAllRestrictions` via `ChangeZoneAi`. This is a separate sample and does not prove a deadlock.
- Earlier runs 38806749643 (10 timeouts) and 38821380104 (4 timeouts) are distinct randomized games, not a controlled before/after comparison.

## Working diagnosis

The principal bottleneck is repeated speculative combat/legality evaluation interacting with expensive card-validity, static ability, replacement-effect and mana checks. The stacks implicate *multiple* paths, not one infinite loop. Nested attacker/blocker prediction is a plausible multiplier. The JFR hotspot is real but requires thread-specific attribution and a controlled workload before modifying `Card.isValid` or caching results. Rules correctness is paramount: game state, zones, timestamps, continuous effects, and replacement effects can invalidate naive caches.

## Long-term stages and exit criteria

### Stage 0 — Preserve and reproduce
1. Preserve diagnostic artifacts before October 29 expiry; retain exact pinned Forge/Lab/harness SHAs, deck checksums, JVM options, logs and JFR settings.
2. Find whether pinned Forge/Lab exposes a reproducible RNG seed or serialized game-state replay. Verify it in code; do not assume.
3. Until deterministic replay exists, compare paired repeated pods with confidence intervals and per-decision distributions; do not claim a fix from fewer timeouts in a single random game.
**Exit:** reproducible or statistically comparable performance baseline with known board complexity.

### Stage 1 — Observability with minimal perturbation
1. Record per-method invocation counts, total inclusive time, max, p50/p95/p99 and caller, per decision; distinguish nested-inclusive times from exclusive CPU.
2. Instrument `AiAttackController.notNeededAsBlockers`, `ComputerUtil.predictNextCombatsRemainingLife`, `AiBlockController.makeGoodBlocks`, `ComputerUtilCombat.canDestroyBlocker`, `ComputerUtilMana.groupSourcesByManaColor`, and relevant static/replacement queries.
3. Include active-player seat, phase, board creatures/permanents, number of candidate actions and JVM GC time. Emit bounded per-decision summaries, not hot-loop logging.
4. Capture JFR from startup, filter by thread and stack, and verify real JVM PID. Correlate each timeout context to trace/profile.
**Exit:** at least 90% of expensive decisions attributable to measured call chains; no noticeable instrumentation-induced slowdown.

### Stage 2 — Isolate root cause without changing decisions
1. Compare caller frequency vs individual call latency and distinguish card-validation overhead from repeated unnecessary evaluation.
2. Review and test existing aliasing change (`new ArrayList<>(attackers)` removal) and no-creature fast path; only retain after decision-equivalence checks.
3. Extract inline Forge patches into pinned, fail-closed, idempotent scripts with unit tests. Fix workflow trigger/caching behavior and separate preflight from expensive gameplay.
4. Strengthen timeout log de-duplication, terminal winner/loser reconciliation and watchdog PID/progress checks; retain fail-closed rules.
**Exit:** all existing tests plus source-level equivalence tests pass, reproducible patch application.

### Stage 3 — Targeted optimization
1. First optimize redundant **repeated** combat simulations (per-decision reuse with explicit invalidation boundaries); never cache across state changes without a proven state key.
2. If profiling still identifies card validity or mana as dominant, optimize allocations and redundant queries only after profiling the specific caller and proving identical legal decisions.
3. Consider algorithmic blocker-search pruning only if mathematically equivalent; do not use arbitrary board-size cutoffs, skip legal candidates or extend the five-second budget.
**Exit:** measurable p95/p99 decision-time improvement on matched workloads and zero decision/legality divergences.

### Stage 4 — Progressive gameplay verification
1. Offline Python/actionlint, Java compile, patch-idempotence tests and deterministic fixtures.
2. One real four-player game, then fixed five-game pod, then 10/20-game random pool.
3. Require zero in-game AI decision timeouts, one consistent terminal winner, no unsupported cards, no silent hangs, preserved raw logs and verified rules behavior.
4. Record every run ID, commit, exact outcome and unresolved defect in the handoff and README. Do not merge PR #1 or touch protected baseline/canonical decks without explicit approval.

## Immediate next engineering task

Implement **per-decision bounded aggregated timing** for the identified nested combat and mana call chains, then rerun a pinned one-game diagnostic with JFR starting at JVM launch. Analyze by thread and caller. Do not optimize `Card.isValid` blindly; JFR includes non-AI worker threads. This document is a diagnosis and plan, not a claim that the underlying issue is fixed.

## Execution checkpoint — offline CI and player-attributed slow calls

- New independent GitHub Actions workflow: `.github/workflows/forge-offline-diagnostics.yml`, commits `41efa05c` and `b26570fb`. It compiles/tests the Python diagnostics and validator without Maven builds or game simulation. Its run ID is published to issue #2; no pass is claimed until job logs confirm it.
- Re-examined raw run #116 Forge debug log: 85 `FORGE_BLOCK_PROFILE` calls >=100ms. Counts by player: Shroofus 42, Winota 33, Lathiel 9, Deadpool 1. Shroofus and Winota account for 75/85 (88.2%) of logged slow calls, and all three decision timeouts in that run.
- Logged slow calls by attacker count: 12 attackers 26 calls; 4 attackers 23; 1 attacker 22; all others 14. The expensive path is not limited to large attacker sets. The slow-call p95 is approximately 2,402ms; **this percentile is conditional on crossing the logging threshold**, not a percentile of all evaluations.
- Next action: validate independent CI, then instrument bounded per-decision totals and per-caller counts, keeping the five-second budget and all gameplay decisions unchanged. Avoid interpreting the 52,951ms inclusive sum as wall time.

### Verified lightweight CI result

GitHub Actions [run 38827290968](https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38827290968), job 116737605885, **completed SUCCESS**. The step `Compile and test without building Forge` passed, including profiling parser, timeout ordering, validator pattern and winner parser unit tests. This validates offline diagnostics only; it does **not** validate a gameplay performance fix or any new Forge runtime instrumentation.

## Runtime instrumentation checkpoint — 2026-10-15

- Extracted the existing `AiBlockController.makeGoodBlocks` source wrapper into versioned, fail-closed `scripts/patch_forge_block_aggregate.py` (commit `e31bda26`), retaining the original 100ms slow-call log and adding thread-safe counters for **all completed blocker-search calls**, inclusive total time and maximum time. One aggregate log line is emitted per 32 calls; no AI choice, rule or timeout budget was changed.
- Added patch regression tests `tests/test_patch_forge_block_aggregate.py` (commit `c9246d3`) and wired them into isolated offline CI (commit `1f5c58e`). **Verified passing:** [run 38830803455](https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38830803455), job 116748884473.
- Replaced the old inline blocker profiling patch in the full-game workflow with the extracted script (commit `01416278`). This triggered [full diagnostic run 38830812217](https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38830812217). At documentation time, it had started; **no Forge build, game or runtime instrumentation success is claimed yet**.
- Limitations: counters aggregate across players and threads, not yet per AI decision; completed calls only; inclusive timings may overlap; sampling every 32 calls means short runs can have no aggregate line. These must be addressed before declaring root-cause isolation.
- Next: check run #117 build, validation and artifacts; parse `FORGE_BLOCK_AGGREGATE` records; add bounded per-decision/per-player accounting and caller correlation, then target the highest redundant call chain with rules-equivalence tests.

## Run #117 verified outcome

[GitHub Actions run 38830812217](https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38830812217), job 116748984111: preflight SUCCESS; Forge and Lab Maven BUILD SUCCESS. Game 1 VALID, final_turn=12, 612 action markers, 0 AI timeouts; game 2 VALID, final_turn=17, 718 action markers, 0 AI timeouts; game 3 INVALID, final_turn=11, 563 action markers, 1 AI decision timeout (Shroofus, Forge turn=19, COMBAT_BEGIN, 5-second budget, 10 candidates). Games 4–5 not attempted; job failed correctly at game 3. The 3-game evidence artifact is 12016739986 (27,480,134 bytes). It was downloaded to the conversation runtime as `github-actions-artifact-12016739986.zip`; do not assume the artifact is permanently archived in GitHub.

The new blocker aggregate instrumentation compiled, but the job log does not expose aggregate log entries directly because Forge diagnostics are in the artifact. Next: extract and analyze game-specific `forge-sim-debug.log` from artifact; compare all-call aggregate with slow-call counts, especially Shroofus game 3. Do not infer optimization from two valid randomized games.

## Run #117 artifact deep analysis — verified

Extracted artifact `12016739986` from GitHub run `38830812217` and inspected `results/game-{1,2,3}/forge-sim-debug.log`, `results/version-manifest.json`, and `diagnostics/game-3/profile-6070.jfr` using the JDK `jfr` tool.

| Metric | Game 1 (VALID) | Game 2 (VALID) | Game 3 (INVALID) |
|---|---:|---:|---:|
| Last reported all-call blocker count | 288 | 512 | 416 |
| Last reported cumulative blocker time (ms) | 9120 | 3695 | 3546 |
| Logged blocker calls >=100ms | 41 | 0 | 3 |
| AI decision timeouts | 0 | 0 | 1 |

The all-call counts and cumulative timings are **last periodic snapshots**, not complete-game totals; calls after the last multiple of 32 are omitted. Cumulative nested times are not wall time. The three slow game-3 blocker calls were Shroofus (887ms with 13 attackers, 793ms with 12, 535ms with 13). The latest aggregate max=60ms can appear lower than those slow calls because the slow calls occurred **after** the last 32-call aggregate snapshot, and/or profiling logs were buffered; do not treat snapshots as synchronized per-decision data.

**Actual game-3 timeout stack:** `PumpAiBase.getPumpCreatures -> ComputerUtilCard.shouldPumpCard -> doesSpecifiedCreatureAttackAI -> AiAttackController.declareAttackers -> notNeededAsBlockers -> ComputerUtil.predictNextCombatsRemainingLife -> AiBlockController.makeGoodBlocks -> ComputerUtilCombat.canDestroyBlocker -> predictPowerBonusOfBlocker -> dealsFirstStrikeDamage -> canGainKeyword -> ComputerUtilCost.canPayCost -> ComputerUtilMana.canPayManaCost -> payManaCost -> chooseManaAbility -> CostPayment.canPayAdditionalCosts -> CostAdjustment.adjust -> Card.updateStaticAbilities`. This establishes a nested speculative combat / mana / static-ability path, **not** that mana is independently the dominant cost.

**JFR:** `profile-6070.jfr` was a short 5-second recording (224 execution samples: 212 from `pool-3-thread-1`, 12 from `Game AI Eval`). `Card.isValid` appeared on 206 stacks, `Card.hasProperty` and `CardProperty.cardHasProperty` on 195 each; none of these percentages should be attributed entirely to AI evaluation because most samples were from the simulation worker thread. The JFR did not capture enough targeted decision-specific data to justify a rules-affecting optimization.

**Version manifest:** Forge pinned SHA `edb28158f6d892f9eb43dc08f019aab567276027`, Lab SHA `54cc5e9b2e9ae6266c64b5cdf11f3302f311c701`, harness SHA `014162784fba2d7e021704ff5db94b990b948720`, `runtime_pilot_integrated=false`. **Do not assume the custom Lathiel pilot was running.**

**Next engineering action:** add *per-decision* bounded counts/timing and a completion/timeout snapshot (not every 32 calls only), scoped to actual AI decision context, for hypothetical combat and cost/static-ability checks. Include caller identifiers and preserve decisions. Validate instrumented source compiles and measure the overhead. Only then attempt narrowly scoped elimination of redundant repeated evaluations with legality-equivalence tests.

## Per-decision instrumentation implementation — 2026-10-15

Implemented new Java diagnostic helper `scripts/forge/ForgeDecisionProfile.java` (commit `96a26047`), which uses thread-local decision scopes and atomic blocker-call counters. The scope is started inside pinned Forge's `FutureTask<SpellAbility>` worker and logged in `finally`, including when the worker unwinds due to timeout. The block patch now records each completed `makeGoodBlocks` invocation into the current worker scope (commit `4e7cf854`). The report contains elapsed time, blocker call count, cumulative inclusive blocker time and max blocker time. This is **decision-scoped for that FutureTask path only**; it does not yet cover every AI callback or every cost/static-ability call. No legality or decision policy changes were made by these new probes.

Fail-closed `scripts/patch_forge_decision_profile.py` was added and simplified (commits `6e4c26bf`, `44e4524c`), with offline regression tests `tests/test_patch_forge_decision_profile.py` (commit `ade1d02b`). Workflow now copies the Java helper into pinned Forge and patches the worker before compilation (commit `ba9006d4`). Offline CI was updated (commit `2d1d5167`).

**Verification status at writing:** earlier isolated diagnostics run [38831692117](https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38831692117) passed, but predates the new worker-patch tests. Full-game [run #118, 38831710776](https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38831710776) passed preflight and had queued the Forge build; **Java compilation and gameplay outcomes are not yet verified**. Check the current workflow runs and logs before making any claim. The worker `finally` snapshot may not execute if forcibly terminated in an unusual JVM state, and completed blocker calls do not include a currently stuck in-flight call.

Next: verify run #118 builds and reports `FORGE_DECISION_PROFILE`, analyze expensive decision scopes and correlate timeout stack, then instrument the specific nested mana/static caller before optimizing. Keep five-second budget unchanged.
