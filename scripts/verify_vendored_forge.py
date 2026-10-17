#!/usr/bin/env python3
"""Verify the vendored Forge snapshot against its immutable SHA-256 manifest."""
from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path

EXPECTED_COMMIT = "edb28158f6d892f9eb43dc08f019aab567276027"

def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def verify(root: Path, metadata: Path) -> dict:
    manifest = json.loads((metadata / "sha256-manifest.json").read_text())
    commit = (metadata / "upstream-commit.txt").read_text().strip()
    if commit != EXPECTED_COMMIT or manifest.get("upstream_commit") != EXPECTED_COMMIT:
        raise SystemExit(f"Unexpected Forge commit: metadata={commit} manifest={manifest.get('upstream_commit')}")
    expected = {row["path"]: row for row in manifest["files"]}
    actual = {p.relative_to(root).as_posix(): p for p in root.rglob("*") if p.is_file()}
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    if missing or extra:
        raise SystemExit(f"Vendored Forge file set drift: missing={missing[:10]} extra={extra[:10]}")
    bad = []
    total = 0
    for rel, row in expected.items():
        p = actual[rel]
        total += p.stat().st_size
        if p.stat().st_size != row["bytes"] or sha256_file(p) != row["sha256"]:
            bad.append(rel)
            if len(bad) >= 10:
                break
    if bad:
        raise SystemExit(f"Vendored Forge content drift: {bad}")
    return {"commit": commit, "files": len(expected), "bytes": total}

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default="vendor/forge")
    ap.add_argument("--metadata", default="vendor/forge-metadata")
    args = ap.parse_args()
    result = verify(Path(args.root), Path(args.metadata))
    print(f"VENDORED_FORGE_OK commit={result['commit']} files={result['files']} bytes={result['bytes']}")

if __name__ == "__main__":
    main()
