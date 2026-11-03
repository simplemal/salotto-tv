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

one 'Installa-Salotto-*-arm64.dmg' Installa-Salotto-Mac.dmg || { echo "Fermo: manca il pacchetto Mac in installatore/dist, va compilato."; exit 1; }
# What the installer updates itself from: latest-mac.yml names the zip, so that name stays.
for part in "$DIST"/Installa-Salotto-*-arm64.zip "$DIST"/latest-mac.yml; do
  cp "$part" "$TARGET/"
done
# Built only on a computer that can run their tools: when they are there, they go too.
# Windows updates itself from latest.yml, so the exe goes only with it and only at the Mac version.
# The yml then names the fixed name, so one copy of the exe serves both the link and the update.
version() { sed -n 's/^version: //p' "$1"; }
if [ -f "$DIST/latest.yml" ] && [ "$(version "$DIST/latest.yml")" = "$(version "$DIST/latest-mac.yml")" ]; then
  exe="$(sed -n 's/^path: //p' "$DIST/latest.yml")"
  cp "$DIST/$exe" "$TARGET/Installa-Salotto-Windows.exe"
  sed "s|$exe|Installa-Salotto-Windows.exe|" "$DIST/latest.yml" > "$TARGET/latest.yml"
else
  echo "Senza Windows: in installatore/dist manca, o è di un'altra versione."
fi
one 'Installa Salotto-*.AppImage' Installa-Salotto-Linux.AppImage || true
for image in "$TARGET"/*.dmg; do
  xcrun stapler validate "$image" > /dev/null || { echo "Fermo: $(basename "$image") non è notarizzato."; exit 1; }
done
