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
EVENT_PREFIX = "RIONYA_EVENT|"

def decode_field(value):
    return value.replace("%7C", "|").replace("%3D", "=").replace("%25", "%")

def parse_authoritative_events(text):
    events = []
    for raw in text.splitlines():
        line = raw.strip()
        if EVENT_PREFIX not in line:
            continue
        payload = line.split(EVENT_PREFIX, 1)[1]
        row = {}
        for part in payload.split("|"):
            if "=" not in part:
                continue
            k, v = part.split("=", 1)
            row[k] = decode_field(v)
        if row:
            for key in ("turn", "event_turn", "player_id", "card_id", "amount", "old", "new",
                        "hand_size", "battlefield_size", "creatures", "lands", "life"):
                if key in row and row[key] not in ("", None):
                    try:
                        row[key] = int(row[key])
                    except ValueError:
                        pass
            events.append(row)
    return events

def authoritative_summary(events):
    primary_ids = {e.get("player_id") for e in events if e.get("type") == "snapshot" and e.get("player_id") is not None}
    primary_id = next(iter(primary_ids)) if len(primary_ids) == 1 else None
    counts = collections.Counter(e.get("type") for e in events)
    primary_zone = [e for e in events if e.get("type") == "zone" and (primary_id is None or e.get("player_id") == primary_id)]
    opening_state = {}
    for e in primary_zone:
        if int(e.get("turn", 0) or 0) != 0:
            continue
        cid = e.get("card_id")
        if e.get("to") == "Hand":
            opening_state[cid] = e.get("card")
        if e.get("from") == "Hand":
            opening_state.pop(cid, None)
    draws = [e for e in primary_zone if e.get("from") == "Library" and e.get("to") == "Hand" and int(e.get("turn", 0) or 0) > 0]
    mulligans = [e for e in events if e.get("type") == "mulligan" and (primary_id is None or e.get("player_id") == primary_id)]
    snapshots = [e for e in events if e.get("type") == "snapshot"]
    snapshot_cards = [e for e in events if e.get("type") == "snapshot_card"]
    grouped_snapshots = []
    for snap in snapshots:
        turn = snap.get("turn")
        boundary = snap.get("boundary")
        cards = [e for e in snapshot_cards
                 if e.get("turn") == turn and e.get("boundary") == boundary
                 and (primary_id is None or e.get("player_id") == primary_id)]
        row = dict(snap)
        row["hand"] = [e.get("card") for e in cards if e.get("zone") == "Hand"]
        row["battlefield"] = [e.get("card") for e in cards if e.get("zone") == "Battlefield"]
        grouped_snapshots.append(row)
    return dict(
        primary_player_id=primary_id,
        event_counts=dict(counts),
        opening_hand_cards=list(opening_state.values()),
        exact_draws=[dict(turn=e.get("turn"), card=e.get("card")) for e in draws],
        mulligan_count=len(mulligans),
        snapshots=grouped_snapshots,
        damage_events=[e for e in events if e.get("type") in ("player_damage", "card_damage")],
        mana_events=[e for e in events if e.get("type") == "mana_pool"],
        counter_events=[e for e in events if e.get("type") == "card_counters"],
    )

STATE = re.compile(r"RIONYA_STATE turn=(\d+) phase=(\w+) hand=(.*?) board=(.*?)(?: life=(\d+) creatures=(\d+))?$")

def parse_snapshots(text):
    snapshots = []
    seen = set()
    for line in text.splitlines():
        match = STATE.search(line)
        if match:
            snapshot = dict(turn=int(match.group(1)), phase=match.group(2),
                            hand=[c for c in match.group(3).split("|") if c],
                            battlefield=[c for c in match.group(4).split("|") if c],
                            life=int(match.group(5)) if match.group(5) else None,
                            creatures=int(match.group(6)) if match.group(6) else None)
            key = (snapshot["turn"], snapshot["phase"], tuple(snapshot["hand"]),
                   tuple(snapshot["battlefield"]), snapshot["life"], snapshot["creatures"])
            if key not in seen:
                seen.add(key)
                snapshots.append(snapshot)
    return snapshots

def card_exposure(snapshots, cast_counts):
    seen = collections.Counter()
    by_turn = collections.defaultdict(set)
    for snapshot in snapshots:
        by_turn[snapshot["turn"]].update(snapshot["hand"])
    for cards in by_turn.values():
        seen.update(cards)
    return dict(hand_snapshots=len(snapshots), observed_turns=len(by_turn), hand_turns_observed=dict(seen),
                observed_in_hand_not_cast=sorted(c for c in seen if not cast_counts.get(c)),
                caveat="Hand snapshots are sampled at main phase; not all draws or playable windows are observed.")

