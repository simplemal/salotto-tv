#!/usr/bin/env python3
"""Copies the 120 aerial weather pictures into the app at screen size, with their table.

The manifest gives each one a WMO code, a temperature and day or night. The app has six phases and
the set only two, so the sun above the horizon counts as day. Pictures of one code and phase are all
listed: the app takes the one nearest the temperature, which it reads from the file name.
Needs cwebp. Usage: distro/tools/prepara-meteo-aereo.py
"""
import json
import multiprocessing
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "assets/background-meteo-aereal"
TARGET = ROOT / "home/sfondi/meteo-aereo"
WIDTH, HEIGHT = 1920, 1080
QUALITY = 74
EXPECTED = 120
PHASES = {"dawn": 1, "day": 1, "afternoon": 1, "sunset": 1, "evening": 0, "night": 0}


def shrink(name):
    target = TARGET / name
    subprocess.run(["cwebp", "-quiet", "-m", "6", "-q", str(QUALITY), "-resize", str(WIDTH), str(HEIGHT),
                    str(SOURCE / name), "-o", str(target)], check=True)
    return target.stat().st_size


def main():
    assets = json.loads((SOURCE / "manifest.json").read_text(encoding="utf-8"))["assets"]
    if len(assets) != EXPECTED:
        raise SystemExit(f"Attese {EXPECTED} immagini, trovate {len(assets)}")
    table = {}
    for asset in assets:
        table.setdefault((asset["is_day"], asset["weather_code"]), []).append(asset["file"])
    # Written over, not emptied first: deleted and made again at once, Dropbox keeps conflicting copies.
    TARGET.mkdir(parents=True, exist_ok=True)
    with multiprocessing.Pool() as pool:
        sizes = pool.map(shrink, [asset["file"] for asset in assets])
    lines = []
    for phase, day in PHASES.items():
        lines += [f"{phase}/{code}={','.join(files)}" for (is_day, code), files in table.items() if is_day == day]
    (TARGET / "tabella.properties").write_text("\n".join(sorted(lines)) + "\n", encoding="ascii")
    print(len(sizes), "immagini,", round(sum(sizes) / 1048576, 1), "MB,", len(lines), "righe")


if __name__ == "__main__":
    main()
