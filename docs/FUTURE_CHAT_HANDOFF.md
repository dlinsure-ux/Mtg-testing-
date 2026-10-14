# START HERE — MTG Commander Forge Testing: Full Recovery and Future-Chat Handoff

**Last documented:** 2026-10-14  
**Repository:** https://github.com/dlinsure-ux/Mtg-testing-  
**Read this entire document before making changes.** This is the canonical operational handoff for the current workstream. Verify live GitHub state rather than assuming this snapshot remains current.

## 0. The user's objective and non-negotiable requirements

Build a durable, repeatable Commander simulation laboratory using KoalaTrapLord's Commander AI Lab and Forge fork, with GitHub as the source of truth. Run real four-player games, validate results strictly, diagnose pilot decisions and card performance, and eventually improve each commander's **actual** runtime AI pilot. User explicitly wants to stop repeating troubleshooting across new chats. Preserve a working recovery point and **never trade reliability for an unverified improvement**.

The user wants five-game series against the **same three opponent decks throughout each series**. Analyze card draw and draw sources, dead/stranded cards in hand, tutors and tutor-target logic, mulligans, mana, commander casts, win conditions, and AI decisions. Pilot changes must be empirically evaluated; written policies alone do not count as runtime changes.

The user has repeatedly asked assistants to work directly in GitHub and keep going, but this is **not permission to merge untested changes into main**. Work on experimental branches, prove behavior, then promote safely.

## 1. Immediate status / READ FIRST

- **Historical known-good five-game baseline:** commit `210e5db24ae4c920a7c1948a5bd2ed74432623de`, branch `baseline/verified-five-game-210e5db`, successful Actions run **38590772642**. Keep intact.
- **Most recent successful main five-game run:** **38623230136**, harness SHA `5da761709ff1f5a0845a4324cc66c08efc6d4669`. It finished SUCCESS and produced `lathiel-five-game-series-evidence`, artifact ID **11923402716**. The artifact includes individual game logs, strict validation reports, diagnostics, version manifest, and series audit. Download while still retained (original expiry 2026-10-28).
- **Experimental branch:** `experiment/guardrails-watchdog-v1`. This branch contains the current audit hardening, non-destructive watchdog prototype, tests, replay script, and experimental CI workflow.
- **Draft pull request:** https://github.com/dlinsure-ux/Mtg-testing-/pull/1 from experimental branch into `main`. **DO NOT MERGE until the checks and review gates below are met.**
- **Real-log replay:** Actions run **38635843028**, SUCCESS, 5/5 archived games had detectable turn progression and terminal result markers. The observed final turns in the Forge debug logs were 39, 46, 50, 34, and 45 respectively. The Lab smoke logs did not provide the same markers; the replay checks both files and succeeds if at least one real log provides each required marker.
- **Preflight after replay documentation change:** Actions run **38635961872**, SUCCESS, SHA `08fffe5`. This is a preflight result, not a five-game run at that experimental SHA.
- **Unverified:** live watchdog integration, detection of a truly stalled live game, PID/JVM dump reliability under stalls, actual Lathiel pilot runtime hooks, telemetry completeness, and any performance gain from pilot policy. Do not describe these as solved.
- **Do not claim a draft PR is merged, a branch is protected, or an artifact replay is a new simulation.**

## 2. Repository structure and exact responsibilities

