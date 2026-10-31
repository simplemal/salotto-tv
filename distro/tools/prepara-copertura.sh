#!/bin/bash
# Builds the small cover app and puts it in Salotto's assets. Salotto installs it itself at the first
# update, so it needs no step in the installer. After a change raise versionCode in home/cover and
# VERSION in Cover.java, or Sticks that have it keep the old one. With FIRMA=1, signs with the final key.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT/home"
T="$PWD/.toolchain"
JAVA_HOME="$T/jdk-17.0.21+9/Contents/Home" ANDROID_HOME="$T/android-sdk" GRADLE_USER_HOME="$T/gradle-home" \
  "$T/gradle-8.9/bin/gradle" --no-daemon --console=plain ${FIRMA:+-Pfirma} :cover:clean :cover:assembleDebug :cover:lintDebug
cp "$T/cover-build/outputs/apk/debug/cover-debug.apk" app/src/main/assets/copertura.apk
ls -la app/src/main/assets/copertura.apk
