#!/usr/bin/env python3
"""Builds the public ERRORI.md from UpdateError.java: one list, so the page cannot fall behind.

Each error in English, then in Italian in italics. The Italian comes from UpdateError.java, the
English titles from the app's own English strings, and the English causes and fixes from ENGLISH
below: a new error without them stops the page from being built.

Usage: genera-errori.py <output-file>
"""
import pathlib
import re
import sys

APP = pathlib.Path(__file__).resolve().parents[2] / "home/app/src/main"
JAVA = APP / "java/it/simplemal/firehome"
SOURCE = JAVA / "UpdateError.java"
STRINGS_EN = APP / "res/values-en/strings_app.xml"
REPO = re.search(r'UPDATES_REPO = "([^"]+)"', (JAVA / "Fire.java").read_text(encoding="utf-8")).group(1)
GROUPS = (("F", "Before anything changes", "Salotto has not been changed.",
           "Prima di modificare qualcosa", "Salotto non è stata modificata."),
          ("C", "During the installation", "Salotto stayed on, or went back to, the previous version.",
           "Durante l'installazione", "Salotto è rimasta o è tornata alla versione precedente."),
          ("R", "While going back to the previous version", "Follow the instructions of each entry.",
           "Durante il ritorno alla versione precedente", "Segui le istruzioni di ogni voce."))
ISSUES = f"https://github.com/{REPO}/issues/new"
# The README's table with the Mac and Windows downloads, in each language.
INSTALLER = f"https://github.com/{REPO}#installation"
INSTALLER_IT = f"https://github.com/{REPO}/blob/main/README.it.md#installazione"
# Cause and fix in English, next to the Italian in UpdateError.java.
ENGLISH = {
    "F101": ("The Stick is not connected to the Internet.", "Check the Wi-Fi in Settings, then try again."),
    "F102": ("GitHub did not answer.", "It is usually temporary: try again in a few minutes."),
    "F103": ("The information about the new version is incomplete or unreadable.", "Try again later. If it persists, report it."),
    "F104": ("The downloaded information is not signed with Salotto's key.", "Do not install anything and report the problem."),
    "F105": ("The version offered is not newer than the one installed.", "Nothing to do."),
    "F106": ("There is not enough space on the Stick to download and install the new version.",
             "Salotto has not been touched. Free up space by removing apps you do not use, then try again, "
             "or update from a computer with Installa Salotto."),
    "F107": ("The file was not downloaded completely.", "Check the connection and try again."),
    "F108": ("The fingerprint of the downloaded file does not match the expected one.",
             "Try again: the file is downloaded again. If it persists, report it."),
    "F109": ("The downloaded file is not a valid Android app.", "Try again. If it persists, report it."),
    "F110": ("The downloaded file is not Salotto.", "Do not install anything and report the problem."),
    "F111": ("The file is not signed like the installed version.", "Do not install anything and report the problem."),
    "F112": ("The file does not contain the announced version.", "Try again later. If it persists, report it."),
    "F113": ("This version needs a newer system.", "Update Fire OS from the Fire TV Settings, then try again."),
    "F114": ("Salotto cannot save the file on the Stick.", "Restart the Stick and try again."),
    "F115": ("The service that installs updates is not connected.",
             "Wait a minute and try again. If it persists, check Settings → Apps → Held-down keys."),
    "F116": ("Salotto could not save your choices.", "Restart the Stick and try again."),
    "F117": ("Something is playing on the Stick.", "Stop the playback and try again."),
    "C201": ("The installer did not start.", "Salotto has not been changed. Try again; if it persists, restart the Stick."),
    "C202": ("Fire OS did not accept the new version.", "Salotto stayed on the previous version. Restart the Stick and try again."),
    "C203": ("After the installation Salotto did not start properly.", "Salotto went back to the previous version. Report the problem."),
    "C204": ("The installed version is not the expected one.", "Salotto went back to the previous version. Report the problem."),
    "C206": ("The new version did not confirm it works in time.",
             "Salotto went back to the previous version. Restart the Stick and try again."),
    "R301": ("The installed version could not be saved before going on.", "Salotto has not been changed. Restart the Stick and try again."),
    "R302": ("Fire OS did not accept the previous version.",
             "From the Mac run install.sh from the distro: it reinstalls Salotto keeping your choices. Then report the problem."),
    "R303": ("The update did not leave a readable outcome.", "Check that Salotto works, then report the problem."),
    "R304": ("On this TV Salotto cannot go back to the previous version by itself.",
             "Open Installa Salotto on the computer and choose “Go back to the previous version”."),
}
# Only for this page: what a code meant on versions that are still out there. English, then Italian.
NOTES = {
    "C202": (f"With a Salotto version older than 1.0.18 this error most likely means there is not enough space on "
             f"the Fire TV for the update. It is usually solved by uninstalling some apps you no longer use, or by "
             f"downloading the new version directly from a computer with [Installa Salotto]({INSTALLER}). From 1.0.18 "
             f"Salotto checks the space before starting and, if it is not enough, says so with SAL-F106.",
             f"Con una versione di Salotto precedente alla 1.0.18 questo errore indica probabilmente che sulla Fire TV "
             f"non c'è spazio sufficiente per l'aggiornamento. Di solito si risolve disinstallando qualche app non più "
             f"in uso, oppure scaricando direttamente la nuova versione dal computer con [Installa Salotto]({INSTALLER_IT}). "
             f"Dalla 1.0.18 Salotto controlla lo spazio prima di iniziare e, se non basta, lo dice con SAL-F106."),
}