| Path | Role | State |
| --- | --- | --- |
| `.github/workflows/commander-ai-lab-build.yml` | Main five-game sequential Forge/Lab build and strict validation workflow | Successful at main SHA 5da7617 |
| `.github/workflows/commander-ai-lab-known-good.yml` | Manual recovery workflow copied from working historical baseline | Preserve |
| `.github/workflows/forge-preflight.yml` | Fast syntax, validator regression, actionlint, and ordering checks | Main + expanded tests on experimental branch |
| `.github/workflows/pilot-policy-check.yml` | Validates pilot specification/golden case structure | NOT a runtime pilot test |
| `.github/workflows/experimental-watchdog-replay.yml` | Downloads archived five-game evidence and replays watchdog parser | Experimental branch only; SUCCESS run 38635843028 |
| `scripts/validate_forge_game.py` | Strict Lab/Forge result validator | Main workflow uses it for every game |
| `scripts/audit_series.py` | Summarizes exactly five independently validated game reports; experimental hardening checks timeouts and unsupported cards | Changes under PR #1 |
| `scripts/game_progress_watchdog.py` | Polls game log, tracks turn/action/terminal markers, writes JSON samples and possible-stall suspicion | Prototype, observation-only, NOT wired into live Forge workflow |
| `scripts/replay_watchdog_evidence.py` | Checks five archived game logs and reports for expected markers | Experimental; verified on archived five-game artifact |
| `tests/test_series_audit.py` | Tests five valid games, missing game, invalid game, in-game timeout | Experimental |
| `tests/test_game_progress_watchdog.py` | Tests turn advancement, log noise, terminal markers, repeated turns, empty logs | Experimental |
| `tests/test_validator_patterns.py` | Existing strict winner/timeout parser regressions | Keep running |
| `pilots/lathiel/v0.1.json` | Human-readable Lathiel pilot policy | **SPECIFICATION ONLY; runtime_integrated:false** |
| `pilots/lathiel/golden_cases.json` | Expected pilot priorities and sample situations | **NOT actual Forge AI decision tests** |
| `scripts/validate_pilot_config.py` | Rejects false claim of runtime integration | Existing |
| `docs/PILOT_ISOLATION.md` | How to isolate pilot development | Existing |
| `docs/RELEASE_GATES.md` | Baseline and promotion rules | Existing |
| `docs/FUTURE_CHAT_HANDOFF.md` | **This document; start here** | Canonical handoff |

Main workflow obtains pinned upstream sources:
- Commander AI Lab: `https://github.com/KoalaTrapLord/commander-ai-lab.git` at `54cc5e9b2e9ae6266c64b5cdf11f3302f311c701`.
- Forge fork: `https://github.com/KoalaTrapLord/Forge-Repo.git` at `edb28158f6d892f9eb43dc08f019aab567276027`.
- Build currently uses Java 17 and `-Drevision=2.0.12-SNAPSHOT`. Do not confuse this with the user's other historical Forge 2.0.14 projects or silently upgrade dependencies.
- Main test deck: `decks/lathiel.dck`. Three fixed upstream opponents: `lab/precon-decks/Explorers_of_the_Deep.dck` (Hakbal), `lab/precon-decks/Enduring_Enchantments.dck` (Anikthea), and `lab/precon-decks/Hail,_Caesar.dck` (Caesar).

## 3. Verified workflow mechanics

Main workflow builds Forge and Lab once and runs five games **sequentially** (not five separate rebuilds). For each game:
1. Start Commander AI Lab with Forge jar, Java 17, virtual display, the same four deck paths, one game, one thread, and game clock.
2. Save Lab stdout/stderr to `results/game-N/smoke-test.log`; capture `forge/forge-sim-debug.log` to `results/game-N/forge-sim-debug.log`.
3. Gather process and JVM diagnostic snapshots when possible.
4. Run `scripts/validate_forge_game.py` and copy its `validation-report.json` to each game's directory.
5. Abort after invalid game 1; any invalid later game also makes the series fail.
6. If all games valid, run `scripts/audit_series.py results` and produce `results/series-audit.json`.
7. Upload logs, reports, diagnostics, manifest and build evidence as a GitHub Actions artifact.

Strict validation means one genuine terminal winner, Lab and Forge agreement, no unsupported-card evidence, and no in-game AI decision timeouts. A long run is not automatically a deadlock. Do not label a game PASS solely because a log contains the word 'winner' or the job exits zero; inspect strict validation evidence. Do not fabricate game results, winner counts, or telemetry.

The manifest records Forge, Lab, harness commits and SHA256 for workflow, validator, pilot spec and all four decks. The manifest explicitly states `runtime_pilot_integrated:false`.

## 4. Why the guardrails exist: previous failures and exact lessons

The repository experienced repeated breakages after well-intentioned workflow changes. These are known regressions and should remain documented:

