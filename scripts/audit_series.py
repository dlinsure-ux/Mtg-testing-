#!/usr/bin/env python3
"""Fail-closed summary of five independently validated game reports."""
import json
import sys
from pathlib import Path

root=Path(sys.argv[1] if len(sys.argv)>1 else 'results')
rows=[]
for n in range(1,6):
    p=root/f'game-{n}'/'validation-report.json'
    if not p.is_file():
        raise SystemExit(f'MISSING game {n} validation report: {p}')
    try:
        d=json.loads(p.read_text())
    except (ValueError, OSError) as exc:
        raise SystemExit(f'INVALID game {n}: unreadable JSON: {exc}')
    if not isinstance(d,dict): raise SystemExit(f'INVALID game {n}: report must be object')
    if d.get('classification')!='VALID' or d.get('errors'):
        raise SystemExit(f'INVALID game {n}: {d.get("errors")}')
    lab=d.get('lab_result',[])
    if not isinstance(lab,list) or len(lab)!=1 or not isinstance(lab[0],list) or len(lab[0])!=3:
        raise SystemExit(f'INVALID game {n}: missing unique Lab winner')
    winner,turns,ms=lab[0]
    if not isinstance(winner,str) or not winner.endswith('.dck') or winner=='DRAW':
        raise SystemExit(f'INVALID game {n}: no terminal winner')
    if d.get('in_game_ai_decision_timeouts',0) or d.get('whole_game_timeout') or d.get('unsupported_cards'):
        raise SystemExit(f'INVALID game {n}: forbidden timeout or unsupported cards')
    forge=d.get('forge_result')
    outcomes=d.get('forge_winning_outcomes')
    if not isinstance(forge,list) or len(forge)!=1 or not isinstance(forge[0],list) or len(forge[0])!=3:
        raise SystemExit(f'INVALID game {n}: missing unique Forge final winner')
    if not isinstance(outcomes,list) or len(outcomes)!=1 or not isinstance(outcomes[0],list) or len(outcomes[0])!=4:
        raise SystemExit(f'INVALID game {n}: missing unique Forge winning outcome')
    try:
        turn_num, duration=int(turns),int(ms)
        forge_ms, forge_seat, forge_name=forge[0]
        outcome_seat, outcome_name=outcomes[0][:2]
        if turn_num<1 or duration<=0 or abs(duration-int(forge_ms))>1000 or not (1<=int(forge_seat)<=4): raise ValueError()
        if str(forge_seat)!=str(outcome_seat) or forge_name!=outcome_name or not forge_name: raise ValueError()
    except (ValueError,TypeError,OverflowError):
        raise SystemExit(f'INVALID game {n}: inconsistent terminal evidence')
    rows.append({'game':n,'winner_deck':winner,'turns':turn_num,'duration_ms':duration,
                 'startup_ai_timeouts':d.get('startup_ai_decision_timeouts',0),
                 'in_game_ai_timeouts':d.get('in_game_ai_decision_timeouts',0)})
report={'classification':'VALID','games':rows,'lathiel_wins':sum('lathiel.dck' in r['winner_deck'] for r in rows)}
out=root/'series-audit.json'
out.write_text(json.dumps(report,indent=2)+chr(10))
print(json.dumps(report,indent=2))
