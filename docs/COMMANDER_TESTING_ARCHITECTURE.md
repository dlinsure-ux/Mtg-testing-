# Commander Testing Reliability & Learning Architecture

Status: incremental migration, **not** a claim that the existing Forge integration is verified.

## Sources evaluated
- LlamaAdam/commander-builder (MIT): separate forge_runner, log_parser, game_analyzer, SQLite knowledge_log, controlled A/B comparisons. Reference: https://github.com/LlamaAdam/commander-builder/blob/master/docs/architecture.md
- kevinleelynch/forge-commander-ai (GPL-3.0): proposed candidate-action ranking and Forge AI fallback. Its repository currently contains primarily documentation; do not treat it as a production-ready AI integration. Reference: https://github.com/kevinleelynch/forge-commander-ai
- onemorethan0/mythforgemtg (MIT): continuous regression testing and simulation-informed analysis. Its semantics dataset is not fully bundled; its custom simulator is not a substitute for Forge rules fidelity. Reference: https://github.com/onemorethan0/mythforgemtg
- KoalaTrapLord/commander-ai-lab: retained as the existing launch/summary adapter until a verified Forge-native runner replacement passes equivalent tests.

## Nonnegotiable result contract
1. The authoritative rules engine is Forge; Lab's report must agree with Forge's exact terminal winner and game number.
2. A game only contributes to wins/losses when the strict validator returns VALID. No inferred outcomes, no synthetic games in stats.
3. Save original Forge/Lab logs, run ID, source commits, deck hashes, exit codes, and validation JSON before analysis.
4. Startup AI timeouts may be reported as warnings only if phase attribution is proved; unknown or gameplay timeouts are invalid.
5. Missing evidence is UNKNOWN/INVALID, not a draw or a win.
6. Separate **game result integrity** from **pilot quality**: a valid winner is not proof that tutors, mulligans, or abilities were piloted correctly.

## Staged migration
### Stage A: Offline contract tests (current)
Python syntax check, log fixtures, mismatch/timeout/crash cases, lint; fail before Forge build. Keep tests independent of the validator's import-time CLI behavior.

### Stage B: Reliable game runner (pending)
Isolate subprocess launch and monitoring from parsing; pin upstream commits. Verify Java PID using process ancestry and command line before thread dumps. Distinguish process liveness from game-turn progress. Never classify a deadlock from a single dump.

### Stage C: Structured persistence (first component added)
Store only validator-approved outcomes in a local SQLite database, with unique run/game IDs. Preserve failures separately as diagnostic records. No statistics from invalid runs.

### Stage D: Observability (pending instrumentation)
Record per-source draws, stranded cards, tutor candidates/targets, commander actions, missed activations and protection choices as *explicit* Forge event data. Do not infer missing decisions from final board state.

### Stage E: Pilot learning (pending)
Candidate-action interface and deterministic policy tests first. Keep Forge's AI fallback. Evaluate one pilot change at a time against a fixed three-deck Bracket 3 pod over repeated five-game series, with RNG uncontrolled but opponents held constant.

## Maintenance and licensing
Do not blindly copy GPL-3.0 code into this repository. Commander Builder is MIT; review provenance and license for any code copied. Prefer reimplementing small, documented interfaces until compatibility is demonstrated. Record a source reference and tests in every integration commit.

## Operator checklist
1. Run offline preflight, inspect exact failures, fix until green.
2. Run one smoke game, require valid terminal agreement and preserved logs.
3. Only then run a fixed-opponent five-game series.
4. Persist outcomes and diagnose card/decision telemetry only when captured, not guessed.
