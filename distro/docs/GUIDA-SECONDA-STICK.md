# Guida: installare Salotto su un'altra Fire TV Stick

**Senza terminale:** l'app Installa Salotto, in `installatore/`, fa gli stessi passi in una sola finestra. Questa guida resta per chi usa `install.sh`.

Da seguire in ordine. Tempo previsto: circa 30 minuti, più gli aggiornamenti di Fire OS.

## Prima di iniziare

Servono il telecomando della Stick, una TV, il Mac sulla stessa rete Wi-Fi e l'account Amazon.

**Durante tutta la procedura il telecomando deve restare a portata di mano:** alcuni dialoghi si confermano solo sulla TV.

## 1. Sulla Fire TV

1. Completa la prima configurazione Amazon e accedi al tuo account.
2. Lascia finire eventuali aggiornamenti di Fire OS.
3. Dall'Appstore Amazon installa le app che vuoi: Netflix, Disney+, Apple TV, NOW, Plex, Pluto TV, RaiPlay, YouTube. Prime Video c'è già. Accedi agli account adesso o dopo.
4. Apri Impostazioni → La mia Fire TV → Opzioni sviluppatore.
5. Se Opzioni sviluppatore non c'è: La mia Fire TV → Informazioni, poi premi OK sette volte sul nome del dispositivo.
6. Attiva **Debug ADB**.
7. Prendi nota dell'indirizzo IP: Impostazioni → La mia Fire TV → Informazioni → Rete.

Il punto 5 viene dalla procedura generale di Fire OS: sulla prima Stick le opzioni erano già visibili.

## 2. Sul Mac

Se `adb` non è presente:

```bash
brew install android-platform-tools
```

Controlla cosa succederebbe, senza cambiare nulla. Sostituisci l'indirizzo con quello della Stick:

```bash
cd /percorso/di/distro && ./install.sh 192.168.1.50 --anteprima
```

La prima volta sulla TV compare il dialogo «Consentire il debug USB?»: seleziona **Consenti**. «Consenti sempre» riguarda questo Mac e va bene; lascia invece disattivate le opzioni di fiducia permanente della rete, se compaiono.

Leggi il riepilogo. Se dice che il modello o la versione di Fire OS sono diversi, fermati e controlla prima di andare avanti: i pacchetti Amazon possono cambiare.

## 3. Installazione

```bash
cd /percorso/di/distro && ./install.sh 192.168.1.50
```

L'installatore salva una copia dello stato in `~/Salotto-backup`, poi fa queste cose, chiedendo conferma dove indicato.

| Passo | Cosa fa | Conferma |
|---|---|---|
| App | Scarica e installa Fixer, AirPlay, Aurora e Salotto | No |
| Permessi di Salotto | Lo autorizzano a cambiare salvaschermo e CEC, e a vedere le app aperte | No |
| Home | Attiva Fixer nell'accessibilità e lo punta su Salotto | Sì |
| Pubblicità e telemetria | Disattiva 29 pacchetti | Sì |
| Alexa | Disattiva 14 pacchetti. **Toglie i comandi vocali** | Sì |
| Privacy | Limita pubblicità personalizzata e dati di utilizzo | Sì |

Se qualcosa non va, lo script si ferma e dice perché. Si può rilanciare: salta ciò che è già fatto.

## 4. Passaggi a mano sulla TV

1. **Tasto Home:** premilo. Deve aprirsi Salotto. Riavvia la Stick una volta e controlla che riparta su Salotto.
2. **Meteo:** Impostazioni di Salotto → Meteo → Luogo.
3. **AirPlay:** parte da solo circa due minuti dopo l'accensione: se è appena installato, aprilo una volta.
4. **Aurora Store:** aprilo e scegli l'accesso anonimo. Nelle impostazioni del dispositivo scegli il profilo **Sony BRAVIA VU2**, API 30, `armeabi-v7a`: serve per ricevere le versioni Android TV. Dopo il cambio ripeti l'accesso anonimo.
5. **Home:** tieni premuto OK su una card per spostarla, toglierla, vedere le informazioni o disinstallare. Le altre app sono in Tutte le app.
6. **Task manager:** Impostazioni di Salotto → App → Apri tenendo premuto Menu. Poi dal Mac lancia `distro/tools/autorizza-adb.py <ip>` e sulla TV premi OK sulla casella **Consenti sempre**, freccia giù, OK su Consenti: la selezione parte già sulla casella. Serve una volta sola.
7. **Salvaschermo:** Impostazioni di Salotto → Schermo e audio → Salvaschermo.

## 5. Cosa controllare alla fine

- [ ] Home apre Salotto, anche dopo un riavvio
- [ ] Con «Home Fire TV con doppio Home» attivo in Strumenti, il doppio Home apre la Home Amazon, e un'app la richiude
- [ ] Le app si aprono e riproducono: sulla prima Stick la riproduzione non è stata provata servizio per servizio
- [ ] Indietro risale nelle Impostazioni di Salotto
- [ ] Meteo aggiornato
- [ ] AirPlay visibile da iPhone

## Problemi noti

| Sintomo | Soluzione |
|---|---|
| `unauthorized` e nessun dialogo sulla TV | Spegni e riaccendi Debug ADB in Opzioni sviluppatore, poi conferma il dialogo |
| Dopo un'installazione la Stick resta sulla Home Amazon | Apri una qualsiasi app e premi Home: era un doppio Home |
| Home non apre più Salotto | Fixer scollegato. Non usare `monkey` o `uiautomator`: sospendono l'accessibilità. Riavvia la Stick |
| La rete cade e i tasti non rispondono | È attiva Duplicazione schermo. Via USB: `adb shell am force-stop com.amazon.cast.sink` |
| Fire OS si aggiorna da solo | Può succedere. Rilancia l'anteprima e controlla Fixer |

## Come tornare indietro

```bash
./disinstalla.sh 192.168.1.50 ~/Salotto-backup/<cartella>
```
