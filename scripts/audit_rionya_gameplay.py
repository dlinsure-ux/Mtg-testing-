#!/usr/bin/env python3
"""Conservative Forge game-event audit. Never infer a card's identity from draw text."""
import argparse
import collections
import json
import re
from pathlib import Path

TURN = re.compile(r"^Turn: Turn (\d+) \(Ai\((\d+)\)-")
ANY_DRAW = re.compile(r"\b(?:draws?|drew|drawing)\b", re.I)
PHASE = re.compile(r"^Phase: Ai\(1\)-.*?'s (.+)$")
CAST = re.compile(r"^Add To Stack: Ai\(1\)-.*? cast (.+?)(?: targeting |$)")
LAND = re.compile(r"^Land: Ai\(1\)-.*? played (.+?) \(\d+\)")
COPY = re.compile(r"^Add To Stack: Ai\(1\)-.*? triggered Rionya, Fire Dancer targeting \[(.+?) \(\d+\)\]")
DRAW = re.compile(r"^.*?Ai\(1\)-.*? draws? (?:a card|two cards|\d+ cards?)\b", re.I)
NAMED_DRAW = re.compile(r"^.*?Ai\(1\)-.*? draws? \[([^\]]+)\]", re.I)
MARKER = re.compile(r"RIONYA_PILOT (.+)")

def audit(log):
    turn = None
    rionya_turn = 0
    phase = None
    events = []
    casts = collections.Counter()
    landplays = collections.Counter()
    copies = collections.Counter()
    unknown_draw_events = 0
    named_draws = collections.Counter()
    markers = []
    commander_casts = 0
    for raw in log.splitlines():
        line = raw.strip()
        m = TURN.search(line)
        if m:
            turn = int(m.group(1))
            turn_owner = int(m.group(2))
            rionya_turn += 1 if turn_owner == 1 else 0
        m = PHASE.search(line)
        if m:
            phase = m.group(1)
        m = CAST.search(line)
        if m:
            card = m.group(1).strip()
            casts[card] += 1
            if card.startswith("Rionya, Fire Dancer"):
                commander_casts += 1
            events.append(dict(global_turn=turn, rionya_turn=rionya_turn, phase=phase, action="cast", card=card))
        m = LAND.search(line)
        if m:
            card = m.group(1).strip()
            landplays[card] += 1
            events.append(dict(global_turn=turn, rionya_turn=rionya_turn, phase=phase, action="land", card=card))
        m = COPY.search(line)
        if m:
            card = m.group(1).strip()
            copies[card] += 1
            events.append(dict(global_turn=turn, rionya_turn=rionya_turn, phase=phase, action="rionya_target", card=card))
        m = NAMED_DRAW.search(line)
        if m:
            named_draws[m.group(1).strip()] += 1
        elif DRAW.search(line) or ("Ai(1)-" in line and ANY_DRAW.search(line)):
            unknown_draw_events += 1
        m = MARKER.search(line)
        if m:
            markers.append(dict(global_turn=turn, rionya_turn=rionya_turn, detail=m.group(1)))
    precombat = [e for e in events if e["action"] == "cast" and e["phase"] and "precombat" in e["phase"].lower()]
    return dict(schema_version=3, commander_casts=commander_casts, global_turn_last_seen=turn, rionya_turns_seen=rionya_turn, events=events, cast_counts=dict(casts),
                land_counts=dict(landplays), rionya_copy_target_counts=dict(copies),
                precombat_casts=precombat, named_draw_counts=dict(named_draws),
                unidentified_draw_events=unknown_draw_events, pilot_markers=markers,
                pilot_markers_observed=bool(markers),
                draw_identity_coverage="incomplete: normal draw-step identities are not logged",
                unplayed_hand_cards="unknown: no hand-zone snapshots",
                mana_spent="unknown: mana-payment events not reconstructed",
                warning="Draw counts are best-effort, not complete; missing markers do not prove the policy was inactive.")

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--results", default="results")
    a = p.parse_args()
    root = Path(a.results)
    reports = []
    for game in sorted(root.glob("game-*"), key=lambda x: int(x.name.split("-")[1])):
        attempts = sorted(game.glob("attempt-*/forge-sim-debug.log"))
        if not attempts:
            continue
        source = attempts[-1]
        report = audit(source.read_text(errors="replace"))
        smoke = source.with_name("smoke-test.log")
        if not smoke.exists():
            smoke = root.parent / "smoke-test.log"
        if smoke.exists():
            report["pilot_markers_in_lab_log"] = [m.group(1) for line in smoke.read_text(errors="replace").splitlines() if (m := MARKER.search(line))]
            report["pilot_markers_observed"] = report["pilot_markers_observed"] or bool(report["pilot_markers_in_lab_log"])
            report["pilot_marker_source"] = str(smoke)
        else:
            report["pilot_markers_in_lab_log"] = []
        report["pilot_activation_confirmed"] = any("decision_entry" in x["detail"] for x in report["pilot_markers"]) or any("decision_entry" in x for x in report["pilot_markers_in_lab_log"])
        report["commander_search_observed"] = any("commander_search" in x["detail"] for x in report["pilot_markers"]) or any("commander_search" in x for x in report["pilot_markers_in_lab_log"])
        report["game"] = int(game.name.split("-")[1])
        report["source"] = str(source)
        out = game / "card-audit.json"
        out.write_text(json.dumps(report, indent=2) + "\n")
        reports.append(report)
        print("CARD_AUDIT game={} casts={} lands={} copies={} pilot_markers={} unknown_draw_events={}".format(
            report["game"], sum(report["cast_counts"].values()),
            sum(report["land_counts"].values()),
            sum(report["rionya_copy_target_counts"].values()),
            len(report["pilot_markers"]) + len(report["pilot_markers_in_lab_log"]), report["unidentified_draw_events"]), flush=True)
    (root / "card-audit-index.json").write_text(json.dumps(
        {"games": len(reports), "summaries": [
            {k: r[k] for k in ("game", "cast_counts", "commander_casts", "rionya_copy_target_counts",
                               "pilot_markers_observed", "pilot_activation_confirmed", "commander_search_observed", "unidentified_draw_events")}
            for r in reports]}, indent=2) + "\n")
    if not reports:
        raise SystemExit("No game logs found for card audit")

if __name__ == "__main__":
    main()
