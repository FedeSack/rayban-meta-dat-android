#!/usr/bin/env python3
from pathlib import Path
import sys

from PIL import Image, ImageChops, ImageStat

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "visual-baseline"
OUT = ROOT / "tools" / "visual-parity" / "out"

PAIRS = (
    ("connect_connected.png", "connect_connected.png"),
    ("connect_disconnected.png", "connect_disconnected.png"),
    ("live_preview.png", "live_preview.png"),
)


def score(baseline: Path, candidate: Path, dest: Path) -> float:
    a = Image.open(baseline).convert("RGB")
    b = Image.open(candidate).convert("RGB")
    if a.size != b.size:
        b = b.resize(a.size, Image.Resampling.NEAREST)
    diff = ImageChops.difference(a, b)
    dest.parent.mkdir(parents=True, exist_ok=True)
    diff.save(dest)
    stat = ImageStat.Stat(diff)
    return sum(stat.mean) / 3.0


def main() -> int:
    failed = 0
    for base_name, cand_name in PAIRS:
        baseline = BASE / base_name
        candidate = OUT / cand_name
        if not candidate.exists():
            print(f"MISSING {candidate}")
            failed += 1
            continue
        mean = score(baseline, candidate, OUT / f"diff_{base_name}")
        print(f"{base_name} mean_abs={mean:.3f}")
        if mean > 8.0:
            failed += 1
    return failed


if __name__ == "__main__":
    sys.exit(main())
