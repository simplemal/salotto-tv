#!/usr/bin/env bash
# Builds the release zip: scripts, lists, guides and the Salotto APK. It first refuses anything
# that looks personal, in the distro folder and in Salotto's sources.
#
# Usage: distro/tools/prepara-rilascio.sh
set -uo pipefail

DISTRO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$(dirname "$DISTRO")"
APK="$ROOT/home/.toolchain/app-build/outputs/apk/debug/app-debug.apk"
VERSION="$(tr -d '[:space:]' < "$DISTRO/VERSIONE")"
OUT="$ROOT/rilasci/salotto-distro-$VERSION"

# One pattern per line, never committed: names, addresses and serials of your own devices.
PERSONAL="$DISTRO/dati-personali.local"
[[ -f "$PERSONAL" ]] || { echo "Fermo: manca $PERSONAL, con i dati da cercare."; exit 1; }
if grep -rnE -f "$PERSONAL" "$DISTRO" "$ROOT/home/app/src" --exclude-dir=scaricati --exclude=dati-personali.local; then
  echo "Fermo: dati personali trovati, toglili prima del rilascio."
  exit 1
fi
[[ -f "$APK" ]] || { echo "Fermo: manca l'APK di Salotto, compila prima."; exit 1; }

rm -rf "$OUT" "$OUT.zip"
mkdir -p "$OUT"
cp -R "$DISTRO/README.md" "$DISTRO/VERSIONE" "$DISTRO/install.sh" "$DISTRO/disinstalla.sh" \
  "$DISTRO/config" "$DISTRO/docs" "$OUT/"
cp "$APK" "$OUT/salotto.apk"
(cd "$OUT" && shasum -a 256 salotto.apk install.sh disinstalla.sh config/* > SHA256SUMS.txt)
(cd "$(dirname "$OUT")" && zip -qr "$(basename "$OUT").zip" "$(basename "$OUT")")
rm -rf "$OUT"
shasum -a 256 "$OUT.zip"
echo "Pronto: $OUT.zip"