1. **GitHub Actions actionlint SC2009** after an initial workflow refactor (`c15497b`); corrected in `27de150`.
2. **setup-java Maven caching too early** before upstream pom.xml checkout (`27de150`); corrected by caching only after cloning upstream repos (`092102f`). Run **38617655035** was successful.
3. **actionlint glob expansion mismatch** in preflight (`703ce7d`); fixed by explicit workflow paths (`eef3138`). Run **38621466987** successful.
4. **Self-matching cache guard**: a script checked for `cache: maven` and matched that string in its own source, producing a false failure (`7eac7b6`); fixed (`554d895`). Preflight run **38621838807** succeeded.
5. **Experimental audit syntax error** from literal backslash-n sequence in code, caught by CI after `0cc1735`. Initial repair `2f65949` exposed a second unterminated-string error; final repair `1a8d203` passed both preflight runs.
6. **Experimental watchdog test fixtures used literal backslash-n** instead of actual line separators; this caused two expected-progress tests to fail after `e017fe5`. Fixed in `362c975`; both preflights succeeded.
7. **Real-evidence replay import error**: `python3 scripts/replay_watchdog_evidence.py` could not import `scripts` (`ModuleNotFoundError`); fixed by importing the sibling module `game_progress_watchdog` (`e0e3aa9`). Replay run **38635843028** passed.
8. **Historical Forge diagnostics PID defect**: `xvfb-run` wrapper PID can be mistaken for actual Java child PID. Before using `jcmd`/`jstack`, verify the Java process identity via process tree, command line and start time. A thread dump alone does not prove deadlock. If a diagnostic attachment fails, record error; `kill -3` on verified Java PID can be a fallback where supported. Do not assume this historical issue has been fully fixed in the present workflow.
9. **False confidence in pilot policy**: the versioned Lathiel JSON and golden cases are documentation/configuration checks, not runtime AI behavior changes. Never report 'pilot fixed' based solely on them.

For every future failure, preserve the original logs, exact SHA, workflow run/job IDs, exit codes, and whether failure occurred in preflight, build, game, validation, audit, replay, or artifact upload. Fix the root cause in a branch and add a regression test.

## 5. The watchdog: what it does and does not prove

`progress_markers(text)` in `scripts/game_progress_watchdog.py` extracts:
- `last_turn` from lines like `Turn: Turn 39` or `Turn: 39`.
- `turn_markers` count.
- `action_markers` count from selected log patterns.
- `terminal_result` when a line begins `Game Result: Game N ended in`.

The standalone CLI accepts `--log`, `--output`, `--interval`, `--stale-seconds`, `--max-seconds`; it polls a growing log and writes JSON evidence with `possible_stall`. It **does not kill a process**, restart Forge, declare deadlock, or alter AI. 'Possible stall' is not proof of deadlock.

Evidence replay checks complete logs; it does **not** prove that a live game will generate the same markers at the same frequency, nor that slow AI and a deadlock are distinguishable. The replay's PASS condition is that for each of five strictly validated games at least one available log contains a turn marker and at least one contains a terminal marker. Replay run **38635843028** met this condition. This is a parser validation gate only, not a production watchdog deployment.

Before wiring into live simulation, test a controlled live run on a separate experimental workflow. Track a monotonic meaningful-progress signature, capture several snapshots separated in time, verify Java child PID, and never terminate a game solely due to stale log size. Account for slow AI loops, normal long turns, JVM crash, external execution interruption and unknown cause. Do not silently change game clock or timeout thresholds.

## 6. How to reproduce the currently verified tests

From the experiment branch checkout:
```bash
python3 -m unittest discover -s tests -p 'test_validator_patterns.py' -v
python3 -m unittest discover -s tests -p 'test_series_audit.py' -v
python3 -m unittest discover -s tests -p 'test_game_progress_watchdog.py' -v
python3 scripts/validate_pilot_config.py pilots/lathiel/v0.1.json
```

For archived real-log replay, GitHub Actions workflow `experimental-watchdog-replay.yml` downloads artifact `lathiel-five-game-series-evidence` from run `38623230136`, then executes:
```bash
python3 scripts/replay_watchdog_evidence.py historical-evidence
```
GitHub Actions run **38635843028** is the verification reference. The artifact has finite retention; do not assume it remains available forever. If expired, create a new validated five-game artifact or preserve a sanctioned fixture (without secrets), and update this guide.

