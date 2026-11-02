#!/usr/bin/env bash
# Installs the Salotto distro on a Fire TV Stick over adb, one confirmed step at a time.
# With --anteprima it only reads the device and prints the commands it would run.
#
# Usage: ./install.sh <fire-tv-ip> [--anteprima]
#        ./install.sh --seriale <usb-serial> [--anteprima]
# Needs: adb, curl, shasum.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SALOTTO=it.simplemal.firehome
# Only to take it off a Stick set up when it had the Home button.
OLD_FIXER=io.github.hatim.firestickfixer
TESTED_MODEL=AFTKM
TESTED_FIREOS="8.1.6.6 8.1.8.5"

PREVIEW=0
TARGET=""
USB=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --anteprima) PREVIEW=1 ;;
    --seriale) USB=1; shift; TARGET="${1:-}" ;;
    *) TARGET="$1" ;;
  esac
  shift
done
[[ -n "$TARGET" ]] || { echo "Uso: ./install.sh <ip-della-fire-tv> [--anteprima]"; exit 2; }
if [[ $USB == 1 ]]; then DEV="$TARGET"; else DEV="$TARGET:5555"; fi

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
stop() { printf '\n\033[1mFermo: %s\033[0m\n' "$*"; exit 1; }

# Every adb call reads /dev/null, so it cannot swallow the answers typed at the prompts.
A() { adb -s "$DEV" "$@" < /dev/null; }

# A change to the device: printed in preview, run otherwise.
W() {
  if [[ $PREVIEW == 1 ]]; then echo "  adb $*"; return 0; fi
  A "$@"
}

ask() {
  if [[ $PREVIEW == 1 ]]; then echo "[domanda] $1"; return 0; fi
  local answer
  read -r -p "$1 [s/N] " answer < /dev/tty
  [[ "$answer" == [sS]* ]]
}

prop() { A shell getprop "$1" | tr -d '\r'; }
version_of() { A shell dumpsys package "$1" | tr -d '\r' | grep -m1 'versionName=' | sed 's/.*versionName=//'; }
salotto_bound() { [[ "$(A shell dumpsys accessibility | sed -n '/Bound services/,/Enabled services/p')" == *Salotto* ]]; }

check_tools() {
  local tool
  for tool in adb curl shasum; do
    command -v "$tool" > /dev/null || stop "manca $tool. Per adb sul Mac: brew install android-platform-tools"
  done
}

connect() {
  say "1. Collegamento"
  [[ $USB == 1 ]] || adb connect "$DEV" > /dev/null < /dev/null
  local state
  state="$(adb -s "$DEV" get-state 2>&1 < /dev/null | tr '\n' ' ')"
  case "$state" in
    device*) echo "Collegato a $DEV." ;;
    *unauthorized*) stop "conferma il dialogo sulla TV, oppure spegni e riaccendi Debug ADB in Opzioni sviluppatore, poi rilancia." ;;
    *) stop "dispositivo non raggiungibile ($state). Controlla IP e Debug ADB." ;;
  esac
}

check_device() {
  say "2. Controllo del dispositivo"
  local model fireos abi
  model="$(prop ro.product.model)"
  fireos="$(prop ro.build.version.name)"
  abi="$(prop ro.product.cpu.abi)"
  echo "Modello: $model"
  echo "Sistema: $fireos"
  echo "ABI: $abi"
  if [[ "$model" != "$TESTED_MODEL" ]]; then
    echo "ATTENZIONE: provata solo su $TESTED_MODEL. I pacchetti Amazon possono essere diversi."
    ask "Continuare comunque su $model?" || exit 0
  fi
  local tested=0 version
  for version in $TESTED_FIREOS; do [[ "$fireos" == *"$version"* ]] && tested=1; done
  [[ $tested == 1 ]] || echo "ATTENZIONE: versione di Fire OS mai provata. Alcuni passaggi possono comportarsi diversamente."
  [[ "$abi" == arm* ]] || stop "ABI $abi non previsto."
}

