#!/usr/bin/env python3
"""Fail-closed reconciliation of one Commander AI Lab game and Forge terminal outcome."""
import json
import re
import sys
from pathlib import Path

lab = Path("smoke-test.log").read_text(errors="replace") if Path("smoke-test.log").exists() else ""
forge = Path("forge/forge-sim-debug.log").read_text(errors="replace") if Path("forge/forge-sim-debug.log").exists() else ""
# Parse only terminal outcomes from Forge, not historical text or workflow shell commands.
lab_results = re.findall(r"^\[Game 1/1\] Winner: (.*?) \| Turns: (\d+) \| Time: (\d+)ms\s*$", lab, re.M)
forge_results = re.findall(r"^Game Result: Game 1 ended in (\d+) ms\. Ai\((\d+)\)-(.+?) has won!\s*$", forge, re.M)
outcomes = re.findall(r"^Game Outcome: Ai\((\d+)\)-(.+?) has won (.+)$", forge, re.M)
# Classify timeouts by phase. Startup AI evaluation timeouts are retained as
# warnings, not mistaken for whole-game interruption after a clean terminal result.
first_turn = re.search(r"^Turn: Turn 1\\b", forge, re.M)
startup_log = forge[:first_turn.start()] if first_turn else ""
game_log = forge[first_turn.start():] if first_turn else forge
startup_ai_timeouts = len(re.findall(r"^java\\.util\\.concurrent\\.TimeoutException\\s*$", startup_log, re.M))
in_game_ai_timeouts = len(re.findall(r"^java\\.util\\.concurrent\\.TimeoutException\\s*$", game_log, re.M))
whole_game_timeout = bool(re.search(r"Stopping slow match as draw|TimeoutException|InterruptedException", lab)) or bool(re.search(r"Stopping slow match as draw|^java\\.lang\\.InterruptedException", forge, re.M))
other_startup_timeouts = len(re.findall(r"^java\\.util\\.concurrent\\.TimeoutException\\s*$", startup_log, re.M)) - len(re.findall(r"java\\.util\\.concurrent\\.TimeoutException\\n\\s*at java\\.base/java\\.util\\.concurrent\\.FutureTask\\.get\\([^\\n]*\\)\\n\\s*at forge\\.ai\\.AiController\\.chooseSpellAbilityToPlayFromList", startup_log))
unsupported = re.findall(r"An unsupported card was requested:.*", lab + "\n" + forge)
exit_status = Path("diagnostics/exit-status.txt").read_text(errors="replace").strip() if Path("diagnostics/exit-status.txt").exists() else ""
errors = []
if len(lab_results) != 1: errors.append(f"Lab terminal results: {len(lab_results)} (expected 1)")
if len(forge_results) != 1: errors.append(f"Forge final results: {len(forge_results)} (expected 1)")
if len(outcomes) != 1: errors.append(f"Forge winners: {len(outcomes)} (expected 1)")
if whole_game_timeout: errors.append("whole-game timeout/interruption evidence")
if in_game_ai_timeouts: errors.append(f"in-game AI decision timeouts: {in_game_ai_timeouts}")
if other_startup_timeouts: errors.append(f"unclassified startup timeouts: {other_startup_timeouts}")
if not first_turn: errors.append("missing first-turn evidence")
if unsupported: errors.append("unsupported card evidence")
if exit_status: errors.append("nonzero Lab/supervisor exit: " + exit_status)
if lab_results and forge_results and outcomes:
    lab_winner, lab_turns, lab_ms = lab_results[0]
    forge_ms, forge_seat, forge_winner = forge_results[0]
    outcome_seat, outcome_winner, _ = outcomes[0]
    if lab_winner == "DRAW": errors.append("Lab reports DRAW despite Forge terminal winner")
    if forge_seat != outcome_seat: errors.append("Forge final winner seat differs from outcome")
    if forge_winner != outcome_winner: errors.append("Forge final winner name differs from outcome")
    if abs(int(lab_ms) - int(forge_ms)) > 1000: errors.append("Lab/Forge elapsed time differs")
    # Lab winner is a deck path; compare seat name against the selected deck's metadata.
    seat_paths = {1:"decks/lathiel.dck",2:"opponents/aesi.dck",3:"opponents/bello.dck",4:"opponents/betor.dck"}
    expected = seat_paths.get(int(forge_seat), "")
    if not expected or not lab_winner.endswith(expected): errors.append("Lab winner deck disagrees with Forge winner seat")
report = {
    "classification": "VALID" if not errors else "INVALID",
    "errors": errors,
    "lab_result": lab_results,
    "forge_result": forge_results,
    "forge_winning_outcomes": outcomes,
    "startup_ai_decision_timeouts": startup_ai_timeouts,\n    "in_game_ai_decision_timeouts": in_game_ai_timeouts,\n    "whole_game_timeout": whole_game_timeout,\n    "warnings": ([f"{startup_ai_timeouts} startup AI decision timeouts before first turn; piloting quality not independently verified"] if startup_ai_timeouts else []),
    "unsupported_cards": unsupported[:10],
    "supervisor_exit": exit_status,
}
Path("results").mkdir(exist_ok=True)
Path("results/validation-report.json").write_text(json.dumps(report, indent=2) + "\n")
print("GAME " + report["classification"] + ": " + ("; ".join(errors) if errors else "Lab and Forge agree on one terminal winner"))
print(json.dumps(report, indent=2))
sys.exit(1 if errors else 0)