The preflight workflow uses actionlint with explicit paths. Do not reintroduce a wildcard assumption or pre-checkout Maven cache. If adding another workflow, add it to static lint coverage after verifying that change itself.

## 7. Next work, in order

**Gate A — verify repository state now.** Check the latest GitHub runs, PR #1 diff, base/head SHAs and branch status. Never infer completion from previous chat messages. Confirm no new failures and no changes to production workflow/decks/pilot.

**Gate B — preserve current evidence.** Keep the successful five-game artifact and its version manifest accessible. If retention expiration is imminent, copy a suitable evidence snapshot to durable repository storage or a separate artifact archive without committing enormous binaries to git.

**Gate C — review PR #1 and rerun checks at its latest SHA.** Review `scripts/audit_series.py`, watchdog, replay, test fixtures, CI permissions and workflow changes. The experimental watchdog is observation-only and not integrated. Check for overly permissive regex, false positives, malformed JSON handling and trust boundary in validation reports. Run all tests. Only merge if changes are safe and all relevant gates pass. If a doc-only commit occurs after the replay, ensure required CI is still green for the current head and state explicitly what was tested.

**Gate D — promote safeguards carefully.** If the user authorizes merge or clearly requests completion, merge the reviewed PR only after confirming green checks and safe scope. Never modify the historical recovery branch. After merge, run preflight and a new strict five-game series on main. Compare exact manifest, game validity, and runtime with known-good; rollback only the new change if it regresses.

**Gate E — implement actual pilot behavior in isolation.** Start a *new* experiment branch. Map Forge AI decision points for tutors, mulligans, commander casting, card sequencing, draw and protection. Build an actual runtime adapter/patch with decision traces and unit tests. Only after runtime integration exists may the policy `runtime_integrated` flag change. Use the same fixed opponent pod for five-game A/B comparisons and track card-level telemetry. Do not confuse a 'policy test passed' with 'AI plays better'.

**Gate F — richer telemetry.** Capture draw source and count, stranded/dead cards, tutor targets and timing, commander casts and missed activations, mana, mulligans, cards not cast, token/counter outcomes, winner and turn. Be explicit when Forge lacks instrumentation rather than inventing metrics.

## 8. GitHub connector / future-chat recovery procedure

A new assistant should:
1. Open this file in `main` if present; otherwise read it from `experiment/guardrails-watchdog-v1`. Start from the repo and the PR, not from chat history.
2. Use GitHub connector access to inspect repository files, commits, pull request diff, workflow run statuses and job logs. If connector access fails, **diagnose permissions** rather than claiming the repo does not exist or asking the user to repeat previous troubleshooting.
3. Inspect the latest `main` SHA and the PR head SHA. Compare to the documented references; don't assume this document is current if newer commits exist.
4. On a failed workflow, fetch **job steps and logs**, locate the first real exception or failing test, reproduce minimally, and add a regression test. Don't keep retrying an unchanged failure.
5. Make changes on a dedicated experiment branch. Commit descriptive changes; keep this handoff updated with precise evidence and what remains unknown.
6. Never report a GitHub action succeeded unless its tool response or workflow result confirms success.
7. Never claim real simulation occurred from static policy tests, log parsing tests or a GitHub Actions job that stopped before the game step.
8. Explain to the user briefly what was verified, what remains pending, and what exact next step is underway. Avoid repetitive generic status updates.

Useful links:
- Repository: https://github.com/dlinsure-ux/Mtg-testing-
- PR #1: https://github.com/dlinsure-ux/Mtg-testing-/pull/1
- Historical baseline run: https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38590772642
- Successful latest main five-game run: https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38623230136
- Successful real-log replay: https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38635843028
- Experimental branch: https://github.com/dlinsure-ux/Mtg-testing-/tree/experiment/guardrails-watchdog-v1

## 9. One-line prompt for a new chat