backup() {
  say "3. Copia dello stato attuale"
  local serial
  serial="$(prop ro.serialno)"
  BACKUP="$HOME/Salotto-backup/$serial-$(date +%Y%m%d-%H%M%S)"
  if [[ $PREVIEW == 1 ]]; then echo "Salverebbe impostazioni e pacchetti in $BACKUP"; return; fi
  mkdir -p "$BACKUP" || stop "non riesco a creare $BACKUP"
  local namespace
  for namespace in global secure system; do
    A shell settings list "$namespace" | tr -d '\r' > "$BACKUP/settings-$namespace.txt"
  done
  A shell pm list packages | tr -d '\r' > "$BACKUP/packages.txt"
  A shell pm list packages -d | tr -d '\r' > "$BACKUP/disabled-before.txt"
  A shell settings get secure enabled_accessibility_services | tr -d '\r' > "$BACKUP/accessibility-before.txt"
  A shell settings get secure accessibility_enabled | tr -d '\r' >> "$BACKUP/accessibility-before.txt"
  : > "$BACKUP/disabled-by-distro.txt"
  echo "Salvato in $BACKUP. Serve a disinstalla.sh: non cancellarlo."
}

# download <url> <sha256>: prints the checked local path.
download() {
  local file="$HERE/scaricati/$2.apk"
  if [[ ! -f "$file" ]]; then
    mkdir -p "$HERE/scaricati"
    curl -fsSL -m 300 -o "$file.tmp" "$1" || { rm -f "$file.tmp"; return 1; }
    mv "$file.tmp" "$file"
  fi
  [[ "$(shasum -a 256 "$file" | cut -d' ' -f1)" == "$2" ]] || { rm -f "$file"; return 1; }
  echo "$file"
}