def english_titles():
    text = STRINGS_EN.read_text(encoding="utf-8")
    return {code.upper(): title.replace("\\'", "'") for code, title in
            re.findall(r'<string name="update_error_([a-z]\d{3})">([^<]+)</string>', text)}


def italic(text):
    return f"*{text}*"


def main():
    text = SOURCE.read_text(encoding="utf-8")
    found = re.findall(r'\b([FCR]\d{3})\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)"\)', text)
    titles = english_titles()
    missing = [code for code, *_ in found if code not in ENGLISH or code not in titles]
    if missing:
        sys.exit("Manca l'inglese per: " + ", ".join(missing))
    lines = ["# Salotto update errors", "",
             "A code appears on the TV: look for it here. A failed update never leaves Salotto halfway. "
             "If the error comes back every time, at the bottom you will find how to update from a computer.", "",
             italic("Errori dell'aggiornamento di Salotto. Sulla TV compare un codice: cercalo qui. Un aggiornamento "
                    "che fallisce non lascia mai Salotto a metà. Se l'errore torna ogni volta, in fondo trovi come "
                    "aggiornare dal computer."), ""]
    for letter, heading, note, heading_it, note_it in GROUPS:
        lines += [f"## {heading}", "", note, "", italic(f"{heading_it}. {note_it}"), ""]
        for code, title, cause, fix in found:
            if not code.startswith(letter):
                continue
            cause_en, fix_en = ENGLISH[code]
            lines += [f"### SAL-{code}", "", f"**{titles[code]}.** {cause_en}", "", f"What to do: {fix_en}", "",
                      italic(f"{title}. {cause}"), "", italic(f"Cosa fare: {fix}"), ""]
            if code in NOTES:
                lines += [NOTES[code][0], "", italic(NOTES[code][1]), ""]
    lines += ["## If the problem persists", "",
              f"Update Salotto from a computer with [Installa Salotto]({INSTALLER}), for Mac and Windows: it installs the "
              "latest version without going through the TV's update, and your choices stay.", "",
              f"If that is not enough, [open an issue]({ISSUES}) with the code and the versions of Salotto and Fire OS.", "",
              italic(f"Se il problema resta, aggiorna Salotto dal computer con [Installa Salotto]({INSTALLER_IT}), per Mac "
                     "e Windows: installa l'ultima versione senza passare dall'aggiornamento della TV, e le tue scelte restano."), "",
              italic(f"Se non basta, [apri una segnalazione]({ISSUES}) indicando codice, versione di Salotto e di Fire OS."), ""]
    pathlib.Path(sys.argv[1]).write_text("\n".join(lines), encoding="utf-8")
    print(len(found), "errori")


if __name__ == "__main__":
    main()
