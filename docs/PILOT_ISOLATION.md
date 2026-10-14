# Pilot isolation and safe development

## Current truth
Forge's built-in AI controls the game. Versioned files under `pilots/` are policy **specifications only** and do not alter the runtime. Do not claim a tutor or mulligan improvement has been deployed unless verified runtime hooks and integration tests exist.

## Change procedure
1. Edit only a new versioned `pilots/<commander>/vX.Y.json`, retaining the previous version.
2. Run `python3 scripts/validate_pilot_config.py pilots/lathiel/v0.1.json` and `python3 -m unittest discover -s tests -p 'test_pilot_config.py'`.
3. Run CI pilot-policy validation. It does not build Forge.
4. For actual AI behavior changes, implement a Forge decision adapter or a tested upstream Forge AI patch on a separate branch. Require targeted decision-unit tests, a one-game smoke test, then five-game strict validation.
5. Keep the known-good Forge workflow untouched; rollback by reverting only the pilot adapter commit or selecting the previous version.

## Safety boundary
A policy document alone cannot change Forge's pilot. The CI policy check guards versioned inputs, **not** Forge gameplay or the reliability of unimplemented adapters.
