# MTG Testing — Forge / Commander AI Lab Recovery README

> **LATEST HANDOFF — 2026-10-16/17, run #143:** **[READ THE RUN #143 COMBAT-PERFORMANCE HANDOFF FIRST](docs/2026-10-16-RUN-143-COMBAT-PERFORMANCE-HANDOFF.md)**. This is the authoritative next-chat restart guide, with exact run/commit IDs, game-by-game validation, seeds, measured 6–7.5-second speculative blocker calls, Shroofus COMBAT_BEGIN timeout stacks, prior #141/#142 context, safe optimization plan, and copy/paste continuation prompt. **Current status: unresolved; #143 passed 4/5, game 5 INVALID with 2 AI decision timeouts.** Documentation saved only; no performance optimization is claimed. Work on `experiment/guardrails-watchdog-v1`; never change protected baseline `3785d644` or merge PR #1.

> **LATEST HANDOFF (2026-10-15):** For the current Forge AI timeout diagnosis, 663-line workflow/code audit, exact runs/commits, safety guardrails, and a copy-ready new-chat prompt, **read [docs/2026-10-15-FORGE-PERFORMANCE-AND-CODE-CLEANUP-HANDOFF.md](docs/2026-10-15-FORGE-PERFORMANCE-AND-CODE-CLEANUP-HANDOFF.md) first.** This supersedes older 'next work' sections below where they conflict. The experimental performance problem remains unresolved; do not treat earlier single-game completions as a reliable series. The protected baseline and canonical decks must not be changed without approval.


**READ THIS FIRST IN EVERY NEW CHAT.** This is the project's persistent troubleshooting guardrail and recovery entry point. Updated 2026-10-14. Do not re-diagnose already solved failures without new evidence. The actual executable source of truth is the GitHub workflow and immutable run logs, not ChatGPT recollections or an old screenshot.

## Verified milestone — a REAL Commander game

- **Repository:** https://github.com/dlinsure-ux/Mtg-testing- (default branch `main`)
- **Workflow:** [Commander AI Lab Build Verification](.github/workflows/commander-ai-lab-build.yml)
- **Proven successful run #24:** https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38499345448
- **Run ID:** `38499345448`; **job:** `115642326391`; **commit:** `57cdbc29ee0f24ea64865f035613d7da90093b1d`
- **Outcome:** FOUR real AI Commander seats: Aesi / Aesi / Bello / Betor. **Bello won by combat, turn 14**, with 14 life remaining. Wall clock ~32.3 seconds; Forge result says 29,069 ms. The duplicate Aesi in seat 1 is only a temporary smoke-test stand-in, NOT Lathiel.
- **Raw winner proof:** `Game Result: Game 1 ended in 29069 ms. Ai(3)-Bello, Bard of the Brambles - archived complete Moxfield mirror has won!`
- **Observed:** real mulligans, card plays, spell casting, combat, eliminations, winner, 14 turns, JSON output and successful GitHub job. This is ONE successful smoke test, NOT a proven five-game series.
- **Artifacts:** `smoke-test.log`, `forge/forge-sim-debug.log`, `results/smoke-test.json`, `direct-forge.log`, GitHub job log and artifact `commander-ai-lab-build-evidence` (14-day retention). `diagnostics-summary.txt` is only an abbreviated, mutable summary.

## Architecture: do not mix environments

**Commander AI Lab path (verified):** upstream `KoalaTrapLord/commander-ai-lab` (Lab v3.0.0) plus `KoalaTrapLord/Forge-Repo`, built as Forge `2.0.12-SNAPSHOT`; Java 17; Linux GitHub Actions; `xvfb-run`; Forge desktop JAR; 4-seat AI simulation via `sim` mode. The workflow clones both upstream repositories and builds them fresh. Record their exact cloned SHAs in future tests, because upstream can change.

**Separate older custom testing environment:** Forge **2.0.14**, custom pilot/neutral pilot, v3/v4 supervisor, watchdog, other recovered archives and historical deck tests. These are distinct from the successful Lab pipeline. Do NOT silently substitute its JAR, classes, pilot, deck rules or results into this pipeline. Existing Library guardrails and handoffs remain relevant for that separate environment.

## Chronological failures, proven causes and solutions

### 1. Java / Maven dependency builds
Earlier attempts had Java-version uncertainty and Maven-installed Forge POMs containing literal `${revision}`, which broke dependent resolution. The successful workflow explicitly installs Temurin Java 17, builds Forge using `-Drevision=2.0.12-SNAPSHOT`, replaces unresolved `${revision}` in installed Forge POM descriptors under `~/.m2/repository/forge/`, and packages Lab using the matching revision. Some external Maven metadata certificate/DNS warnings occurred even in successful builds: investigate build exit status, not warnings alone.

