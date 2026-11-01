#!/usr/bin/env python3
"""Copies the 60 abstract backdrops into the app at screen size.

Needs Pillow. Usage: distro/tools/prepara-astratti.py
"""
import multiprocessing
import pathlib

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "assets/background-abstract-illustrated"
TARGET = ROOT / "home/app/src/main/assets/astratti"
SIZE = (1920, 1080)
QUALITY = 78
EXPECTED = 60


def shrink(source):
    target = TARGET / source.name
    picture = Image.open(source).convert("RGB").resize(SIZE, Image.LANCZOS)
    picture.save(target, "WEBP", quality=QUALITY, method=6)
    return target.stat().st_size


def main():
    sources = sorted(SOURCE.glob("*.webp"))
    if len(sources) != EXPECTED:
        raise SystemExit(f"Attese {EXPECTED} immagini, trovate {len(sources)}")
    # Written over, not emptied first: deleted and made again at once, Dropbox keeps conflicting copies.
    TARGET.mkdir(parents=True, exist_ok=True)
    for old in TARGET.glob("*.webp"):
        if not (SOURCE / old.name).exists():
            old.unlink()
    with multiprocessing.Pool() as pool:
        sizes = pool.map(shrink, sources)
    print(len(sizes), "immagini,", round(sum(sizes) / 1048576, 1), "MB")


if __name__ == "__main__":
    main()