install_components() {
  say "4. Installazione delle app"
  local name version package url sum file current
  while IFS='|' read -r name version package url sum; do
    [[ -z "$name" || "$name" == \#* ]] && continue
    current="$(version_of "$package")"
    if [[ "$current" == "$version" ]]; then echo "$name $version: già presente."; continue; fi
    echo "$name $version (ora: ${current:-assente})"
    if [[ $PREVIEW == 1 ]]; then echo "  scarica $url e controlla SHA-256"; W install -r "$name.apk"; continue; fi
    file="$(download "$url" "$sum")" || stop "download o controllo SHA-256 falliti per $name."
    A install -r "$file" || stop "installazione fallita per $name."
  done < "$HERE/config/componenti.txt"

  local apk="$HERE/salotto.apk"
  [[ -f "$apk" ]] || apk="$HERE/../home/.toolchain/app-build/outputs/apk/debug/app-debug.apk"
  [[ -f "$apk" ]] || stop "manca salotto.apk accanto a questo script."
  W install -r "$apk" || stop "installazione di Salotto fallita."
  W shell pm grant "$SALOTTO" android.permission.WRITE_SECURE_SETTINGS
  W shell pm grant "$SALOTTO" android.permission.READ_LOGS
  W shell pm grant "$SALOTTO" android.permission.ACCESS_FINE_LOCATION
  W shell appops set "$SALOTTO" GET_USAGE_STATS allow
  W shell appops set "$SALOTTO" REQUEST_INSTALL_PACKAGES allow
  W shell cmd notification allow_listener "$SALOTTO/$SALOTTO.NotificationCounter" || true
}

configure_home() {
  say "5. Salotto come Home"
  ask "Attivo il servizio di Salotto, che porta su Salotto il tasto Home e l'accensione?" || return
  W shell pm disable-user --user 0 com.amazon.tv.quicksettings.ui
  local service="$SALOTTO/$SALOTTO.TaskService" current others
  current="$(A shell settings get secure enabled_accessibility_services | tr -d '\r')"
  others="$(tr ':' '\n' <<< "$current" | grep -v -e '^$' -e '^null$' -e "^$service\$" | paste -sd: -)"
  W shell settings put secure enabled_accessibility_services "${others:+$others:}$service"
  W shell settings put secure accessibility_enabled 1
  if [[ $PREVIEW == 0 ]]; then
    local tries
    for tries in $(seq 1 20); do salotto_bound && break; sleep 0.5; done
    salotto_bound || stop "il servizio di Salotto non risulta collegato. Riavvia la Stick e rilancia."
    echo "Servizio di Salotto collegato."
  fi
  remove_fixer "$others"
}

# Last: a Stick where Salotto's service does not start keeps the Home button it has.
remove_fixer() {
  local kept
  kept="$(tr ':' '\n' <<< "$1" | grep -v "^$OLD_FIXER/" | paste -sd: -)"
  [[ "$kept" == "$1" ]] || W shell settings put secure enabled_accessibility_services "${kept:+$kept:}$SALOTTO/$SALOTTO.TaskService"
  [[ -z "$(A shell pm path "$OLD_FIXER" | tr -d '\r')" ]] || W uninstall "$OLD_FIXER"
}

disable_list() {
  local package
  while IFS= read -r package; do
    [[ -z "$package" ]] && continue
    if [[ -z "$(A shell pm path "$package" | tr -d '\r')" ]]; then echo "$package: assente, salto."; continue; fi
    if [[ $PREVIEW == 1 ]]; then echo "  adb shell pm disable-user --user 0 $package"; continue; fi
    if A shell pm disable-user --user 0 "$package" | grep -q 'disabled-user'; then
      echo "$package" >> "$BACKUP/disabled-by-distro.txt"
      echo "$package: disattivato."
    else
      echo "$package: rifiutato da Fire OS."
    fi
  done < "$1"
}

disable_packages() {
  say "6. Riduzione di pubblicità, telemetria e Alexa"
  if ask "Disattivo i pacchetti di pubblicità e telemetria?"; then disable_list "$HERE/config/disattiva-pubblicita.txt"; fi
  echo "I pacchetti Alexa tolgono la voce: telecomando vocale e comandi Alexa non funzioneranno più."
  if ask "Disattivo anche Alexa?"; then disable_list "$HERE/config/disattiva-alexa.txt"; fi
}

apply_privacy() {
  say "7. Impostazioni di privacy"
  ask "Limito tracciamento pubblicitario e invio dei dati di utilizzo?" || return
  local namespace key value before
  while read -r namespace key value; do
    [[ -z "$namespace" || "$namespace" == \#* ]] && continue
    if [[ $PREVIEW == 0 ]]; then
      before="$(A shell settings get "$namespace" "$key" | tr -d '\r')"
      echo "$namespace $key $before" >> "$BACKUP/privacy-before.txt"
    fi
    W shell settings put "$namespace" "$key" "$value"
  done < "$HERE/config/privacy.txt"
}

finish() {
  say "8. Avvio e controllo"
  # Not KEYCODE_HOME: pressed right after an install it counts as a double Home and stays on Amazon's.
  W shell am start -n io.github.jqssun.airplay/.MainActivity || true
  [[ $PREVIEW == 1 ]] || sleep 3
  W shell am start -n "$SALOTTO/.MainActivity"
  if [[ $PREVIEW == 1 ]]; then echo; echo "Anteprima finita: nessuna modifica eseguita."; return; fi
  sleep 3
  echo "Salotto: $(version_of "$SALOTTO")"
  if salotto_bound; then
    echo "Servizio di Salotto collegato."
  else
    echo "ATTENZIONE: il servizio di Salotto non risulta collegato. Riavvia la Stick e controlla il tasto Home."
  fi
  say "Finito. Restano i passaggi a mano della guida: docs/GUIDA-SECONDA-STICK.md"
}

check_tools
connect
check_device
backup
install_components
configure_home
disable_packages
apply_privacy
finish