Commands from the working workflow:
```bash
git clone --depth 1 https://github.com/KoalaTrapLord/commander-ai-lab.git lab
git clone --depth 1 https://github.com/KoalaTrapLord/Forge-Repo.git forge
(cd forge && mvn -B -ntp install -pl forge-gui-desktop -am -DskipTests -Drevision=2.0.12-SNAPSHOT)
# Repair installed Forge POM descriptors still containing literal ${revision}.
(cd lab && mvn -B -ntp -DskipTests -Drevision=2.0.12-SNAPSHOT package)
```
**Guardrail:** `BUILD SUCCESS` or a generated JAR does NOT mean a game happened.

### 2. Fake zero-turn draws concealed startup failure
Lab resolved deck paths and wrote JSON, but Forge subprocess exited **1 with no captured output**, while Lab summarized a `DRAW`, `Turns: 0`. A successful JSON validation was misleading. Always inspect the subprocess exit code and raw Forge output. A failed subprocess, absent match result, `Could not load deck`, timeout or no meaningful turns is an **invalid run**, not a draw/loss.

### 3. Diagnostic shell prematurely aborted
Direct Forge startup probe used `timeout ... java -jar ... --help > direct-forge.log 2>&1` under GitHub Actions Bash `-e`; exit 1 aborted before printing the actual log. Correct capture:
```bash
rc=0
timeout 30 xvfb-run -a "$JAVA_HOME/bin/java" -jar "$FORGE_JAR" --help > direct-forge.log 2>&1 || rc=$?
echo "Direct Forge exit: $rc"
cat direct-forge.log
```
**Important:** Forge `--help` prints `Unknown mode. Known mode is 'sim', 'parse'` and may exit 0; this proves JVM startup, NOT a valid game. Keep diagnostics in `if: always()` steps.

### 4. Old GitHub reruns use old workflow definitions
Rerunning failed run `38465027063` still used its older YAML, even after a newer commit fixed probe handling. **To test changed workflow YAML, push a new commit and inspect the NEW run's commit SHA.** Never assume rerun means latest code. Do not infer status solely from `diagnostics-summary.txt`, which can lag or be overwritten.

### 5. Misdiagnosed JAR manifest
A truncated `unzip -p "$FORGE_JAR" META-INF/MANIFEST.MF | head -30` hid the actual `Main-Class`. Inspection of the upstream `forge-gui-desktop/pom.xml` and the complete manifest confirmed **`Main-Class: forge.view.Main`**. The executable JAR was valid. Do not resurrect the false 'missing Main-Class' diagnosis.

### 6. Actual startup fix: Xvfb
Forge desktop's GUI dependencies require a virtual display on headless Linux. Install `xvfb xauth` and run **both** the direct Forge probe and Commander AI Lab under `xvfb-run -a`. Commit `6851e39d9f8deed96ef36d88c917fb323fafe5b7`. The next run `38469339260` proved Forge started; its startup log showed Forge v2.0.12 and Java 17. The next blocker became deck loading.

### 7. Actual deck loading fix: Forge's named Commander deck store
After Xvfb, Forge printed **`Could not load deck - Aesi, match cannot start`**, while Lab still reported a 1-turn draw and GitHub was GREEN. Investigation of upstream `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java` revealed that `sim -d Aesi ... -f commander` resolves `Aesi` from `FModel.getDecks().getCommander()`. When a parameter ends with a three-character file extension, it instead looks in Forge's Commander directory. Merely passing source paths to Lab does not populate Forge's named deck store.

On Linux, Forge defaults to `~/.forge/decks/commander/` (see `ForgeProfileProperties` and `ForgeConstants.DECK_COMMANDER_DIR`). Install deck files before running:
```bash
mkdir -p "$HOME/.forge/decks/commander"
cp opponents/aesi.dck opponents/bello.dck opponents/betor.dck "$HOME/.forge/decks/commander/"
ls -l "$HOME/.forge/decks/commander/"
```
Commit `41bd48d13ebb7e920d654a74c2eb885af6757c04` installed decks and added fail-closed assertions; commit `57cdbc29ee0f24ea64865f035613d7da90093b1d` triggered the fresh successful run.

### 8. Prevent false green tests
The workflow must reject `Could not load deck`, `match cannot start`, `No deck found`, Forge nonzero exits, no subprocess output, `Turns: 0`, `Avg Turns: 0`, and absence of a raw Forge winner. Example:
```bash
grep -Eqi 'Could not load deck|match cannot start|No deck found|Forge exit=[1-9]|Forge produced NO output|Turns: 0|Avg Turns: 0' smoke-test.log forge/forge-sim-debug.log && exit 1
grep -Eq 'Game Result: Game [0-9]+ ended in .*has won!' forge/forge-sim-debug.log || exit 1
```
Also validate expected seat count, meaningful turn count, process status and full terminal results. A 1-turn summary alone proves nothing; genuine draws need explicit completed-game evidence, not just a draw label. Preserve raw logs even when an assertion fails.

