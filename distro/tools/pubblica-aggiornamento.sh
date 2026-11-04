#!/usr/bin/env bash
# Publishes the built Salotto as a release of this repository: APK, signed update.json, error page,
# and next to them the latest installer, which is what people download.
# Usage: distro/tools/pubblica-aggiornamento.sh "first change" "second change" ...
# The changes are in Italian; distro/novita.json holds them in every other language of the app, for
# this same version, and each TV shows its own. Older versions read only the Italian list.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APK="$ROOT/home/.toolchain/app-build/outputs/apk/debug/app-debug.apk"
KEY="$ROOT/chiavi-aggiornamento/privata.pem"
# The certificate of firma-salotto.jks, which is public: every Stick has it. A build with any other
# key cannot update them.
CERTIFICATE="864ae0a359c87748a250d8a9185072854c2d6fb94367bcf517abdf0887a2feb2"
CODE="$(sed -n "s/.*versionCode \([0-9]*\).*/\1/p" "$ROOT/home/app/build.gradle")"
NAME="$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" "$ROOT/home/app/build.gradle")"
REPO="$(sed -n 's/.*UPDATES_REPO = "\(.*\)".*/\1/p' "$ROOT/home/app/src/main/java/it/simplemal/firehome/Fire.java")"
[ -n "$REPO" ] || { echo "Fermo: repository non trovato in Fire.java."; exit 1; }
[ $# -gt 0 ] || { echo "Elenca le novità tra virgolette."; exit 2; }
if grep -rnE -f "$ROOT/distro/dati-personali.local" "$ROOT/home/app/src" > /dev/null; then
  echo "Fermo: dati personali nei sorgenti."; exit 1
fi

BADGING="$("$ROOT/home/.toolchain/android-sdk/build-tools/34.0.0/aapt2" dump badging "$APK" | sed -n 1p)"
case "$BADGING" in
  *"versionCode='$CODE' versionName='$NAME'"*) ;;
  *) echo "Fermo: l'APK non è la versione $NAME ($CODE), va ricompilato."; exit 1 ;;
esac

TOOLS="$ROOT/home/.toolchain"
SIGNER="$(JAVA_HOME="$(echo "$TOOLS"/jdk-*/Contents/Home)" "$TOOLS/android-sdk/build-tools/34.0.0/apksigner" verify --print-certs "$APK" 2>/dev/null | sed -n 's/.*certificate SHA-256 digest: //p' | sed -n 1p)"
[ "$SIGNER" = "$CERTIFICATE" ] || { echo "Fermo: l'APK non ha la firma definitiva."; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK:?}"' EXIT
cp "$APK" "$WORK/salotto.apk"
"$ROOT/distro/tools/prepara-installatore.sh" "$WORK/installatore"
python3 - "$WORK" "$CODE" "$NAME" "$REPO" "$ROOT" "$@" <<'PY'
import hashlib, json, os, re, sys
work, code, name, repo, root, *changes = sys.argv[1:]
java = open(os.path.join(root, "home/app/src/main/java/it/simplemal/firehome/Language.java"), encoding="utf-8").read()
codes = re.findall(r'"([a-z]{2})"', re.search(r"CODES = \{([^}]*)\}", java).group(1))
try:
    notes = json.load(open(os.path.join(root, "distro/novita.json"), encoding="utf-8"))
except FileNotFoundError:
    sys.exit("Fermo: mancano le novità nelle altre lingue, in distro/novita.json.")
if notes.get("versionName") != name:
    sys.exit(f"Fermo: distro/novita.json è per la {notes.get('versionName')}, non per la {name}.")
languages = {"it": changes}
for language in codes:
    if language == "it":
        continue
    lines = notes.get("changes", {}).get(language)
    if not lines or len(lines) != len(changes):
        sys.exit(f"Fermo: in distro/novita.json la lingua {language} non ha {len(changes)} novità.")
    languages[language] = lines
apk = os.path.join(work, "salotto.apk")
data = open(apk, "rb").read()
json.dump({"versionCode": int(code), "versionName": name,
           "url": f"https://github.com/{repo}/releases/download/v{name}/salotto.apk",
           "sha256": hashlib.sha256(data).hexdigest(), "size": len(data), "changes": changes,
           "changes_by_language": languages},
          open(os.path.join(work, "update.json"), "w"), ensure_ascii=False, indent=2)
PY
openssl dgst -sha256 -sign "$KEY" -out "$WORK/update.json.sig" "$WORK/update.json"
openssl dgst -sha256 -verify <(openssl pkey -in "$KEY" -pubout 2>/dev/null) -signature "$WORK/update.json.sig" "$WORK/update.json"

# The page people reach from the QR is the one on main: it changes there when this branch arrives.
python3 "$ROOT/distro/tools/genera-errori.py" "$ROOT/ERRORI.md"
git -C "$ROOT" add ERRORI.md
git -C "$ROOT" diff --cached --quiet || git -C "$ROOT" commit -q -m "Errori per la versione $NAME"
git -C "$ROOT" push -q origin HEAD
gh release create "v$NAME" "$WORK/salotto.apk" "$WORK/update.json" "$WORK/update.json.sig" "$WORK"/installatore/* \
  --repo "$REPO" --title "Salotto $NAME" --notes "$(printf -- '- %s\n' "$@")" --latest
echo "Pubblicata la versione $NAME ($CODE)"
