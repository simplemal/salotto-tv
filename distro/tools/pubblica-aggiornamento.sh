#!/usr/bin/env bash
# Publishes the built Salotto as a release of this repository: APK, signed update.json, error page,
# and next to them the latest installer, which is what people download.
# Usage: distro/tools/pubblica-aggiornamento.sh [--beta] [--google-tv] "first change" "second change" ...
# With --google-tv the same release carries Salotto for Google TV too, built by compila-googletv.sh:
# salotto-googletv.apk and its own signed update-googletv.json, which only Google TV reads.
# With --beta the version, named like 1.0.20-beta.1, becomes a pre-release, which GitHub's "latest"
# never points to: only the TVs with the betas on find it, through the fixed pre-release "beta". The
# installer, README and error page stay on the stable version. Betas and stable versions share one row
# of version codes: the next stable one only needs a higher code to reach the TVs on the beta too.
# The changes are in Italian; distro/novita.json holds them in every other language of the app, for
# this same version, and each TV shows its own. Older versions read only the Italian list.
set -euo pipefail

BETA=false
GOOGLE_TV=false
while [ $# -gt 0 ]; do
  case "$1" in
    --beta) BETA=true; shift ;;
    --google-tv) GOOGLE_TV=true; shift ;;
    *) break ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APK="$ROOT/home/.toolchain/app-build/outputs/apk/debug/app-debug.apk"
GOOGLE_APK="$ROOT/home/.toolchain/googletv/salotto-googletv.apk"
KEY="$ROOT/chiavi-aggiornamento/privata.pem"
# The certificate of firma-salotto.jks, which is public: every Stick has it. A build with any other
# key cannot update them.
CERTIFICATE="864ae0a359c87748a250d8a9185072854c2d6fb94367bcf517abdf0887a2feb2"
CODE="$(sed -n "s/.*versionCode \([0-9]*\).*/\1/p" "$ROOT/home/app/build.gradle")"
NAME="$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" "$ROOT/home/app/build.gradle")"
REPO="$(sed -n 's/.*UPDATES_REPO = "\(.*\)".*/\1/p' "$ROOT/home/app/src/main/java/it/simplemal/firehome/Fire.java")"
[ -n "$REPO" ] || { echo "Fermo: repository non trovato in Fire.java."; exit 1; }
[ $# -gt 0 ] || { echo "Elenca le novità tra virgolette."; exit 2; }
case "$NAME" in
  *-beta*) $BETA || { echo "Fermo: la $NAME è una beta, si pubblica con --beta."; exit 1; } ;;
  *) ! $BETA || { echo "Fermo: per una beta il nome della versione finisce con -beta.N, ora è $NAME."; exit 1; } ;;
esac
if grep -rnE -f "$ROOT/distro/dati-personali.local" "$ROOT/home/app/src" > /dev/null; then
  echo "Fermo: dati personali nei sorgenti."; exit 1
fi

# Salotto no longer carries the small copies of the backdrops: each TV downloads them from the
# release "sfondi", so they must be there before a version without them reaches anyone.
if ! curl -fsSL "https://github.com/$REPO/releases/download/sfondi/sfondi.json" |
    python3 -c 'import json, sys; sys.exit(0 if all("thumbs" in p for p in json.load(sys.stdin)["packs"]) else 1)'; then
  echo "Fermo: il rilascio sfondi non ha ancora le miniature, prima distro/tools/pubblica-sfondi.sh."; exit 1
fi