## Commit and run recovery map

| ID | Evidence |
|---|---|
| `38465027063` | Failed old direct-probe run; old-YAML rerun trap |
| `d592be6209c867b493454667dad717d6b7d64f4d` | Nonfatal probe exit capture / direct log |
| `6851e39d9f8deed96ef36d88c917fb323fafe5b7` | Install Xvfb, run GUI-backed Forge under virtual display |
| `38469339260` | Forge starts; Aesi cannot load; green check falsely accepted |
| `41bd48d13ebb7e920d654a74c2eb885af6757c04` | Install Commander decks in Forge's own library, reject invalid games |
| `57cdbc29ee0f24ea64865f035613d7da90093b1d` | Fresh trigger, successful run #24 |
| `38499345448` | VERIFIED game: Bello combat win, 14 turns |

## Rules for new chats and future runs

1. **First:** read this README, current `.github/workflows/commander-ai-lab-build.yml`, and the last actual GitHub run logs; establish latest commit SHA. Preserve known-working baseline.
2. Check Java 17, Maven build, `forge.view.Main`, Xvfb, Forge working directory `forge/forge-gui` with `res/`, `~/.forge/decks/commander/`, four deck slots, subprocess launch and full output. Do not restart troubleshooting from guesses.
3. **Next planned deck:** canonical 100-card **Lathiel, the Bounteous Dawn** has **NOT** yet been uploaded or tested in this Lab workflow. Add exact `decks/lathiel.dck` and install it in Forge's Commander directory. Replace seat 1's duplicate Aesi with Lathiel. Seats 2–4 remain Aesi, Bello, Betor. Do not silently modify any deck to make it load.
4. Validate ONE completed real 4-player smoke game with Lathiel before requesting a five-game series; then five games, then larger series. Rotate opponents when requested. Track real wins, turns, draws, mulligans, commander casts/activations, tutor targets, counters, tokens, unsupported-card warnings, and meaningful game progress *only to the extent logs support*.
5. User's custom Forge testing rules remain distinct: true RNG, multiplayer London mulligans, first mulligan free, repeated free mulligans for one-land hands, no 1-land keeps except forced 5-card edge case, tutors for winning. **Do not assume Commander AI Lab implements these custom rules without source-level verification.**
6. For slow games, distinguish slow play, AI decision loops, JVM deadlocks, external timeouts and unknown. Monitor **meaningful game progress** (turns/actions/results), not generic log activity. When launched under `xvfb-run`, find and verify the real Java child PID (command line, process tree, start time) before `jcmd Thread.print` or `jstack`; collect multiple spaced dumps; fallback `kill -3` where supported. Long runtime alone is NOT a VOID/deadlock.
7. Preserve source hashes, exact commands, exit statuses, per-game logs, results JSON, artifact URLs and warnings. Never invent a winner or turn count; never count incomplete tests as games.
8. GitHub evidence artifacts expire after **14 days** in this workflow. Archive important logs. `diagnostics-summary.txt` is mutable and not complete.
9. GitHub workflow `on: push` triggers when its YAML path changes; README-only commits **do not trigger gameplay**. Use an intentional workflow change or dispatch to run future tests.
10. New chats should be able to resume using **this README and the workflow**, without asking the user to repeat troubleshooting already documented.

## Outstanding work (do not misrepresent as completed)

- Lathiel upload/validation and first five-game series.
- Multiple-run reliability and comprehensive opponent legality/card compatibility audit.
- Custom pilot and mulligan-rule compatibility with the Lab integration.
- Hardening Lab's underlying Java result parser (the workflow's fail-closed checks mitigate but do not necessarily fix its internal false-draw bug).
- Pinning exact upstream Forge/Lab commits for reproducibility.
- Long-term evidence archival before 14-day artifact expiry.

**Bottom line:** the infrastructure is genuinely working for at least one completed four-player game. Continue from that fact; do not confuse it with full project completion.

## Latest verified state — 2026-10-14 (Lathiel diagnostic)

**Persistent source of truth: GitHub, not chat context.** Every subsequent repair must update this README (or a linked guardrail document) with evidence, exact commit, workflow run, outstanding defects, and next actions. Do not rely on a new chat to remember prior discussion.

