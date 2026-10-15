# Forge / Commander AI Lab — 2026-10-15 recovery and cleanup handoff

> **START HERE in a new ChatGPT conversation.** This is an evidence-based handoff, not a claim that the performance defect has been repaired. Also read the repository root [README](../README.md), the actual [workflow](../.github/workflows/commander-ai-lab-build.yml), and the latest GitHub Actions logs before editing anything.
>
> **User's current instruction:** Resume a comprehensive cleanup of the experimental test harness and targeted troubleshooting of Forge AI timeouts. Preserve the canonical decks and protected baseline. Do the work in GitHub; avoid asking the user to repeat previous troubleshooting.

## 1. Canonical repository, branches, source pins and invariants

- GitHub: https://github.com/dlinsure-ux/Mtg-testing-
- **Working branch:** `experiment/guardrails-watchdog-v1`
- **Protected baseline branch:** `baseline/verified-five-game-210e5db`, SHA `210e5db24ae4c920a7c1948a5bd2ed74432623de`. **Do not merge, force-push, reset, or modify this baseline without explicit user approval.**
- PR #1 is the experimental work. Do **not** merge it without explicit approval.
- **Do not edit `decks/lathiel.dck` or any canonical deck to work around a performance bug without explicit permission.**
- Workflow clones Forge from `KoalaTrapLord/Forge-Repo` pinned at `edb28158f6d892f9eb43dc08f019aab567276027`, and Commander AI Lab from `KoalaTrapLord/commander-ai-lab` pinned at `54cc5e9b2e9ae6266c64b5cdf11f3302f311c701`. Verify actual workflow pins before future builds.
- The Forge build uses Java 17 and `-Drevision=2.0.12-SNAPSHOT`; distinguish this build coordinate from other Forge release/version labels.
- Keep exact rules-engine results and strict game validation. **Never increase the 5-second AI decision budget or relax the validator to conceal failures.** A game reaching a winner is not a valid test if any in-game AI timeout occurred.
- User's intended testing: Forge rules-accurate four-player Commander, five-game fixed-pod series then larger/random-pool series, true RNG, per-card telemetry, and reliable pilot behavior. **Do not assume custom mulligan/tutor/pilot policies are integrated merely because JSON specifications exist.**
- No unrequested changes to the protected branch or to canonical decks.

## 2. Verified current source state and last runs

At the time this handoff was written, the experimental branch head **before the documentation-only handoff commits** was `4133dba4bc0ed3bb0bd277d299aa2a1f88f3a525`. The handoff commits will advance the branch head; check the latest commit before writing. No subsequent performance fix is implied.