TOOLS="$ROOT/home/.toolchain"
check_apk() {
  local badging signer
  badging="$("$TOOLS/android-sdk/build-tools/34.0.0/aapt2" dump badging "$1" | sed -n 1p)"
  case "$badging" in
    *"versionCode='$CODE' versionName='$NAME'"*) ;;
    *) echo "Fermo: $2 non è la versione $NAME ($CODE), va ricompilato."; exit 1 ;;
  esac
  signer="$(JAVA_HOME="$(echo "$TOOLS"/jdk-*/Contents/Home)" "$TOOLS/android-sdk/build-tools/34.0.0/apksigner" verify --print-certs "$1" 2>/dev/null | sed -n 's/.*certificate SHA-256 digest: //p' | sed -n 1p)"
  [ "$signer" = "$CERTIFICATE" ] || { echo "Fermo: $2 non ha la firma definitiva."; exit 1; }
}
check_apk "$APK" "l'APK"
if $GOOGLE_TV; then
  [ -f "$GOOGLE_APK" ] || { echo "Fermo: manca Salotto per Google TV, prima distro/tools/compila-googletv.sh."; exit 1; }
  check_apk "$GOOGLE_APK" "l'APK per Google TV"
fi

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK:?}"' EXIT
cp "$APK" "$WORK/salotto.apk"
FILES=("$WORK/salotto.apk" "$WORK/update.json" "$WORK/update.json.sig")
if $GOOGLE_TV; then
  cp "$GOOGLE_APK" "$WORK/salotto-googletv.apk"
  FILES+=("$WORK/salotto-googletv.apk" "$WORK/update-googletv.json" "$WORK/update-googletv.json.sig")
fi
$BETA || "$ROOT/distro/tools/prepara-installatore.sh" "$WORK/installatore"
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
for apk_name, manifest in (("salotto.apk", "update.json"), ("salotto-googletv.apk", "update-googletv.json")):
    apk = os.path.join(work, apk_name)
    if not os.path.exists(apk):
        continue
    data = open(apk, "rb").read()
    json.dump({"versionCode": int(code), "versionName": name,
               "url": f"https://github.com/{repo}/releases/download/v{name}/{apk_name}",
               "sha256": hashlib.sha256(data).hexdigest(), "size": len(data), "changes": changes,
               "changes_by_language": languages},
              open(os.path.join(work, manifest), "w"), ensure_ascii=False, indent=2)
PY
MANIFESTS=(update.json)
$GOOGLE_TV && MANIFESTS+=(update-googletv.json)
for manifest in "${MANIFESTS[@]}"; do
  openssl dgst -sha256 -sign "$KEY" -out "$WORK/$manifest.sig" "$WORK/$manifest"
  openssl dgst -sha256 -verify <(openssl pkey -in "$KEY" -pubout 2>/dev/null) -signature "$WORK/$manifest.sig" "$WORK/$manifest"
done

NOTES="$(printf -- '- %s\n' "$@")"
if $BETA; then
  gh release create "v$NAME" "${FILES[@]}" \
    --repo "$REPO" --title "Salotto $NAME" --notes "$NOTES" --prerelease --latest=false
  gh release view beta --repo "$REPO" > /dev/null 2>&1 ||
    gh release create beta --repo "$REPO" --title "Salotto beta" --prerelease --latest=false \
      --notes "Il canale beta: Salotto lo legge solo con Impostazioni → Aggiornamento → Versioni beta. Le versioni sono nelle pre-release."
  for manifest in "${MANIFESTS[@]}"; do
    gh release upload beta "$WORK/$manifest" "$WORK/$manifest.sig" --repo "$REPO" --clobber
  done
  echo "Pubblicata la beta $NAME ($CODE)"
  exit 0
fi

# The page people reach from the QR is the one on main: it changes there when this branch arrives.
python3 "$ROOT/distro/tools/genera-errori.py" "$ROOT/ERRORI.md"
git -C "$ROOT" add ERRORI.md
git -C "$ROOT" diff --cached --quiet || git -C "$ROOT" commit -q -m "Errori per la versione $NAME"
git -C "$ROOT" push -q origin HEAD
gh release create "v$NAME" "${FILES[@]}" "$WORK"/installatore/* \
  --repo "$REPO" --title "Salotto $NAME" --notes "$NOTES" --latest
echo "Pubblicata la versione $NAME ($CODE)"
