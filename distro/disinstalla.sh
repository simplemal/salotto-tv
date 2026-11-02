#!/usr/bin/env bash
# Undoes install.sh using the copy it saved. With --anteprima it only prints the commands.
#
# Usage: ./disinstalla.sh <fire-tv-ip> <backup-folder> [--anteprima]
set -uo pipefail

SALOTTO=it.simplemal.firehome
# Only on a Stick set up when it had the Home button.
OLD_FIXER=io.github.hatim.firestickfixer
PREVIEW=0
ARGS=()
for arg in "$@"; do
  if [[ "$arg" == --anteprima ]]; then PREVIEW=1; else ARGS+=("$arg"); fi
done
[[ ${#ARGS[@]} == 2 ]] || { echo "Uso: ./disinstalla.sh <ip-della-fire-tv> <cartella-backup> [--anteprima]"; exit 2; }
DEV="${ARGS[0]}:5555"
BACKUP="${ARGS[1]}"
[[ -d "$BACKUP" ]] || { echo "Cartella non trovata: $BACKUP"; exit 2; }

A() { adb -s "$DEV" "$@" < /dev/null; }
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

command -v adb > /dev/null || { echo "Manca adb: installa Android Platform Tools e riprova."; exit 1; }
adb connect "$DEV" > /dev/null < /dev/null
[[ "$(adb -s "$DEV" get-state 2>&1 < /dev/null | tail -1)" == device ]] || { echo "Dispositivo non raggiungibile."; exit 1; }

if [[ -s "$BACKUP/disabled-by-distro.txt" ]] && ask "Riattivo i pacchetti disattivati dalla distro?"; then
  while IFS= read -r package; do
    [[ -n "$package" ]] && W shell pm enable --user 0 "$package"
  done < "$BACKUP/disabled-by-distro.txt"
fi

# What Salotto may have changed from its own pages, as the copy found it.
restore_setting() {
  local value
  value="$(grep -m1 "^$2=" "$BACKUP/settings-$1.txt" | cut -d= -f2-)"
  [[ -n "$value" ]] && W shell "settings put $1 $2 '$value'"
}

if [[ -s "$BACKUP/settings-global.txt" ]] && ask "Rimetto nome della Stick, salvaschermo e spegnimento di prima?"; then
  restore_setting global device_name
  restore_setting secure bluetooth_name
  restore_setting secure screensaver_components
  restore_setting secure screensaver_enabled
  restore_setting secure sleep_timeout
  restore_setting system screen_off_timeout
fi

if [[ -s "$BACKUP/privacy-before.txt" ]] && ask "Rimetto le impostazioni di privacy di prima?"; then
  while read -r namespace key value; do
    if [[ "$value" == null || -z "$value" ]]; then
      W shell settings delete "$namespace" "$key"
    else
      W shell settings put "$namespace" "$key" "$value"
    fi
  done < "$BACKUP/privacy-before.txt"
fi

if ask "Tolgo Salotto dall'accessibilità e rimetto la Home di Amazon?"; then
  services="$(sed -n 1p "$BACKUP/accessibility-before.txt" | tr ':' '\n' | grep -v "^$OLD_FIXER/" | paste -sd: -)"
  enabled="$(sed -n 2p "$BACKUP/accessibility-before.txt")"
  if [[ "$services" == null || -z "$services" ]]; then
    W shell settings delete secure enabled_accessibility_services
  else
    W shell settings put secure enabled_accessibility_services "$services"
  fi
  W shell settings put secure accessibility_enabled "${enabled:-0}"
  W shell pm enable --user 0 com.amazon.tv.quicksettings.ui
  W shell settings delete global adb_allowed_connection_time
  [[ -z "$(A shell pm path "$OLD_FIXER" | tr -d '\r')" ]] || W uninstall "$OLD_FIXER"
  W uninstall "$SALOTTO"
  # Salotto installs it at its first update: it may not be there.
  W uninstall it.simplemal.firehome.cover || true
fi

if ask "Disinstallo anche AirPlay Server e Aurora Store?"; then
  W uninstall io.github.jqssun.airplay
  W uninstall com.aurora.store
fi

[[ $PREVIEW == 1 ]] && echo "Anteprima finita: nessuna modifica eseguita."
