#!/usr/bin/env python3
"""Makes the small copies of the backdrop collections, and the still collage of each that the app carries.

The small copies go into home/miniature, one folder for each collection: pubblica-sfondi.sh puts
each in an archive of its own, which Salotto downloads with the collection, or alone for one
already on the TV. Without them the grid and the mosaic still work, but take seconds to fill.
The collage is 15 pictures of the collection side by side, which the first start shows, still,
for a collection not yet downloaded: it goes inside the app, in assets/collage.
Needs Pillow. Usage: distro/tools/prepara-miniature.py
"""
import multiprocessing
import pathlib
import shutil

from PIL import Image, ImageOps

ROOT = pathlib.Path(__file__).resolve().parents[2]
PACKS = ROOT / "home/sfondi"
OUT = ROOT / "home/miniature"
COLLAGE = ROOT / "home/app/src/main/assets/collage"
# The same order and names as Packs.ALL in Salotto.
SETS = ("meteo-illustrato", "meteo-foto", "meteo-aereo", "astratti", "astratti-3d", "nox-city", "natura-aerea")
# The cell of PictureGridActivity on the screen, pixel for pixel. It is not quite 16:9, so a line of
# the picture goes at the edges: enlarged instead, on the Stick, the whole picture would go soft.
SIZES = {"grandi": ((432, 244), 90), "piccole": ((108, 61), 55)}
# As many as the tiles of PictureMosaic, each a little smaller than a tile on a 1080p screen:
# under the page's veil the difference does not show, and the seven together stay small.
TILES, TILE, COLUMNS, COLLAGE_QUALITY = 15, (320, 180), 5, 72


def folder_of(pack):
    return OUT / f"miniature-{pack}"


def shrink(job):
    source, target, size, quality = job
    picture = ImageOps.fit(Image.open(source).convert("RGB"), size, Image.LANCZOS)
    picture.save(target, "WEBP", quality=quality, method=6)
    return target.stat().st_size


def collage(pack, sources):
    """Spread over the whole collection: for the weather, every moment of the day."""
    picked = [sources[round(index * (len(sources) - 1) / (TILES - 1))] for index in range(TILES)]
    rows = (TILES + COLUMNS - 1) // COLUMNS
    sheet = Image.new("RGB", (COLUMNS * TILE[0], rows * TILE[1]))
    for index, source in enumerate(picked):
        tile = ImageOps.fit(Image.open(source).convert("RGB"), TILE, Image.LANCZOS)
        sheet.paste(tile, (index % COLUMNS * TILE[0], index // COLUMNS * TILE[1]))
    COLLAGE.mkdir(parents=True, exist_ok=True)
    target = COLLAGE / f"{pack}.webp"
    sheet.save(target, "WEBP", quality=COLLAGE_QUALITY, method=6)
    return target.stat().st_size


def main():
    with multiprocessing.Pool() as pool:
        for name in SETS:
            sources = sorted(path for path in (PACKS / name).rglob("*")
                             if path.suffix.lower() in (".webp", ".jpg", ".jpeg", ".png") and not path.name.startswith("."))
            if not sources:
                raise SystemExit(f"Fermo: nessuna immagine in {PACKS / name}.")
            shutil.rmtree(folder_of(name), ignore_errors=True)
            for kind, (size, quality) in SIZES.items():
                targets = [folder_of(name) / kind / source.relative_to(PACKS / name).with_suffix(".webp") for source in sources]
                for target in targets:
                    target.parent.mkdir(parents=True, exist_ok=True)
                sizes = pool.map(shrink, [(source, target, size, quality) for source, target in zip(sources, targets)])
                print(name, kind, len(sizes), "immagini,", round(sum(sizes) / 1024), "KB")
            print(name, "collage,", round(collage(name, sources) / 1024), "KB")


if __name__ == "__main__":
    main()
