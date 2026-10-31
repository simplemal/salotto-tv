#!/usr/bin/env python3
"""Lets Salotto's adb key be approved on the TV, once.

adbd shows its dialog only to connections coming from another machine, so for this one time
Salotto's connection travels TV -> this computer -> TV:5555. Afterwards the key is known and
Salotto reaches adbd directly. Nothing stays behind: the relay and the reverse port are removed.

Usage: autorizza-adb.py <fire-tv-ip>
"""
import shutil
import socket
import subprocess
import sys
import threading

PACKAGE = "it.simplemal.firehome"
DEVICE_PORT = 5560
LOCAL_PORT = 5561
ADB_PORT = 5555
WAIT_SECONDS = 240
ADB = shutil.which("adb") or "/opt/homebrew/bin/adb"


def adb(target, *args):
    return subprocess.run([ADB, "-s", target, *args], capture_output=True, text=True, timeout=30)


def pump(source, sink):
    try:
        while True:
            data = source.recv(65536)
            if not data:
                break
            sink.sendall(data)
    except OSError:
        pass
    for side in (source, sink):
        try:
            side.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass


def relay(listener, address):
    """Carries Salotto's connection until it ends, which it does right after the approval."""
    incoming, _ = listener.accept()
    outgoing = socket.create_connection((address, ADB_PORT), timeout=10)
    outgoing.settimeout(None)
    incoming.settimeout(None)
    back = threading.Thread(target=pump, args=(outgoing, incoming), daemon=True)
    back.start()
    pump(incoming, outgoing)
    back.join(5)


def main():
    if len(sys.argv) != 2:
        sys.exit("Uso: autorizza-adb.py <ip-della-fire-tv>")
    address = sys.argv[1]
    target = f"{address}:{ADB_PORT}"
    subprocess.run([ADB, "connect", target], capture_output=True, text=True, timeout=15)
    if adb(target, "get-state").stdout.strip() != "device":
        sys.exit("Fire TV non raggiungibile. Controlla IP e Debug ADB.")
    if not adb(target, "shell", "pm", "path", PACKAGE).stdout.strip():
        sys.exit("Salotto non è installato.")

    listener = socket.create_server(("127.0.0.1", LOCAL_PORT))
    listener.settimeout(WAIT_SECONDS)
    adb(target, "reverse", f"tcp:{DEVICE_PORT}", f"tcp:{LOCAL_PORT}")
    try:
        # Opening the task manager is what makes Salotto try again.
        adb(target, "shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
        adb(target, "shell", "am", "start", "-n", f"{PACKAGE}/.TasksActivity", "--ez", "connect", "true")
        print(f"Guarda la TV: scegli Consenti sempre, poi OK. Hai {WAIT_SECONDS // 60} minuti.")
        relay(listener, address)
        print("Fatto. Puoi chiudere questa finestra.")
    except TimeoutError:
        sys.exit("Salotto non si è collegato. Controlla che Apri tenendo premuto Menu sia su Sì.")
    finally:
        listener.close()
        adb(target, "reverse", "--remove", f"tcp:{DEVICE_PORT}")


if __name__ == "__main__":
    main()