> Continue my MTG Forge testing project in GitHub repo `dlinsure-ux/Mtg-testing-`. **First read `docs/FUTURE_CHAT_HANDOFF.md` and `docs/RELEASE_GATES.md` from GitHub**, inspect draft PR #1, the current main and experiment branch, and recent Actions results. Preserve historical baseline `baseline/verified-five-game-210e5db`. Follow the written release gates; do not claim the Lathiel pilot JSON is runtime-integrated. Continue the next unfinished verified step, commit work to GitHub, and update the handoff document with precise evidence.

## 10. Completion checklist for every future session

- [ ] Inspected live repository SHA and recent workflow runs.
- [ ] Read the handoff, release gates, and pilot isolation docs.
- [ ] Identified whether changes affect only documentation/tests or actual Forge runtime.
- [ ] Preserved historical baseline and production functionality.
- [ ] Used experimental branch for risky work.
- [ ] Added/ran regression tests and checked actual GitHub Actions result.
- [ ] Preserved failure logs and diagnostic evidence.
- [ ] Updated this file with new commits, runs, proven facts, unknowns and next action.
- [ ] Never declared the entire project complete while pilot runtime integration and telemetry remain unfinished.

## 11. Live continuation audit — 2026-10-14

- Inspected current GitHub state: main at `ab23a817f771320faa82277c2e6880a712ba26a8` (README formatting after verified gameplay); experimental branch at `e909a862d9081e93f3c7dd05e3f148a8d9e6f160` before this documentation update. PR #1 is OPEN, DRAFT, NOT MERGED; GitHub reports `mergeable:false`. No merge attempted.
- Latest inspected PR preflight run `38636779860` completed SUCCESS on experimental SHA `e909a862`. This is static/preflight evidence, not a new Forge game or a fresh watchdog replay. Historical five-game main run `38623230136` remains the last documented verified gameplay series.
- Reviewed release gates and pilot isolation; read experimental `audit_series.py`, `game_progress_watchdog.py`, `replay_watchdog_evidence.py` and audit tests. The watchdog is still observation-only and cannot establish actual JVM deadlock. Runtime pilot integration remains false.
- Review finding: `audit_series.py` trusts a report's `classification:VALID` and its Lab tuple without independently revalidating Forge winner evidence; that trust boundary depends on the upstream strict per-game validator. The replay checks turn/terminal markers but does not demonstrate a live stall. Before promotion, add negative tests for malformed reports, false-positive progress, and terminal markers without unique winners; verify actual five-game run on candidate code.
- Historical baseline `baseline/verified-five-game-210e5db` and production workflow/decks were not modified. This update documents review only; no gameplay, regression execution or pilot fix is claimed. Next: isolate a fail-closed audit hardening change with tests, run preflight and replay on its new SHA, then use the release gates before considering merge.

## 12. Continuation commits — 2026-10-14

- Experimental-only series audit hardening committed: `4e79ef67228803762fbc526e4b096fe8513f05f9` (`scripts/audit_series.py`). Adds fail-closed checks for malformed JSON, absent/contradictory Forge terminal evidence, invalid turn counts and elapsed-time disagreement.
- Regression fixtures and four negative tests committed: `8f312a8a21b4b408fece300c5937e101c7b294c3` (`tests/test_series_audit.py`). Fixture now supplies Forge terminal and winning outcome evidence.
- **Verification not yet completed:** GitHub connector returned no PR-triggered Actions runs for the second commit at inspection time; this does NOT prove tests passed. Run unit tests and preflight at the latest branch SHA; then replay and actual one-game/five-game release gates per `docs/RELEASE_GATES.md`. Do not merge or claim production promotion without evidence.
- Preserve `baseline/verified-five-game-210e5db` and main; neither was changed by these commits. PR #1 remains experimental until gates are verified.

## 13. Replay trigger follow-up — 2026-10-14

