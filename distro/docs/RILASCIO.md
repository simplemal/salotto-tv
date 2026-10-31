# Rilasciare una nuova versione

Repository: [simplemal/salotto-tv](https://github.com/simplemal/salotto-tv), pubblico. È la cartella `launcher/`, ma contiene solo ciò che è elencato nel `.gitignore`: `distro/` e i sorgenti di Salotto in `home/`. Backup, log, schermate, chiavi, file di stato e toolchain restano fuori.

## Versioni

- `distro/VERSIONE`: versione della distro. Cambia a ogni rilascio.
- `home/app/build.gradle`: versione di Salotto. Cambia quando cambia l'app, sempre con un nuovo `versionCode`.
- `distro/config/componenti.txt`: versioni e impronte dei programmi di terzi.

Regola: terzo numero per correzioni, secondo per novità, primo per cambiamenti che richiedono di reinstallare.

## Compilare Salotto

La prima volta serve la toolchain in `home/.toolchain`, esclusa dalla sincronizzazione: Temurin 17, Gradle 8.9, Android SDK 35 e build-tools 34.0.0. Poi, da `home`:

```bash
T="$PWD/.toolchain"; JAVA_HOME="$T/jdk-17.0.21+9/Contents/Home" ANDROID_HOME="$T/android-sdk" GRADLE_USER_HOME="$T/gradle-home" "$T/gradle-8.9/bin/gradle" --no-daemon --console=plain :app:assembleDebug :app:lintDebug
```

## Firma

Le versioni nuove si installano sopra le vecchie solo se firmate con la stessa chiave. La chiave non va mai nel repository: conservala a parte, con una copia di sicurezza. Se si perde, chi ha già Salotto deve disinstallarlo e reinstallarlo.

Oggi l'APK è una build debug. Permette di caricare gli sfondi con `run-as`, ma va sostituita da una firma definitiva prima di un rilascio pubblico stabile.

## Aggiornare un programma di terzi

1. Scarica la nuova versione dal progetto originale.
2. Calcola l'impronta con `shasum -a 256`.
3. Aggiorna `distro/config/componenti.txt`.
4. Provala su una Stick prima di rilasciare.

## Preparare il rilascio

1. Aggiorna `distro/VERSIONE`, `CHANGELOG.md` e `docs/REGISTRO-PASSAGGI.md`.
2. Compila Salotto.
3. Prova `./install.sh <ip> --anteprima` su una Stick.
4. Crea il pacchetto, che si ferma se trova dati personali. Li cerca nel file `distro/dati-personali.local`, una voce per riga, che resta solo sul tuo Mac: aggiungi nomi, indirizzi e seriali di ogni nuovo dispositivo.

```bash
distro/tools/prepara-rilascio.sh
```

5. Prova il pacchetto su una Stick reale.

## Pubblicare

Dalla cartella `launcher`, con la versione giusta al posto di `0.1.0`:

```bash
git tag v0.1.0 && git push origin main v0.1.0
```

```bash
gh release create v0.1.0 rilasci/salotto-distro-0.1.0.zip --title "Salotto 0.1.0" --notes-file distro/docs/NOTE-RILASCIO.md
```

## Aggiornare Salotto sulle Stick

1. Alza `versionCode` e `versionName` in `home/app/build.gradle` e compila.
2. Lancia `distro/tools/pubblica-aggiornamento.sh "prima novità" "seconda novità"`.

Lo strumento controlla versione e dati personali, firma `update.json`, aggiorna `ERRORI.md` e crea la release in questo repository. Le Stick la propongono al controllo successivo. Le novità compaiono così sulla TV: scrivile per chi la usa.

## Prima del primo rilascio pubblico

- [ ] Licenza di Salotto scelta. Fixer, AirPlay Server e Aurora sono GPL-3.0, e non vengono ridistribuiti
- [ ] Firma definitiva
- [ ] Provata su una seconda Stick
- [ ] Controllo di privacy di tutta la storia Git, non solo dell'ultima versione
- [ ] Nomi e marchi: «Fire TV» e «Apple TV» si citano solo per descrivere la compatibilità
