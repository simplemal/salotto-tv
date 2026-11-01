# Salotto TV

Una Fire TV Stick più pulita, senza root: una Home in stile Apple TV al posto di quella Amazon, meno pubblicità e telemetria, AirPlay in ricezione e uno store di app.

Non è un sistema operativo: non cambia Fire OS, non sblocca il dispositivo e si può togliere. È una raccolta di app e impostazioni applicate via ADB.

## Cosa contiene

| Parte | Da dove arriva | Cosa fa |
|---|---|---|
| Salotto | questo progetto | La nuova Home, con dock, meteo e Impostazioni |
| [FireStick Fixer](https://github.com/Hornet34/firestick-fixer) 3.1.0 | scaricato dall'originale | Porta il tasto Home e l'accensione su Salotto |
| [AirPlay Server](https://github.com/jqssun/android-airplay-server) 0.0.36 | scaricato dall'originale | Riceve video da iPhone, iPad e Mac |
| [Aurora Store](https://f-droid.org/packages/com.aurora.store/) 4.8.4 | scaricato da F-Droid | Installa app Android TV senza account |
| Elenchi di pacchetti e impostazioni | questo progetto | Disattivano 29 pacchetti di pubblicità e telemetria e, se vuoi, 14 di Alexa |

I programmi di terzi non sono inclusi: l'installatore li scarica dai progetti originali e ne controlla l'impronta SHA-256.

## Dispositivi

| Modello | Fire OS | Stato |
|---|---|---|
| Fire TV Stick 4K 2ª gen (`AFTKM`) | 8.1.6.6 e 8.1.8.5 | Provato a mano. L'installatore automatico non è ancora stato eseguito fino in fondo |

Su altri modelli l'installatore avvisa e chiede conferma.

## Installazione

Serve un Mac con `adb`. La procedura completa è in [docs/GUIDA-SECONDA-STICK.md](docs/GUIDA-SECONDA-STICK.md).

```bash
./install.sh <ip-della-fire-tv> --anteprima
```

```bash
./install.sh <ip-della-fire-tv>
```

Il primo comando legge soltanto e mostra cosa farebbe. Il secondo salva una copia dello stato e chiede conferma per ogni gruppo di modifiche.

## Limiti noti

- Fire OS può aggiornarsi da solo: i pacchetti degli aggiornamenti non sono disattivabili.
- La Home Amazon resta sotto: si riapre attivando «Home Fire TV con doppio Home» in Impostazioni → Strumenti.
- AirPlay parte da solo circa due minuti dopo l'accensione, non gestisce contenuti protetti da DRM e non equivale ad AirPlay 2 completo.
- Suoni e schermo e il selettore profili sono riservati alle app di sistema: Salotto non può aprirli direttamente.
- Disattivare i pacchetti Alexa toglie i comandi vocali.

## Togliere tutto

```bash
./disinstalla.sh <ip-della-fire-tv> <cartella-backup>
```

La cartella di backup è quella creata dall'installatore in `~/Salotto-backup`.

## Documenti

- [Registro dei passaggi](docs/REGISTRO-PASSAGGI.md): tutto ciò che è stato fatto sulla prima Stick, e perché
- [Rilascio su GitHub](docs/RILASCIO.md): come pubblicare una nuova versione

## Licenza

Si può usare e derivare, non vendere: `LICENSE` e `NOTICE.md` nella cartella `launcher`. Tutti i marchi appartengono ai rispettivi proprietari.
