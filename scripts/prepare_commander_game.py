#!/usr/bin/env python3
"""Create per-game directories and record a replay seed.

Default: fresh cryptographically random seed per game.
Optional SEED_BASE: reproducible test suite, seed = base + game number.
"""
import argparse
import json
import os
from pathlib import Path
import secrets

def prepare(game: int, results: Path, diagnostics: Path, seed_base: str | None = None) -> int:
    if game < 1:
        raise ValueError("game must be positive")
    game_results = results / f"game-{game}"
    game_diagnostics = diagnostics / f"game-{game}"
    game_results.mkdir(parents=True, exist_ok=True)
    game_diagnostics.mkdir(parents=True, exist_ok=True)
    if seed_base is None or seed_base == "":
        seed = secrets.randbits(63)
        mode = "random"
    else:
        seed = int(seed_base) + game
        if not 0 <= seed < (1 << 63):
            raise ValueError("seed out of range")
        mode = "fixed"
    (game_results / "replay-seed.txt").write_text(f"{seed}\n")
    (game_results / "replay-metadata.json").write_text(json.dumps(
        {"game": game, "seed": seed, "mode": mode}, indent=2) + "\n")
    return seed

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--game", type=int, required=True)
    parser.add_argument("--results", type=Path, default=Path("results"))
    parser.add_argument("--diagnostics", type=Path, default=Path("diagnostics"))
    parser.add_argument("--seed-base", default=os.environ.get("SEED_BASE"))
    args = parser.parse_args()
    print(prepare(args.game, args.results, args.diagnostics, args.seed_base))

if __name__ == "__main__":
    main()