- **Latest diagnostic workflow commit:** `89131dc859dc68bc36ae8aaccd9d71a1b32fc4e0` (single instrumented Forge game, 600-second clock, Java thread sampling, fail-closed validation).
- **Run #28:** https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38504334241 ; job `115658242622`. Completed **failure** because the validation detected an unsupported card, NOT because the game exceeded the time limit.
- **Actual game evidence:** Lab reports Lathiel winner, **turn 13, 119043 ms**. Forge says Lathiel won; Deadpool, Winota, and Shroofus each lost at zero life; Match Result credits Lathiel 1 and everyone else 0. This is a consistent winner report, but **not a fully validated game** due to the unsupported-card diagnostic.
- **Run #27:** https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38501764232 ; job `115650123183`. Five-game series; games 1,2,4,5 reached approximately 300 seconds and emitted multiple contradictory winner candidates. They are **INVALID**, not losses. Game 3 completed in 123895 ms on turn 13, Winota sole winner, other three lost at zero life; credible result.
- **Run #26:** https://github.com/dlinsure-ux/Mtg-testing-/actions/runs/38500899296 ; job `115647345176`. Explicit Forge warning: `An unsupported card was requested: "Smaug, Wicked Worm" from "[N.A.]"`; AI timeout and contradictory winners. This is evidence of unsupported Smaug in that run, **not proof it is the only unsupported card in run #28**.
- **Confirmed deck location:** `opponents/deadpool.dck` contains `1 Smaug, Wicked Worm`; the three other currently tested deck files do not. **Do not modify the canonical Deadpool deck to hide this issue.**
- **Active request:** patch Forge's **card database and, if necessary, its mechanics implementation** so Smaug is actually supported. A name alias or a blank card entry is not a valid mechanics fix. The GitHub workflow currently clones `KoalaTrapLord/Forge-Repo` fresh on each run, so any patch must be reproducibly applied **before the Forge build**, preferably from a versioned patch/script committed to this repository. Validate exact card parsing, abilities, then a real four-seat game. Do not report a successful patch before testing.
- **Known test limitation:** run #28 logs display the unsupported-card validation failure, but the precise warning text from that run was not available in the displayed job log. Its `commander-ai-lab-build-evidence` artifact preserves `forge/forge-sim-debug.log` for inspection. Do not claim Smaug is proven to be the only cause without inspecting it.
- **Instrumentation caveat:** the current workflow uses periodic snapshots every 65 seconds. It may not capture a game that finishes in 119 seconds before the first meaningful Forge Java sampling point. Confirm actual PID/thread-dump artifacts before asserting a diagnostic dump exists. Improve sampling when investigating a slowdown.
- **Results-integrity guardrail:** never choose a winner by life total when Forge reports multiple winners or hits a clock limit. Require a single Forge winner and three genuine eliminations, with no unsupported-card warning or timeout. Distinguish simulation validity from GitHub job status.

**Next work:** inspect the full run #28 debug artifact and Forge card definitions; identify exact missing Smaug implementation; add a versioned, reproducible Forge source/database patch; build and run a card-specific test plus a complete four-seat test; update this README with verified evidence and commit SHAs. Keep this record on GitHub, not only in conversation.

## 2026-10-15 performance investigation — current checkpoint

**Current authoritative plan:** [docs/2026-10-15-FORGE-ROOT-CAUSE-AND-LONG-TERM-PLAN.md](docs/2026-10-15-FORGE-ROOT-CAUSE-AND-LONG-TERM-PLAN.md). Historical handoff: [docs/2026-10-15-FORGE-PERFORMANCE-AND-CODE-CLEANUP-HANDOFF.md](docs/2026-10-15-FORGE-PERFORMANCE-AND-CODE-CLEANUP-HANDOFF.md).

Run 38821663401: Forge/Lab compiled, but game INVALID with 3 AI decision timeouts in COMBAT_BEGIN. Artifact 12012687900 (expires October 29) contains JFR and raw logs. Measured 85 slow blocker calls totaling 52,951 ms inclusive, max 2,893 ms. JFR contains heavy `Card.isValid`/card-property checking, but samples span worker threads and must not be treated as exclusively AI decision CPU. No root-cause fix yet.

New offline-only instrumentation-analysis work: `scripts/summarize_forge_profiles.py` (commit `63dad417`) and `tests/test_summarize_forge_profiles.py` (commit `5ff7d697`). These analyze **existing** profile markers and do not alter Forge decisions, AI budget, decks, or workflow. They are committed but **not yet CI-verified**. Run `python3 -m unittest discover -s tests -p 'test_summarize_forge_profiles.py' -v` and `python3 scripts/summarize_forge_profiles.py forge/forge-sim-debug.log --output results/profile-summary.json` from the repo root. A future isolated preflight should verify tests without triggering the expensive five-game default workflow.

Immediate priorities: verify offline tests; profile the specific repeated call sites and isolate their caller frequencies; validate the current aliasing/no-blocker changes; introduce bounded per-decision summaries before making any gameplay optimization. Never merge PR #1 or modify the protected baseline or canonical decks.
