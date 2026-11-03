#!/usr/bin/env python3
"""Builds the public ERRORI.md from UpdateError.java: one list, so the page cannot fall behind.

Usage: genera-errori.py <output-file>
"""
import pathlib
import re
import sys

JAVA = pathlib.Path(__file__).resolve().parents[2] / "home/app/src/main/java/it/simplemal/firehome"
SOURCE = JAVA / "UpdateError.java"
REPO = re.search(r'UPDATES_REPO = "([^"]+)"', (JAVA / "Fire.java").read_text(encoding="utf-8")).group(1)
GROUPS = (("F", "Prima di modificare qualcosa", "Salotto non è stata modificata."),
          ("C", "Durante l'installazione", "Salotto è rimasta o è tornata alla versione precedente."),
          ("R", "Durante il ritorno alla versione precedente", "Segui le istruzioni di ogni voce."))
ISSUES = f"https://github.com/{REPO}/issues/new"
# The README's table with the Mac and Windows downloads.
INSTALLER = f"https://github.com/{REPO}#installazione"
# Only for this page: what a code meant on versions that are still out there.
NOTES = {
    "C202": "Con una versione di Salotto precedente alla 1.0.18 questo errore indica probabilmente che sulla Fire TV "
            "non c'è spazio sufficiente per l'aggiornamento. Di solito si risolve disinstallando qualche app non più "
            f"in uso, oppure scaricando direttamente la nuova versione dal computer con [Installa Salotto]({INSTALLER}). "
            "Dalla 1.0.18 Salotto controlla lo spazio prima di iniziare e, se non basta, lo dice con SAL-F106.",
}


def main():
    text = SOURCE.read_text(encoding="utf-8")
    found = re.findall(r'\b([FCR]\d{3})\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)"\)', text)
    lines = ["# Errori dell'aggiornamento di Salotto", "",
             "Sulla TV compare un codice: cercalo qui. Un aggiornamento che fallisce non lascia mai Salotto a metà. "
             "Se l'errore torna ogni volta, in fondo trovi come aggiornare dal computer.", ""]
    for letter, heading, note in GROUPS:
        lines += [f"## {heading}", "", note, ""]
        for code, title, cause, fix in found:
            if code.startswith(letter):
                lines += [f"### SAL-{code}", "", f"**{title}.** {cause}", "", f"Cosa fare: {fix}", ""]
                if code in NOTES:
                    lines += [NOTES[code], ""]
    lines += ["## Il problema resta", "",
              f"Aggiorna Salotto dal computer con [Installa Salotto]({INSTALLER}), per Mac e Windows: installa l'ultima versione "
              "senza passare dall'aggiornamento della TV, e le tue scelte restano.", "",
              f"Se non basta, [apri una segnalazione]({ISSUES}) indicando codice, versione di Salotto e di Fire OS.", ""]
    pathlib.Path(sys.argv[1]).write_text("\n".join(lines), encoding="utf-8")
    print(len(found), "errori")


if __name__ == "__main__":
    main()
