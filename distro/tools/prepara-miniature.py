#!/usr/bin/env python3
"""Makes the small copies the backdrop grid shows, from the pictures already in the app.

Run it after prepara-astratti.py. Without them the grid still works, but takes seconds to fill.
Needs Pillow. Usage: distro/tools/prepara-miniature.py
"""
import multiprocessing
import pathlib

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "home/app/src/main/assets"
SETS = ("astratti", "astratti-3d", "nox-city", "natura-aerea")
TARGET = ASSETS / "miniature"
# The cell of PictureGridActivity on the screen, so that nothing is enlarged or shrunk again.
SIZE = (432, 243)
QUALITY = 70


def shrink(job):
    source, target = job
    Image.open(source).convert("RGB").resize(SIZE, Image.LANCZOS).save(target, "WEBP", quality=QUALITY, method=6)
    return target.stat().st_size


def main():
    with multiprocessing.Pool() as pool:
        for name in SETS:
            folder = TARGET / name
            folder.mkdir(parents=True, exist_ok=True)
            sources = sorted((ASSETS / name).glob("*.webp"))
            for old in folder.glob("*.webp"):
                if not (ASSETS / name / old.name).exists():
                    old.unlink()
            sizes = pool.map(shrink, [(source, folder / source.name) for source in sources])
            print(name, len(sizes), "miniature,", round(sum(sizes) / 1024), "KB")


if __name__ == "__main__":
    main()
