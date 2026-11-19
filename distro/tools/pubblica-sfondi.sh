#!/usr/bin/env bash
# Publishes the collections of backdrops on the fixed release "sfondi", apart from the versions of
# Salotto: one archive for each, one more with its small copies, a strip of small pictures to show
# what it holds, ten larger ones for the installer's gallery, sfondi.json, and sfondi-indice.json with the place of every file in its archive
# signed with the key of the updates. Salotto downloads from there only the collections chosen,
# and checks each archive against the signed list before opening it.
# Usage: distro/tools/pubblica-sfondi.sh [--prova]   (with --prova it builds everything and uploads nothing)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# The collections themselves are kept out of the app, here, and their small copies beside them,
# made by prepara-miniature.py.
PACKS_DIR="$ROOT/home/sfondi"
THUMBS_DIR="$ROOT/home/miniature"
KEY="$ROOT/chiavi-aggiornamento/privata.pem"
REPO="$(sed -n 's/.*UPDATES_REPO = "\(.*\)".*/\1/p' "$ROOT/home/app/src/main/java/it/simplemal/firehome/Fire.java")"
[ -n "$REPO" ] || { echo "Fermo: repository non trovato in Fire.java."; exit 1; }
TRY=false
if [ "${1:-}" = "--prova" ]; then TRY=true; fi
# Every collection, with its kind and name: the first ones are Packs.ALL in Salotto, the others
# reach every Salotto through the signed list, with no update of the app.
LIST="$ROOT/distro/config/sfondi.txt"
PACKS="$(grep -v '^#' "$LIST" | cut -f1 | xargs)"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK:?}"' EXIT
for pack in $PACKS; do
  [ -d "$PACKS_DIR/$pack" ] || { echo "Fermo: manca la cartella $pack."; exit 1; }
  # Plain tar, without the Mac's own extra files: the pictures are compressed already.
  (cd "$PACKS_DIR" && COPYFILE_DISABLE=1 tar --format ustar -cf "$WORK/$pack.tar" "$pack")
  [ -d "$THUMBS_DIR/miniature-$pack" ] || { echo "Fermo: mancano le miniature di $pack, lancia prepara-miniature.py."; exit 1; }
  (cd "$THUMBS_DIR" && COPYFILE_DISABLE=1 tar --format ustar -cf "$WORK/miniature-$pack.tar" "miniature-$pack")
done

python3 - "$WORK" "$THUMBS_DIR" "$PACKS_DIR" "$REPO" "$LIST" $PACKS <<'PY'
import hashlib, json, os, re, sys, tarfile
from PIL import Image
work, thumbs_dir, packs_dir, repo, list_file, *packs = sys.argv[1:]
kinds, names = {}, {}
for line in open(list_file).read().splitlines():
    if not line.strip() or line.startswith("#"):
        continue
    pack, kind, name = line.split("\t")
    # What Salotto accepts for a collection added later: a plain folder name, a short name.
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,39}", pack) or kind not in ("weather", "timed") or not 0 < len(name.strip()) <= 40:
        raise SystemExit(f"Fermo: riga non valida in sfondi.txt: {line}")
    kinds[pack], names[pack] = kind, name.strip()