Relevant sequence:
1. `7cc95721` introduced a 12-creature cutoff to speculative pump combat. Run [#38802357408](https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38802357408): game 1 VALID, 745 actions, terminal turn 13, 0 timeouts; game 2 INVALID, 667 actions, turn 14, **15 timeouts**. That heuristic was subsequently removed.
2. `fc6442e5` removed the arbitrary 12-creature cutoff.
3. `818d5c5544d73d1574de31855b377e9cb9bf2076` replaced repeated speculative attack checks in `ComputerUtilCard` with a **per-invocation** memoizing `BooleanSupplier`. Run [#38803876704](https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38803876704): INVALID, 822 actions, terminal turn 17, **1 in-game timeout**, Lathiel at logged turn 33, `COMBAT_BEGIN`, 8 candidates.
4. `28ce2cca1ae5a55ee76980d89a23031425e9fd8b` added slow-call profiling of `ComputerUtilCard.doesSpecifiedCreatureAttackAI` (logs calls >=250 ms). Run [#38805280324](https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38805280324): INVALID, 531 actions, turn 11, **2 timeouts**: Shroofus MAIN1 turn 17 and Winota COMBAT_BEGIN turn 22. Previous investigation found hypothetical attack evaluations around 3,958 ms (Théoden), 3,973 ms (Mayhem Patrol), and 1,167 ms (Elvish Mystic). Recheck archived log before relying on precise per-card timing.
5. `4133dba4bc0ed3bb0bd277d299aa2a1f88f3a525` added profiling of `AiBlockController.makeGoodBlocks` (logs calls >=100 ms). Run [#38806749643](https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38806749643): INVALID, **10 AI timeouts**, 741 actions, terminal turn 15. Shroofus 6 timeouts (upkeep/main/draw/combat), Winota 4 (upkeep/draw/main/combat), Lathiel 0. All ten timeout contexts were counted as in-game by strict validation. This run's evidence artifact is `lathiel-revised-single-game-evidence`, artifact ID **12006638829**, ~29.2 MB; artifacts have **14-day retention**. Previous audit reported **121 slow blocker calls** (>=100 ms), **9 slow hypothetical attacks** (>=250 ms), max `makeGoodBlocks` ~**1,737 ms**, max hypothetical attack ~**3,005 ms**. Treat these as profiling observations, not proof that instrumentation caused the extra timeouts; game states differ across runs.
6. Run #38806749643 workflow preflight and Forge compilation passed. The failed job step was **Run single diagnostic Forge game**, due to strict validation, not Java compilation.

**Do not sum `startup_ai_decision_timeouts` and `in_game_ai_decision_timeouts`** in the current validator: log buffering can cause the same event to appear in both classifications. Prefer unique `AI_DECISION_TIMEOUT` context events keyed by game/turn/player/phase and actual stack evidence.

## 3. Current root-cause diagnosis — separate observation from hypothesis

**Confirmed:** Forge's AI sometimes spends several seconds on a *single* hypothetical attack or blocker calculation, exceeding the 5-second decision budget. Nested combat prediction is on the timeout stacks, often via `PumpAiBase` → `ComputerUtilCard.shouldPumpCard` → `AiAttackController.declareAttackers` → `ComputerUtil.predictNextCombatsRemainingLife` → `AiBlockController` / `makeGoodBlocks`.

**Additional stack hotspots:** `CreatureEvaluator.evaluateCreature`, `Card.canUntap`, `ReplacementHandler.getReplacementList`, `Game.forEachCardInGame`, trigger/toughness evaluation, and `ComputerUtilMana.payManaCost` / `groupSourcesByManaColor`. These may be costly repeated suboperations during AI decisions, but a thread stack snapshot alone **does not measure cumulative time** or establish causality. Shroofus's timeout snapshots also involve mana/replacement-effect calculations. Some expensive blocker evaluations involved only one attacker, so number of attackers alone is not an adequate explanation. No evidence proves that unrecognized cards caused these recent AI timeouts.

**Important nuance:** 1, 2, 10, and 15 timeouts occurred in different game states. Do not claim monotonic improvement or regression from timeout counts alone. Need deterministic/replayable workload before judging optimizations.

## 4. Public/upstream references and competing Magic AI approaches

Review these issues and upstream code rather than assuming a ready-made fix:
- https://github.com/Card-Forge/forge/issues/7625 — Commander attacker selection hangs; attacker prediction → blocker assignment stack.
- https://github.com/Card-Forge/forge/issues/11810 — extremely slow blocker AI on large token board.
- https://github.com/Card-Forge/forge/issues/6726 — slowdown with many permanents.
- https://github.com/Card-Forge/forge/issues/3410 — progressive complex-evaluation slowdown (verify current issue text).
- https://github.com/Card-Forge/forge/issues/10150 — reported AI stalls in complex Commander (verify details).
- https://github.com/KoalaTrapLord/commander-ai-lab — actual Lab integration and docs.
- https://github.com/KoalaTrapLord/commander-ai-lab/issues/83 — Forge integration and rules-fidelity concerns.
- Forge's official AI docs/source and related tests; search upstream commits/PRs for fixes to `AiBlockController`, `AiAttackController`, `ComputerUtilCard`, replacement-effect scanning, and `CreatureEvaluator`.

**Other AI platforms:** Commander AI Lab's simplified Python/Monte Carlo simulation is faster but is **not** a rules-equivalent substitute for full Forge Commander games; it omits complex stack/priority/trigger mechanics. Other Magic AI platforms can inform heuristic/profiling design but must not silently replace the verified Forge rules engine. Cross-check exact current project capabilities before making claims.

## 5. Code audit — verified technical debt and suspected correctness risks

The current `.github/workflows/commander-ai-lab-build.yml` is about **663 lines**, with approximately **14 inline `python3 - <<'PY'` source-patch blocks**. It simultaneously patches Java source, compiles, caches, launches games, supervises processes, validates, uploads artifacts, and reports to an issue. This is operational but fragile; refactor into versioned patch files and scripts with unit/integration tests, preserving exact behavior.

Audit findings:
- **High priority — potential aliasing regression:** the workflow replaces `final List<Card> attackerList = new ArrayList<>(attackers);` with `final List<Card> attackerList = attackers;` in `AiAttackController.getCardCanBlockAnAttacker`. The inspected method currently only iterates; no actual mutation bug established. The tiny allocation savings are unproven relative to seconds-long bottlenecks; benchmark and consider reverting.
- **High priority — unproven semantic fast path:** `ComputerUtilCard` skips hypothetical blocker AI when no opponents currently control creatures. Verify method's return semantics and future/created blockers and effects. Add equivalence tests or revert.
- **Medium — caching:** compiled build cache key hashes the entire workflow YAML plus `forge-overrides/**`. Any workflow edit causes a costly rebuild. Split code/dependency hash from orchestration changes, while preserving correct invalidation for source patch scripts, pinned upstream SHAs and compiler inputs. Do not reuse stale compiled binaries.
- **Medium — default dispatch vs push:** workflow dispatch defaults to 5 games, while workflow file push auto-triggers. Diagnostic edits may inadvertently run expensive tests. Add explicit fast preflight/single-game vs intentional multi-game controls.
- **Medium — pilot spec is not runtime integration:** version manifest explicitly contains `runtime_pilot_integrated: false`. Do not claim Lathiel pilot/tutor/mulligan rules are active without tracing actual Lab/Forge calls.
- **Medium — validator parsing:** `scripts/validate_forge_game.py` is ~78 lines, uses regexes against Lab/Forge log formats; `scripts/game_progress_watchdog.py` is ~49 lines, polls action/turn markers. Preserve fail-closed behavior but add unit fixtures and unique timeout event accounting. Do not interpret a watchdog 'possible_stall' as deadlock.
- **Potential duplicated/overlapping artifacts or steps:** inspect workflow's exact YAML structure and validate for accidental duplication; do not infer from search snippets.
- **Known unsupported cards:** `scripts/preflight_selected_decks.py` flags `Smaug, Wicked Worm` and `Miss Highwater`. Older runs included unsupported Smaug; the recent timeouts are **not established** as unsupported-card failures. Do not edit opponent deck lists to suppress evidence.
- **Evidence retention:** artifacts expire after 14 days; preserve critical logs and manifests before expiry.

## 6. Targeted repair plan — next chat should act, not restart research from zero

1. **Read this document, README, workflow and scripts; check current branch head and recent run status.** Make a short inventory of exact modifications and SHA pins. Keep PR #1 open and baseline untouched.
2. **Secure evidence** from run #38806749643 and earlier runs if available: timeout stack frames, `FORGE_BLOCK_PROFILE`, `FORGE_COMBAT_PROFILE`, per-game validation JSON, JFR/thread dumps, exact source/deck SHA manifests. GitHub artifacts expire. If logs are inaccessible, explicitly state the gap.
3. **Profile with aggregation rather than only thresholds:** measure invocation count, cumulative elapsed time, max, p95 and board complexity for `makeGoodBlocks`, attacker prediction, replacement effect lookup, and mana evaluation. Avoid noisy logging in hot loops; aggregate per game or periodically. JFR is available in the workflow; confirm real Java PID and actual JFR files.
4. **Reproduce a representative expensive state.** Determine whether Forge/Lab supports a deterministic seed or state snapshot; do not pretend one exists. Add a repeatable benchmark/fixture and compare baseline vs experimental builds on the same workload. Include legality and AI decision equivalence assertions.
5. **Refactor source patches** from inline YAML to tracked `scripts/patch_forge.py` and/or `forge-patches/*.patch`, with upstream SHA checks, exact-count assertions, idempotence tests, and patch-specific regression tests. Do this in small commits, keeping existing working test harness intact.
6. **Audit/rollback low-value or unverified shortcuts**, especially the removed defensive copy and no-blocker fast path, only after testing. Avoid heuristic cutoffs that make AI dumber. Memoization must not outlive an unchanged game state.
7. **Fix cache keys and workflow separation** after patch scripts are stable; ensure a source change always invalidates compiled Forge and Lab caches.
8. **Improve validator/watchdog tests**, unique timeout accounting, PID identity validation, and clear classifications for AI decision timeout vs slow game vs deadlock vs external interruption. Preserve original logs and fail-closed results.
9. **Run staged verification:** Python/unit/actionlint; Java compilation; focused benchmark; one real four-player game; five-game fixed pod; 10/20-game random series when reliable. Require **zero AI decision timeouts, one consistent terminal winner, no unsupported-card warnings, and complete logs** for VALID.
10. **Update this handoff and root README** with each verified commit, run ID, actual outcome, regression and next steps. Do not claim fixed until repeated clean games.

## 7. How to resume efficiently in a fresh chat

Paste this exact request:

> Continue the Forge / Commander AI Lab troubleshooting and cleanup from GitHub repository `dlinsure-ux/Mtg-testing-`, branch `experiment/guardrails-watchdog-v1`. **First read `README.md` and `docs/2026-10-15-FORGE-PERFORMANCE-AND-CODE-CLEANUP-HANDOFF.md` on that branch**, then inspect `.github/workflows/commander-ai-lab-build.yml`, `scripts/validate_forge_game.py`, `scripts/game_progress_watchdog.py`, and run #38806749643 evidence. Follow the documented staged plan: preserve evidence, clean up the 663-line patch-heavy workflow, profile nested combat/replacement/mana hotspots, and validate without relaxing the 5-second AI budget. Do not modify protected baseline `baseline/verified-five-game-210e5db`, canonical decks, or merge PR #1. Work in small commits on the experiment branch; update the handoff with verified changes and runs. Don't redo already resolved setup problems.

## 8. Non-negotiable evidence discipline

- Distinguish **observed** from **hypothesized** causes and **completed** from **planned** changes.
- A completed Forge game with AI timeouts is **INVALID**, even if it reports a winner.
- Distinguish game turn in Forge's raw log from Lab's terminal turn count; don't conflate them.
- Do not claim a Java stack trace was inspected unless its artifact was actually accessed.
- No background monitoring is implied. Explicitly check GitHub Actions for results when asked.
- Avoid changing cards/decks to work around an AI performance defect.
- Always preserve actual source and evidence links, not just chat summaries.

## 9. 2026-10-15 continuation — first cleanup slice

- Working branch only: `experiment/guardrails-watchdog-v1`. Protected baseline and canonical decks untouched; PR #1 not merged.
- Commit `58e0362b`: extracted the existing Commander AI Lab alternate-win parser replacement into `scripts/patch_lab_winner_parser.py`. It preserves the original replacement text, asserts an exact upstream anchor, refuses ambiguous source, and supports idempotent reapplication.
- Commit `7a6816ad`: replaced the inline YAML patch with a call to that script. No intentional gameplay change.
- Commit `2bdc54c0`: added offline tests for exact replacement, idempotence and malformed upstream anchors.
- Commit `b9be6219`: included those tests in both existing workflow preflight locations. This workflow-file commit triggers a new Actions run; inspect its actual run SHA and results before claiming validation.
- Evidence artifact from run `38806749643` was confirmed present (artifact `12006638829`, 29,243,282 bytes, expires 2026-10-29). Its contents have **not yet** been inspected or archived in this continuation.
- **Status:** patch extraction committed; offline and CI test outcomes not yet verified at time of writing. No Forge AI performance fix claimed. Next: check triggered run, examine preserved diagnostic artifact, then extract the next patch only after validation.
