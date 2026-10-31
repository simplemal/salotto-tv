#!/usr/bin/env python3
"""Copies the weather scenes into the app and turns the animation tables into a small file.

The app reads only what it needs, so a change to the graphics or to the tables means running this
again. Usage: distro/tools/prepara-meteo.py
"""
import pathlib
import re
import shutil

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "assets/background-meteo-anime"
TARGET = ROOT / "home/app/src/main/assets/meteo"
ROW = re.compile(r"^\| \[(\w+) / (\w+)\]\([^)]+\) \| [^|]+ \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$")


def main():
    if TARGET.exists():
        shutil.rmtree(TARGET)
    TARGET.mkdir(parents=True)
    for phase in sorted((SOURCE / "backgrounds").iterdir()):
        if not phase.is_dir():
            continue
        (TARGET / phase.name).mkdir()
        for scene in phase.glob("*.svg"):
            condition = scene.stem.split("_", 2)[2]
            text = scene.read_text(encoding="utf-8")
            text = re.sub(r"<title.*?</title>|<metadata>.*?</metadata>", "", text, flags=re.S)
            (TARGET / phase.name / f"{condition}.svg").write_text(text, encoding="utf-8")
    rows = []
    for line in (SOURCE / "WEATHER_ANIMATION_MATHEMATICS.md").read_text(encoding="utf-8").splitlines():
        match = ROW.match(line)
        if match:
            values = [value.strip().replace("—", "0") for value in match.groups()]
            # Phase, condition and speed: the only columns WeatherScene reads.
            rows.append(",".join((values[0], values[1], values[8])))
    if len(rows) != 120:
        raise SystemExit(f"Attese 120 righe, trovate {len(rows)}")
    (TARGET / "parametri.csv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(len(rows), "scene")


if __name__ == "__main__":
    main()
