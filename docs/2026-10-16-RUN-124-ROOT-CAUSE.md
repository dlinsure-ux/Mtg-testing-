# Run #124 — game 3 performance diagnosis (2026-10-16)

Evidence: GitHub Actions run 38893590407, artifact 12042734776, game-3/forge-sim-debug.log and diagnostics/game-3/thread-5-7198.txt.

## Reproduction
Games 1 and 2 VALID (743 and 988 actions). Game 3 INVALID (1012 actions; six 5-second timeouts). Failing players: Deadpool (MAIN1 twice), Shroofus (MAIN1 and COMBAT_BEGIN three times), Winota (COMBAT_BEGIN once). Games 4–5 not attempted.

## Quantitative evidence
Game 3 emitted FORGE_COUNTERATTACK_PROFILE through at least call 305. Inclusive aggregate at call 305: 121452 ms; max single measured prediction: 4890 ms. These totals include nested/overlapping work and are not independent CPU-time accounting. Several predictions in a row exceeded 1 second.

Representative decision profiles:
- Shroofus MAIN1 elapsed 5001ms, 16 blocker calls, 2602ms inclusive blocker time.
- Shroofus COMBAT_BEGIN elapsed 5003ms, 14 blocker calls, 2865ms inclusive blocker time.
- Deadpool MAIN1 elapsed 5000ms, only 2 blocker calls totaling 7ms, showing that not all stalls are blocker-bound.

## Concrete stack
PumpAiBase.getPumpCreatures -> ComputerUtilCard.shouldPumpCard -> doesCreatureAttackAI -> AiController.getPredictedCombat -> AiAttackController.declareAttackers -> notNeededAsBlockers -> ComputerUtil.predictNextCombatsRemainingLife -> AiBlockController.assignBlockersForCombat -> makeGoodBlocks -> getSafeBlockers -> ComputerUtilCombat.canDestroyBlocker -> predictPowerBonusOfBlocker -> dealsFirstStrikeDamage -> canGainKeyword -> Card.getAllSpellAbilities -> CardState.updateSpellAbilities.

## Critical caching finding
Upstream AiController.getPredictedCombat already memoizes its predicted Combat in the predictedCombat field. Adding another cache at doesCreatureAttackAI would duplicate an existing cache and risks stale board-state answers. Do not add a second cache without measuring cache invalidation and establishing equivalence. Likewise, caching predictNextCombatsRemainingLife by CardCollection identity is unsafe: the same collection is mutated in the blocker loop, and abilities/state can change during evaluation.

## Next bounded work
1. Profile call counts and timings at CardState.updateSpellAbilities, ComputerUtilCombat.canGainKeyword, and ComputerUtilCombat.canDestroyBlocker, including main-thread and worker-thread execution.
2. Add a regression fixture for the actual game-3 board state or deterministic seed, plus a test that compares attack decisions with and without each optimization.
3. Optimize repeated *pure* calculations only after proving equivalent inputs and safe invalidation; preserve exact combat legality and five-second deadline.
4. Retest 5 consecutive games, zero timeouts, and compare AI tactical choices. Do not count a build-only pass as a gameplay fix.

No evidence yet establishes a safe broad cache or a fully repaired five-game run.