base = f"https://github.com/{repo}/releases/download/sfondi/"
SAMPLES, WIDTH, HEIGHT, GAP = 6, 216, 122, 6
# Seen large in the installer: 10 pictures of each collection, 1280 x 720, asked for only when opened.
VIEWS, VIEW_SIZE = 10, (1280, 720)
listed = []
indexes = {}
for pack in packs:
    # The weather's illustrations sit in a folder for each moment of the day.
    small_dir = os.path.join(thumbs_dir, f"miniature-{pack}", "grandi")
    small = sorted(os.path.relpath(os.path.join(where, name), small_dir)
                   for where, _, names in os.walk(small_dir)
                   for name in names if not name.startswith("."))
    picked = [small[round(index * (len(small) - 1) / (SAMPLES - 1))] for index in range(SAMPLES)]
    strip = Image.new("RGB", (SAMPLES * WIDTH + (SAMPLES - 1) * GAP, HEIGHT), (0, 0, 0))
    for index, name in enumerate(picked):
        with Image.open(os.path.join(small_dir, name)) as picture:
            strip.paste(picture.convert("RGB").resize((WIDTH, HEIGHT)), (index * (WIDTH + GAP), 0))
    sample = os.path.join(work, f"campione-{pack}.webp")
    strip.save(sample, quality=80)
    full = sorted(os.path.relpath(os.path.join(where, name), os.path.join(packs_dir, pack))
                  for where, _, names in os.walk(os.path.join(packs_dir, pack))
                  for name in names if not name.startswith(".")
                  and name.lower().endswith((".webp", ".jpg", ".jpeg", ".png")))
    gallery = []
    for index in range(VIEWS):
        name = full[round(index * (len(full) - 1) / (VIEWS - 1))]
        view = os.path.join(work, f"vista-{pack}-{index + 1:02d}.webp")
        with Image.open(os.path.join(packs_dir, pack, name)) as picture:
            picture = picture.convert("RGB")
            # Filled and cut to 16:9, like the TV shows it.
            scale = max(VIEW_SIZE[0] / picture.width, VIEW_SIZE[1] / picture.height)
            picture = picture.resize((round(picture.width * scale), round(picture.height * scale)), Image.LANCZOS)
            left, top = (picture.width - VIEW_SIZE[0]) // 2, (picture.height - VIEW_SIZE[1]) // 2
            picture.crop((left, top, left + VIEW_SIZE[0], top + VIEW_SIZE[1])).save(view, quality=78)
        gallery.append({"url": base + os.path.basename(view), "sha256": hashlib.sha256(open(view, "rb").read()).hexdigest()})
    archive = os.path.join(work, f"{pack}.tar")
    data = open(archive, "rb").read()
    # Where each file sits in the archive: Salotto cuts out only the picture the Home opens on.
    index = {}
    with tarfile.open(archive) as opened:
        for member in opened.getmembers():
            if member.isfile():
                body = data[member.offset_data:member.offset_data + member.size]
                index[member.name] = [member.offset_data, member.size, hashlib.sha256(body).hexdigest()]
    thumbs = open(os.path.join(work, f"miniature-{pack}.tar"), "rb").read()
    files = sum(len([f for f in fs if not f.startswith(".")]) for _, _, fs in os.walk(os.path.join(packs_dir, pack)))
    listed.append({
        "id": pack, "name": names[pack], "kind": kinds[pack], "bytes": len(data), "files": files,
        "sha256": hashlib.sha256(data).hexdigest(), "url": base + f"{pack}.tar",
        "sample": base + f"campione-{pack}.webp",
        "sample_sha256": hashlib.sha256(open(sample, "rb").read()).hexdigest(),
        "gallery": gallery,
        # Read by Salotto from version 1.1.6: before, the small copies came inside the app.
        "thumbs": {"url": base + f"miniature-{pack}.tar", "bytes": len(thumbs), "sha256": hashlib.sha256(thumbs).hexdigest()},
    })
    indexes[pack] = index
json.dump({"version": 1, "packs": listed}, open(os.path.join(work, "sfondi.json"), "w"), indent=2)
# Apart, and signed apart: the installer reads sfondi.json only up to 64 KB, and this is larger.
json.dump({"version": 1, "index": indexes}, open(os.path.join(work, "sfondi-indice.json"), "w"), separators=(",", ":"))
for item in listed:
    print(f"{item['id']}: {item['files']} file, {item['bytes'] / 1048576:.1f} MB")
PY

openssl dgst -sha256 -sign "$KEY" -out "$WORK/sfondi.json.sig" "$WORK/sfondi.json"
openssl dgst -sha256 -sign "$KEY" -out "$WORK/sfondi-indice.json.sig" "$WORK/sfondi-indice.json"
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
gh release upload sfondi "$WORK"/*.tar "$WORK"/campione-*.webp "$WORK"/vista-*.webp "$WORK/sfondi.json" "$WORK/sfondi.json.sig" "$WORK/sfondi-indice.json" "$WORK/sfondi-indice.json.sig" --repo "$REPO" --clobber
echo "Pubblicate le collezioni di sfondi."
