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
    d=json.loads(p.read_text())
    if d.get('classification')!='VALID' or d.get('errors'):
        raise SystemExit(f'INVALID game {n}: {d.get("errors")}')
    lab=d.get('lab_result',[])
    if len(lab)!=1 or len(lab[0])!=3:
        raise SystemExit(f'INVALID game {n}: missing unique Lab winner')
    winner,turns,ms=lab[0]
    if not winner or winner=='DRAW':
        raise SystemExit(f'INVALID game {n}: no terminal winner')
    rows.append({'game':n,'winner_deck':winner,'turns':int(turns),'duration_ms':int(ms),
                 'startup_ai_timeouts':d.get('startup_ai_decision_timeouts',0),
                 'in_game_ai_timeouts':d.get('in_game_ai_decision_timeouts',0)})
report={'classification':'VALID','games':rows,'lathiel_wins':sum('lathiel.dck' in r['winner_deck'] for r in rows)}
out=root/'series-audit.json'
out.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
