#!/usr/bin/env python3
"""Copies the backdrops that change on a timer into the app at screen size: 60 for each set.

Needs Pillow. Usage: distro/tools/prepara-astratti.py
"""
import multiprocessing
import pathlib

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "home/app/src/main/assets"
SETS = {ROOT / "assets/background-abstract-illustrated": ASSETS / "astratti",
        ROOT / "assets/background-nox-city": ASSETS / "nox-city"}
SIZE = (1920, 1080)
QUALITY = 78
EXPECTED = 60


def shrink(job):
    source, target = job
    picture = Image.open(source).convert("RGB").resize(SIZE, Image.LANCZOS)
    picture.save(target, "WEBP", quality=QUALITY, method=6)
    return target.stat().st_size


def copy_set(source_folder, target_folder, pool):
    sources = sorted(source_folder.glob("*.webp"))
    if len(sources) != EXPECTED:
        raise SystemExit(f"Attese {EXPECTED} immagini in {source_folder.name}, trovate {len(sources)}")
    # Written over, not emptied first: deleted and made again at once, Dropbox keeps conflicting copies.
    target_folder.mkdir(parents=True, exist_ok=True)
    for old in target_folder.glob("*.webp"):
        if not (source_folder / old.name).exists():
            old.unlink()
    sizes = pool.map(shrink, [(source, target_folder / source.name) for source in sources])
    print(target_folder.name, len(sizes), "immagini,", round(sum(sizes) / 1048576, 1), "MB")


def main():
    with multiprocessing.Pool() as pool:
        for source_folder, target_folder in SETS.items():
            copy_set(source_folder, target_folder, pool)


if __name__ == "__main__":
    main()
