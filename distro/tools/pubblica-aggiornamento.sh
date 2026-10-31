#!/usr/bin/env bash
# Publishes the built Salotto as a release of this repository: APK, signed update.json, error page.
# Usage: distro/tools/pubblica-aggiornamento.sh "first change" "second change" ...
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# Sticks up to 0.12.20 look only here: it gets the same files until none of them is left.
OLD_REPO="simplemal/salotto-tv-updates"
APK="$ROOT/home/.toolchain/app-build/outputs/apk/debug/app-debug.apk"
KEY="$ROOT/chiavi-aggiornamento/privata.pem"
CODE="$(sed -n "s/.*versionCode \([0-9]*\).*/\1/p" "$ROOT/home/app/build.gradle")"
NAME="$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" "$ROOT/home/app/build.gradle")"
REPO="$(sed -n 's/.*UPDATES_REPO = "\(.*\)".*/\1/p' "$ROOT/home/app/src/main/java/it/simplemal/firehome/Fire.java")"
[ -n "$REPO" ] || { echo "Fermo: repository non trovato in Fire.java."; exit 1; }
[ $# -gt 0 ] || { echo "Elenca le novità tra virgolette."; exit 2; }
if grep -rnE -f "$ROOT/distro/dati-personali.local" "$ROOT/home/app/src" > /dev/null; then
  echo "Fermo: dati personali nei sorgenti."; exit 1
fi

BADGING="$("$ROOT/home/.toolchain/android-sdk/build-tools/34.0.0/aapt2" dump badging "$APK" | head -1)"
case "$BADGING" in
  *"versionCode='$CODE' versionName='$NAME'"*) ;;
  *) echo "Fermo: l'APK non è la versione $NAME ($CODE), va ricompilato."; exit 1 ;;
esac

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK:?}"' EXIT
cp "$APK" "$WORK/salotto.apk"
python3 - "$WORK" "$CODE" "$NAME" "$REPO" "$@" <<'PY'
import hashlib, json, os, sys
work, code, name, repo, *changes = sys.argv[1:]
apk = os.path.join(work, "salotto.apk")
data = open(apk, "rb").read()
json.dump({"versionCode": int(code), "versionName": name,
           "url": f"https://github.com/{repo}/releases/download/v{name}/salotto.apk",
           "sha256": hashlib.sha256(data).hexdigest(), "size": len(data), "changes": changes},
          open(os.path.join(work, "update.json"), "w"), ensure_ascii=False, indent=2)
PY
openssl dgst -sha256 -sign "$KEY" -out "$WORK/update.json.sig" "$WORK/update.json"
openssl dgst -sha256 -verify <(openssl pkey -in "$KEY" -pubout 2>/dev/null) -signature "$WORK/update.json.sig" "$WORK/update.json"

# The page people reach from the QR is the one on main: it changes there when this branch arrives.
python3 "$ROOT/distro/tools/genera-errori.py" "$ROOT/ERRORI.md"
git -C "$ROOT" add ERRORI.md
git -C "$ROOT" diff --cached --quiet || git -C "$ROOT" commit -q -m "Errori per la versione $NAME"
git -C "$ROOT" push -q origin HEAD
for target in "$REPO" "$OLD_REPO"; do
  gh release create "v$NAME" "$WORK/salotto.apk" "$WORK/update.json" "$WORK/update.json.sig" \
    --repo "$target" --title "Salotto $NAME" --notes "$(printf -- '- %s\n' "$@")" --latest
done
echo "Pubblicata la versione $NAME ($CODE)"
