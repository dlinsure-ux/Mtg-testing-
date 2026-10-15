#!/usr/bin/env python3
"""Observe meaningful Forge game progress; report evidence, never kill the game."""
import argparse
import json
import re
import time
from pathlib import Path

def progress_markers(text):
    turns=re.findall(r"^(?:Turn: (?:Turn )?|Game Outcome: Turn )(\d+)\b",text,re.M)
    actions=re.findall(r"^(?:Add To Stack:|Resolve Stack:|Combat:|Damage:|Phase:|Game Outcome:|Game Result:)",text,re.M)
    terminal=bool(re.search(r"^(?:Game Result: Game \d+ ended in|Game Outcome: Ai\(\d+\)-.+ has won (?:because|due to effect of))",text,re.M))
    terminal_turns=re.findall(r"^Game Outcome: Turn (\d+)\b",text,re.M)
    return {"last_turn":int(terminal_turns[-1]) if terminal_turns else (int(turns[-1]) if turns else None),
            "turn_markers":len(turns),"action_markers":len(actions),
            "terminal_result":terminal}

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--log",required=True)
    p.add_argument("--output",required=True)
    p.add_argument("--fallback-log",action="append",default=[])
    p.add_argument("--interval",type=int,default=60)
    p.add_argument("--stale-seconds",type=int,default=300)
    p.add_argument("--max-seconds",type=int,default=900)
    args=p.parse_args()
    started=time.monotonic()
    last_change=started
    previous=None
    samples=[]
    while time.monotonic()-started < args.max_seconds:
        candidates=[Path(args.log)]+[Path(x) for x in args.fallback_log]
        observed=[(path,progress_markers(path.read_text(errors="replace"))) for path in candidates if path.is_file()]
        source,markers=max(observed,key=lambda item:(item[1]["action_markers"],item[1]["turn_markers"])) if observed else (None,progress_markers(""))
        signature=(str(source),markers["last_turn"],markers["turn_markers"],markers["action_markers"],markers["terminal_result"])
        now=time.monotonic()
        if signature!=previous:
            last_change=now
            previous=signature
        stale=bool(observed) and now-last_change>=args.stale_seconds
        samples.append({"elapsed_seconds":round(now-started,1),"stale_seconds":round(now-last_change,1),**markers,"source_log":str(source) if source else None,"possible_stall":stale})
        Path(args.output).parent.mkdir(parents=True,exist_ok=True)
        Path(args.output).write_text(json.dumps({"samples":samples,"warning":"Stall suspicion is not proof of deadlock"},indent=2)+"\n")
        if markers["terminal_result"]: return
        time.sleep(args.interval)

if __name__=="__main__":
    main()