- Experimental workflow `experimental-watchdog-replay.yml` changed at commit `cf27f7c5e0d913609da67a880218c7b8ce03ba44` so changes to `scripts/audit_series.py` or `tests/test_series_audit.py` also trigger real-log watchdog replay. The workflow-file commit itself is also within the workflow's push path filters.
- Confirmed green preflight runs: `38637848016` at regression-test commit `8f312a8`, and `38637876616` at handoff commit `59a4f83`; latest preflight job steps all succeeded.
- The GitHub connector's commit-run lookup returns only pull-request-triggered runs and showed none immediately for `cf27f7c`; this does **not** establish whether a push-triggered replay ran or passed. Inspect GitHub Actions for the new SHA directly before claiming success.
- Still required: verified new replay result, real one-game gate, real five-game gate, hash manifest and pilot telemetry comparison. Do not merge PR #1 yet. Preserve historical baseline and main.

## 14. Live-game release gate trigger — 2026-10-14

- Updated experimental `.github/workflows/commander-ai-lab-build.yml` with a nonfunctional comment to trigger its push-path GitHub Actions run. Commit `0b927032d2cf1642174c1b677d6b8cd83acd9dc6`.
- This workflow is a real Forge/Lab build and sequential five-game run. It validates game 1 and aborts remaining games if invalid. It is not a standalone one-game workflow; do not claim separate one-game gate evidence without checking actual logs.
- GitHub connector `fetch_commit_workflow_runs` returns only pull-request-triggered runs, so immediate empty results for `0b92703` do NOT prove a run started, completed or failed. Check https://github.com/dlinsure-ux/Mtg-testing-/actions and capture exact run ID, SHA, step outcomes, manifest, and per-game validation before claiming any game results.
- Previous `cf27f7c` preflight run `38638172412` passed; archived watchdog replay at that SHA still needs separate outcome verification. Preserve baseline and leave PR #1 unmerged until gates pass.

## 15. Cancelled run #62 and live watchdog diagnosis (2026-10-14)

- User screenshot showed `Commander AI Lab Build Verification #62`, PR-triggered, SHA `0b92703`, preflight success and five-game build job still in progress at about 10 minutes; user subsequently cancelled. This is **not** evidence of a game stall or valid game. No run ID or game logs were obtained.
- Reviewed upstream Commander AI Lab docs: real Forge uses Java subprocesses and can be slow. Existing live workflow collected periodic Java thread dumps but never invoked `scripts/game_progress_watchdog.py`. This is an observability gap, not proven root cause of cancellation/slowdown.
- Commit `c6cc70d40aba46301963033e8155ac0db8d6b2de` starts the observation-only progress watchdog alongside each live game, saves `diagnostics/game-N/progress-watchdog.json`, and terminates only the observer after game exit. Existing `diagnostics/**` artifact upload includes these reports. No game-killing policy introduced.
- Next: inspect new GitHub Actions run and preflight; audit watchdog output against actual Forge turn and action logs. Separate Maven build time from game time. Verify that Forge progress markers match the actual log format; false stale reports are possible. Preserve baseline; no pilot change or merge without validated game evidence.

## 16. Automatic GitHub Actions run discovery (2026-10-14)

- Historical chats did inspect runs when numerical IDs were known (e.g. successful five-game run `38623230136`); the present GitHub connector's commit-run listing exposes preflight PR checks but not all push-triggered gameplay runs. This is a discovery problem, not proof of user permission failure.
- Created durable issue dashboard `https://github.com/dlinsure-ux/Mtg-testing-/issues/2`. Experimental workflow change commit `5063a829091682039bfe6965d6cd1e6d3ba92827` grants `issues: write` and adds `publish-run-start` job to post `RUN_START` with run ID, SHA and URL; build job posts `RUN_BUILD_STEP_FINISHED` and its job status via `if: always()`. Posting errors are warnings so they cannot invalidate game evidence.
- **First post not yet verified.** Future chats: use GitHub `fetch_issue_comments` for issue #2 to discover the newest run ID; then `fetch_workflow_run_jobs`, `fetch_workflow_job_logs`, `fetch_workflow_run_artifacts` to audit evidence. Do not infer run success from the dashboard start comment; verify jobs and per-game validation reports.
- Important limitation: if preflight fails, the build job is skipped and will not post its build outcome. Inspect run jobs. If dashboard remains empty, inspect experimental workflow preflight and GitHub issue-write permissions, and use the already-known run IDs rather than asking user to reconnect GitHub.
