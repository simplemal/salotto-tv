#!/usr/bin/env python3
"""Copies the backdrops that change on a timer into the app at screen size: 60 for each set.

The dithered sets stay lossless, with the dithering made again at the new size: lossy WebP smooths
it away and the banding comes back. They take about 1.5 MB each against 40 KB.

Needs Pillow and numpy. Usage: distro/tools/prepara-astratti.py [only this set, as astratti-3d]
"""
import multiprocessing
import pathlib
import sys

import numpy
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "home/app/src/main/assets"
SETS = {ROOT / "assets/background-abstract-illustrated": ASSETS / "astratti",
        ROOT / "assets/background-abstract-3d": ASSETS / "astratti-3d",
        ROOT / "assets/background-nox-city": ASSETS / "nox-city",
        ROOT / "assets/background-aereal": ASSETS / "natura-aerea"}
DITHERED = {ASSETS / "astratti-3d"}
SIZE = (1920, 1080)
QUALITY = 78
EXPECTED = 60


def dithered(picture):
    """Averages in floating point, so no steps appear, and adds one level of noise before rounding."""
    values = numpy.asarray(picture, dtype=numpy.float32)
    channels = [numpy.asarray(Image.fromarray(values[..., index], mode="F").resize(SIZE, Image.BOX), dtype=numpy.float32)
                for index in range(3)]
    smooth = numpy.stack(channels, -1)
    noise = numpy.random.default_rng(len(smooth.tobytes()))
    triangular = noise.random(smooth.shape, dtype=numpy.float32) - noise.random(smooth.shape, dtype=numpy.float32)
    return Image.fromarray(numpy.clip(numpy.round(smooth + triangular), 0, 255).astype(numpy.uint8))


def shrink(job):
    source, target = job
    picture = Image.open(source).convert("RGB")
    if target.parent in DITHERED:
        dithered(picture).save(target, "WEBP", lossless=True, method=6)
    else:
        picture.resize(SIZE, Image.LANCZOS).save(target, "WEBP", quality=QUALITY, method=6)
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
    only = sys.argv[1:]
    with multiprocessing.Pool() as pool:
        for source_folder, target_folder in SETS.items():
            if not only or target_folder.name in only:
                copy_set(source_folder, target_folder, pool)


if __name__ == "__main__":
    main()
