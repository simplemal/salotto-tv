#!/usr/bin/env python3
"""Copies the 120 weather illustrations into the app at screen size, with the manifest's table.

The app never builds a file name: it looks up phase/WMO code in tabella.properties, which comes
straight from the manifest. Needs Pillow. Usage: distro/tools/prepara-meteo-illustrato.py
"""
import hashlib
import json
import multiprocessing
import pathlib
import shutil

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "assets/background-meteo-anime-raster"
TARGET = ROOT / "home/app/src/main/assets/meteo-illustrato"
SIZE = (1920, 1080)
QUALITY = 70
EXPECTED = 120


def shrink(image):
    source = SOURCE / image["file"]
    if hashlib.sha256(source.read_bytes()).hexdigest() != image["sha256"]:
        raise SystemExit(f"Impronta diversa: {image['file']}")
    target = TARGET / image["file"]
    picture = Image.open(source).convert("RGB").resize(SIZE, Image.LANCZOS)
    picture.save(target, "WEBP", quality=QUALITY, method=6)
    return target.stat().st_size


def main():
    manifest = json.loads((SOURCE / "WEATHER_RASTER_MANIFEST.json").read_text(encoding="utf-8"))
    images = manifest["images"]
    if len(images) != EXPECTED:
        raise SystemExit(f"Attese {EXPECTED} immagini, trovate {len(images)}")
    if TARGET.exists():
        shutil.rmtree(TARGET)
    for phase in manifest["phases"]:
        (TARGET / phase["id"]).mkdir(parents=True)
    with multiprocessing.Pool() as pool:
        sizes = pool.map(shrink, images)
    lines = []
    for table in ("lookupByPhaseAndWeather", "lookupByPhaseAndWmo"):
        for phase, entries in manifest[table].items():
            lines += [f"{phase}/{key}={file}" for key, file in entries.items()]
    missing = [line for line in lines if not (TARGET / line.split("=", 1)[1]).is_file()]
    if missing:
        raise SystemExit(f"File assenti: {missing[:3]}")
    (TARGET / "tabella.properties").write_text("\n".join(sorted(lines)) + "\n", encoding="ascii")
    print(len(sizes), "immagini,", round(sum(sizes) / 1048576, 1), "MB,", len(lines), "righe")


if __name__ == "__main__":
    main()
