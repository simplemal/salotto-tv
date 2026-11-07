#!/usr/bin/env python3
"""Copies the 120 weather photos into the app at screen size, with their table.

The photos have no manifest: phase and WMO code come from each file's name. They cover four phases,
and the app has six, so afternoon uses day and evening uses night. A code with two photos lists both.
Needs Pillow. Usage: distro/tools/prepara-meteo-foto.py
"""
import multiprocessing
import pathlib
import re

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "assets/background-meteo-photos"
TARGET = ROOT / "home/sfondi/meteo-foto"
SIZE = (1920, 1080)
QUALITY = 78
EXPECTED = 120
NAME = re.compile(r"weather_([a-z]+)_wmo_(\d+)_")
PHASES = {"dawn": "dawn", "day": "day", "afternoon": "day", "sunset": "sunset", "evening": "night", "night": "night"}


def shrink(source):
    target = TARGET / source.name
    picture = Image.open(source).convert("RGB").resize(SIZE, Image.LANCZOS)
    picture.save(target, "WEBP", quality=QUALITY, method=6)
    return target.stat().st_size


def main():
    sources = sorted(SOURCE.glob("*.webp"))
    if len(sources) != EXPECTED:
        raise SystemExit(f"Attese {EXPECTED} immagini, trovate {len(sources)}")
    table = {}
    for source in sources:
        match = NAME.match(source.name)
        if not match:
            raise SystemExit(f"Nome inatteso: {source.name}")
        table.setdefault((match[1], match[2]), []).append(source.name)
    # Written over, not emptied first: deleted and made again at once, Dropbox keeps conflicting copies.
    TARGET.mkdir(parents=True, exist_ok=True)
    for old in TARGET.glob("*.webp"):
        if not (SOURCE / old.name).exists():
            old.unlink()
    with multiprocessing.Pool() as pool:
        sizes = pool.map(shrink, sources)
    lines = []
    for phase, shown in PHASES.items():
        lines += [f"{phase}/{code}={','.join(files)}" for (kind, code), files in table.items() if kind == shown]
    (TARGET / "tabella.properties").write_text("\n".join(sorted(lines)) + "\n", encoding="ascii")
    print(len(sizes), "immagini,", round(sum(sizes) / 1048576, 1), "MB,", len(lines), "righe")


if __name__ == "__main__":
    main()
