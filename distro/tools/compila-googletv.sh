#!/usr/bin/env bash
# Builds Salotto for Google TV from the branch google-tv, in a copy of its own: the folder in use
# stays on its branch. The APK lands in home/.toolchain/googletv/salotto-googletv.apk, where
# pubblica-aggiornamento.sh --google-tv takes it. It is signed with the same key as the Fire TV one.
# Usage: distro/tools/compila-googletv.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOOLS="$ROOT/home/.toolchain"
COPY="$TOOLS/googletv-src"
OUT="$TOOLS/googletv"

git -C "$ROOT" fetch -q origin google-tv
# The release is made from the branch in use: Google TV must have all of it, or the two would differ.
git -C "$ROOT" merge-base --is-ancestor HEAD origin/google-tv ||
  { echo "Fermo: google-tv non contiene ancora questo commit, prima unisci $(git -C "$ROOT" branch --show-current) in google-tv."; exit 1; }

git -C "$ROOT" worktree remove --force "$COPY" 2>/dev/null || rm -rf "$COPY"
git -C "$ROOT" worktree prune
git -C "$ROOT" worktree add -q --detach "$COPY" origin/google-tv
trap 'git -C "$ROOT" worktree remove --force "$COPY" 2>/dev/null || true' EXIT
# The final key stays out of Git: firma.gradle looks for it next to the project.
[ -d "$ROOT/chiavi-aggiornamento" ] && ln -s "$ROOT/chiavi-aggiornamento" "$COPY/chiavi-aggiornamento"

(cd "$COPY/home" && JAVA_HOME="$(echo "$TOOLS"/jdk-*/Contents/Home)" ANDROID_HOME="$TOOLS/android-sdk" \
  GRADLE_USER_HOME="$TOOLS/gradle-home" "$(echo "$TOOLS"/gradle-*/bin/gradle)" --no-daemon --console=plain -q \
  --init-script "$ROOT/distro/tools/non-debuggable.init.gradle" :app:assembleDebug)

mkdir -p "$OUT"
cp "$COPY/home/.toolchain/app-build/outputs/apk/debug/app-debug.apk" "$OUT/salotto-googletv.apk"
echo "Pronto: $OUT/salotto-googletv.apk ($(git -C "$COPY" log --oneline -1))"
