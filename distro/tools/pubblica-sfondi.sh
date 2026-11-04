#!/usr/bin/env bash
# Publishes the collections of backdrops on the fixed release "sfondi", apart from the versions of
# Salotto: one archive for each, a strip of small pictures to show what it holds, and sfondi.json
# signed with the key of the updates. Salotto downloads from there only the collections chosen,
# and checks each archive against the signed list before opening it.
# Usage: distro/tools/pubblica-sfondi.sh [--prova]   (with --prova it builds everything and uploads nothing)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ASSETS="$ROOT/home/app/src/main/assets"
# The collections themselves are kept out of the app, here.
PACKS_DIR="$ROOT/home/sfondi"
KEY="$ROOT/chiavi-aggiornamento/privata.pem"
REPO="$(sed -n 's/.*UPDATES_REPO = "\(.*\)".*/\1/p' "$ROOT/home/app/src/main/java/it/simplemal/firehome/Fire.java")"
[ -n "$REPO" ] || { echo "Fermo: repository non trovato in Fire.java."; exit 1; }
TRY=false
if [ "${1:-}" = "--prova" ]; then TRY=true; fi
# The same order and names as Packs.ALL in Salotto.
PACKS="meteo-illustrato meteo-foto meteo-aereo astratti astratti-3d nox-city natura-aerea"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK:?}"' EXIT
for pack in $PACKS; do
  [ -d "$PACKS_DIR/$pack" ] || { echo "Fermo: manca la cartella $pack."; exit 1; }
  # Plain tar, without the Mac's own extra files: the pictures are compressed already.
  (cd "$PACKS_DIR" && COPYFILE_DISABLE=1 tar --format ustar -cf "$WORK/$pack.tar" "$pack")
done

python3 - "$WORK" "$ASSETS" "$PACKS_DIR" "$REPO" $PACKS <<'PY'
import hashlib, json, os, sys
from PIL import Image
work, assets, packs_dir, repo, *packs = sys.argv[1:]
base = f"https://github.com/{repo}/releases/download/sfondi/"
SAMPLES, WIDTH, HEIGHT, GAP = 6, 216, 122, 6
listed = []
for pack in packs:
    # The weather's illustrations sit in a folder for each moment of the day.
    small = sorted(os.path.relpath(os.path.join(where, name), os.path.join(assets, "miniature", pack))
                   for where, _, names in os.walk(os.path.join(assets, "miniature", pack))
                   for name in names if not name.startswith("."))
    picked = [small[round(index * (len(small) - 1) / (SAMPLES - 1))] for index in range(SAMPLES)]
    strip = Image.new("RGB", (SAMPLES * WIDTH + (SAMPLES - 1) * GAP, HEIGHT), (0, 0, 0))
    for index, name in enumerate(picked):
        with Image.open(os.path.join(assets, "miniature", pack, name)) as picture:
            strip.paste(picture.convert("RGB").resize((WIDTH, HEIGHT)), (index * (WIDTH + GAP), 0))
    sample = os.path.join(work, f"campione-{pack}.webp")
    strip.save(sample, quality=80)
    archive = os.path.join(work, f"{pack}.tar")
    data = open(archive, "rb").read()
    files = sum(len([f for f in fs if not f.startswith(".")]) for _, _, fs in os.walk(os.path.join(packs_dir, pack)))
    listed.append({
        "id": pack, "bytes": len(data), "files": files,
        "sha256": hashlib.sha256(data).hexdigest(), "url": base + f"{pack}.tar",
        "sample": base + f"campione-{pack}.webp",
        "sample_sha256": hashlib.sha256(open(sample, "rb").read()).hexdigest(),
    })
json.dump({"version": 1, "packs": listed}, open(os.path.join(work, "sfondi.json"), "w"), indent=2)
for item in listed:
    print(f"{item['id']}: {item['files']} file, {item['bytes'] / 1048576:.1f} MB")
PY

openssl dgst -sha256 -sign "$KEY" -out "$WORK/sfondi.json.sig" "$WORK/sfondi.json"
openssl dgst -sha256 -verify <(openssl pkey -in "$KEY" -pubout 2>/dev/null) -signature "$WORK/sfondi.json.sig" "$WORK/sfondi.json"

if $TRY; then
  mkdir -p "$ROOT/home/.toolchain/sfondi-prova"
  cp "$WORK"/* "$ROOT/home/.toolchain/sfondi-prova/"
  echo "Prova: tutto in home/.toolchain/sfondi-prova, niente caricato."
  exit 0
fi
# A pre-release, which GitHub's "latest" never points to: the versions of Salotto stay where they are.
gh release view sfondi --repo "$REPO" > /dev/null 2>&1 ||
  gh release create sfondi --repo "$REPO" --title "Salotto: collezioni di sfondi" --prerelease --latest=false \
    --notes "Le collezioni di sfondi che Salotto scarica quando si scelgono. Non si installano a mano."
gh release upload sfondi "$WORK"/*.tar "$WORK"/campione-*.webp "$WORK/sfondi.json" "$WORK/sfondi.json.sig" --repo "$REPO" --clobber
echo "Pubblicate le collezioni di sfondi."