def analyze_gameplay(snapshots, events):
    """Observed card availability and board development; no invented draw identities."""
    unique = {}
    for snapshot in snapshots:
        unique[(snapshot["turn"], snapshot["phase"], tuple(snapshot["hand"]),
                tuple(snapshot["battlefield"]))] = snapshot
    snapshots = sorted(unique.values(), key=lambda x: x["turn"])
    timeline = []
    first_seen = {}
    last_seen = {}
    for snap in snapshots:
        turn = snap["turn"]
        for card in snap["hand"]:
            first_seen.setdefault(card, turn)
            last_seen[card] = turn
        board = snap["battlefield"]
        timeline.append(dict(turn=turn, phase=snap["phase"],
                             hand_size=len(snap["hand"]),
                             battlefield_size=len(board),
                             life=snap.get("life"), creature_count=snap.get("creatures"),
                             lands=sum(c in ("Mountain", "Snow-Covered Mountain") for c in board),
                             rionya_on_battlefield="Rionya, Fire Dancer" in board,
                             board=board))
    played = {e["card"] for e in events if e["action"] in ("cast", "land")}
    return dict(board_timeline=timeline,
                first_observed_in_hand=first_seen,
                last_observed_in_hand=last_seen,
                observed_but_not_played=sorted(set(first_seen) - played),
                evidence_limitations=[
                    "First observed in hand is not necessarily draw turn",
                    "No unobserved draws or mana payments are inferred",
                    "Battlefield name snapshots do not distinguish token identity or counters",
                ])


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
        forge_text = source.read_text(errors="replace")
        report = audit(forge_text)
        authoritative = parse_authoritative_events(forge_text)
        smoke = source.with_name("smoke-test.log")
        if not smoke.exists():
            smoke = root.parent / "smoke-test.log"
        if smoke.exists():
            report["pilot_markers_in_lab_log"] = [m.group(1) for line in smoke.read_text(errors="replace").splitlines() if (m := MARKER.search(line))]
            report["pilot_markers_observed"] = report["pilot_markers_observed"] or bool(report["pilot_markers_in_lab_log"])
            report["pilot_marker_source"] = str(smoke)
        else:
            report["pilot_markers_in_lab_log"] = []
        lab_text = smoke.read_text(errors="replace") if smoke.exists() else ""
        authoritative.extend(parse_authoritative_events(lab_text))
        report["authoritative_events"] = authoritative
        report["authoritative_summary"] = authoritative_summary(authoritative)
        if report["authoritative_summary"]["opening_hand_cards"]:
            report["draw_identity_coverage"] = "authoritative: zone-transition events"
        report["state_snapshots"] = parse_snapshots(forge_text + "\n" + lab_text)
        report["card_exposure"] = card_exposure(report["state_snapshots"], report["cast_counts"])
        report["gameplay_analysis"] = analyze_gameplay(report["state_snapshots"], report["events"])
        if report["authoritative_summary"]["opening_hand_cards"]:
            report["unplayed_hand_cards"] = report["gameplay_analysis"]["observed_but_not_played"]
        report["telemetry_quality"] = {
            "has_hand_snapshots": bool(report["state_snapshots"]),
            "has_life_snapshots": any(x["life"] is not None for x in report["state_snapshots"]),
            "has_creature_snapshots": any(x["creatures"] is not None for x in report["state_snapshots"]),
            "has_battlefield_snapshots": bool(report["state_snapshots"]),
            "has_cast_events": bool(report["cast_counts"]),
            "has_land_events": bool(report["land_counts"]),
            "has_pilot_decisions": bool(report["pilot_markers_observed"]),
            "has_exact_draw_identity": bool(report["authoritative_summary"]["exact_draws"]) or bool(report["authoritative_summary"]["opening_hand_cards"]),
            "has_mulligan_events": "mulligan" in report["authoritative_summary"]["event_counts"] or bool(report["authoritative_summary"]["opening_hand_cards"]),
            "has_mana_payment_events": bool(report["authoritative_summary"]["mana_events"]),
            "complete_for_card_cut_recommendations": all([
                bool(report["authoritative_summary"]["opening_hand_cards"]),
                bool(report["authoritative_summary"]["snapshots"]),
                bool(report["authoritative_summary"]["event_counts"].get("zone")),
                bool(report["authoritative_summary"]["event_counts"].get("turn_begin")),
            ]),
        }
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
            {k: r[k] for k in ("game", "cast_counts", "commander_casts", "card_exposure", "gameplay_analysis", "authoritative_summary", "telemetry_quality", "rionya_copy_target_counts",
                               "pilot_markers_observed", "pilot_activation_confirmed", "commander_search_observed", "unidentified_draw_events")}
            for r in reports]}, indent=2) + "\n")
    if not reports:
        raise SystemExit("No game logs found for card audit")

if __name__ == "__main__":
    main()
