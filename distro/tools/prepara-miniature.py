#!/usr/bin/env python3
"""Makes the small copies the backdrop grid and mosaic show, from the pictures already in the app.

Run it after prepara-astratti.py. Without them the grid still works, but takes seconds to fill.
Needs Pillow. Usage: distro/tools/prepara-miniature.py
"""
import itertools
import multiprocessing
import pathlib

from PIL import Image, ImageOps

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "home/app/src/main/assets"
SETS = ("astratti", "astratti-3d", "nox-city", "natura-aerea", "meteo-illustrato", "meteo-foto", "meteo-aereo")
TARGETS = {ASSETS / "miniature": ((432, 244), 70), ASSETS / "miniature-piccole": ((108, 61), 55)}
# The cell of PictureGridActivity on the screen, pixel for pixel. It is not quite 16:9, so a line of
# the picture goes at the edges: enlarged instead, on the Stick, the whole picture would go soft.


def shrink(job):
    source, target, size, quality = job
    picture = ImageOps.fit(Image.open(source).convert("RGB"), size, Image.LANCZOS)
    picture.save(target, "WEBP", quality=quality, method=6)
    return target.stat().st_size


def main():
    with multiprocessing.Pool() as pool:
        for (top, (size, quality)), name in itertools.product(TARGETS.items(), SETS):
            folder = top / name
            sources = sorted((ASSETS / name).rglob("*.webp"))
            for old in folder.rglob("*.webp"):
                if not (ASSETS / name / old.relative_to(folder)).exists():
                    old.unlink()
            targets = [folder / source.relative_to(ASSETS / name) for source in sources]
            for target in targets:
                target.parent.mkdir(parents=True, exist_ok=True)
            sizes = pool.map(shrink, [(source, target, size, quality) for source, target in zip(sources, targets)])
            print(top.name, name, len(sizes), "immagini,", round(sum(sizes) / 1024), "KB")


if __name__ == "__main__":
    main()
