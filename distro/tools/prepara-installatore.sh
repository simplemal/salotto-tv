#!/usr/bin/env bash
# Copies the built installer into a folder under names that never change, so the download links
# stay the same: .../releases/latest/download/Installa-Salotto-Mac.dmg
# Stops if a Mac package is not notarized. Usage: distro/tools/prepara-installatore.sh FOLDER
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIST="$ROOT/../installatore/dist"
TARGET="$1"
mkdir -p "$TARGET"

shopt -s nullglob
one() {
  local IFS=
  local found=("$DIST"/$1)
  [ ${#found[@]} -gt 0 ] && cp "${found[0]}" "$TARGET/$2"
}

one 'Installa Salotto-*-arm64.dmg' Installa-Salotto-Mac.dmg || { echo "Fermo: manca il pacchetto Mac in installatore/dist, va compilato."; exit 1; }
# Built only on a computer that can run their tools: when they are there, they go too.
one 'Installa Salotto Setup *.exe' Installa-Salotto-Windows.exe || true
one 'Installa Salotto-*.AppImage' Installa-Salotto-Linux.AppImage || true
for image in "$TARGET"/*.dmg; do
  xcrun stapler validate "$image" > /dev/null || { echo "Fermo: $(basename "$image") non è notarizzato."; exit 1; }
done
