# Registro dei passaggi

Tutto ciò che è stato fatto sulla prima Stick (`AFTKM`, Fire OS 8.1.6.6 poi 8.1.8.5), nell'ordine reale, con ciò che è entrato nella distro e ciò che è stato abbandonato. Nessun root, nessuna modifica a `/system`, nessun factory reset.

Da aggiornare a ogni nuovo passaggio eseguito su un dispositivo.

## Fase 1. Preparazione

| Passaggio | Come | Nella distro |
|---|---|---|
| Debug ADB attivato sulla TV | Opzioni sviluppatore | Guida, a mano |
| Collegamento | USB e rete porta 5555 | `install.sh`, passo 1 |
| Fotografia dello stato iniziale | `settings list`, `pm list packages` | `install.sh`, passo 3 |

## Fase 2. App di base

| Passaggio | Come | Nella distro |
|---|---|---|
| FireStick Fixer 3.1.0 | `adb install`, hash uguale alla release ufficiale | Fino al 2026-11-02 |
| AirPlay Server 0.0.36 | `adb install`, hash uguale alla release ufficiale | Sì |
| Aurora Store 4.8.4 | `adb install`, APK da F-Droid | Sì |
| Aurora: accesso anonimo e profilo Sony BRAVIA VU2 | A mano sulla TV | Guida |
| Aurora: permesso di installare APK | Concesso a mano; ogni installazione mantiene la conferma di sistema | Guida, se serve |
| Projectivy Launcher 4.72 come Home | Provato, poi disinstallato | **No:** offerte Premium per funzioni base |
| FirePlay 0.2.0 | Scaricato come alternativa, mai installato | No |

## Fase 3. Fixer e tasto Home (vecchio)

Storia di com'era. Dal 2026-11-02 la distro non installa più Fixer: il tasto Home lo prende il servizio di Salotto, vedi la fase 5.

| Passaggio | Comando | Nella distro |
|---|---|---|
| Permessi | `pm grant io.github.hatim.firestickfixer android.permission.WRITE_SECURE_SETTINGS` e `READ_LOGS` | Fino al 2026-11-02 |
| Accessibilità | `settings put secure enabled_accessibility_services`, `accessibility_enabled 1` | Fino al 2026-11-02 |
| Destinazione Home | `dumpsys activity service <fixer>/.HijackService prefs import <base64>` | Fino al 2026-11-02, con Salotto |
| Opzioni | `launch_on_boot=true`, `hijack_enabled=true`, `voice_search_to_app=false` | Fino al 2026-11-02 |
| Azioni veloci di Fixer | Script ufficiale `setup-adb.sh`, senza la riga che toglie la scadenza delle chiavi | **No:** dopo il riavvio falliva con `wifi_refused`, e serve solo a chiudere le app |
| Chiave ADB interna di Fixer | Confermata sulla TV | No, legata alle azioni veloci |
| Fiducia permanente della rete | Rifiutata | No |

## Fase 4. Pulizia

| Passaggio | Risultato | Nella distro |
|---|---|---|
| 29 pacchetti pubblicità e telemetria | `pm disable-user --user 0`, tutti riusciti | `config/disattiva-pubblicita.txt` |
| 14 pacchetti Alexa | Tutti riusciti, restano disattivati dopo il riavvio | `config/disattiva-alexa.txt`, facoltativo |
| 2 pacchetti OTA | Rifiutati da Fire OS con `SecurityException` | **No:** protetti |
| 10 impostazioni di privacy | `settings put`, tutte riuscite | `config/privacy.txt` |

Fire OS si è aggiornato da solo da 8.1.6.6 a 8.1.8.5 durante il lavoro. Il blocco degli aggiornamenti non esiste. Dopo l'aggiornamento ADB era `unauthorized`: si risolve spegnendo e riaccendendo Debug ADB.

## Fase 5. Salotto

| Passaggio | Come | Nella distro |
|---|---|---|
| Compilazione | Gradle 8.9, AGP 8.7.3, JDK 17, SDK 35. Comando in `RILASCIO.md` | Sì, come APK nel rilascio |
| Installazione | `adb install -r`, poi `am start`, mai `KEYCODE_HOME` subito dopo | Sì |
| Permessi | `pm grant it.simplemal.firehome android.permission.WRITE_SECURE_SETTINGS` e `READ_LOGS` | Sì |
| App aperte | `appops set it.simplemal.firehome GET_USAGE_STATS allow` | Sì |
| Task manager | Impostazioni → App → Apri tenendo premuto Menu, poi `distro/tools/autorizza-adb.py <ip>` e Consenti sempre sulla TV. adbd non mostra il dialogo alle connessioni nate sulla TV, serve il passaggio dal Mac | Guida e script |
| Fixer su Salotto (vecchio) | `prefs import` con `target_package=it.simplemal.firehome` | Fino al 2026-11-02 |
| Tasto Home e accensione | Servizio `it.simplemal.firehome/it.simplemal.firehome.TaskService` in `enabled_accessibility_services`, `accessibility_enabled 1`, `pm disable-user --user 0 com.amazon.tv.quicksettings.ui` | Sì |
| Fixer tolto dalle Stick vecchie | Via da `enabled_accessibility_services`, poi `adb uninstall io.github.hatim.firestickfixer`. Salotto lo toglie dall'accessibilità anche da sola | Sì |
| Projectivy disinstallato | `adb uninstall com.spocky.projengmenu` | Non serve: non viene installato |
| Luogo del meteo | Da Impostazioni di Salotto | Guida |
| Sfondo personale | `adb push` in `/data/local/tmp`, `run-as` e `cp` in `files/sfondi`. Solo con build debug | No, da fare per dispositivo |

Le scelte dell'interfaccia sono nel `CHANGELOG.md` del progetto, build per build.

## Fase 6. Strumenti sul Mac

| Passaggio | Nella distro |
|---|---|
| Telecomando con anteprima dello schermo, progetto separato | No: si distribuisce a parte |
| Toolchain Android locale | Solo per chi compila, vedi `RILASCIO.md` |

## Cosa fare a mano, sempre

- Configurazione iniziale e account Amazon
- App di streaming e relativi accessi
- Conferme sulla TV
- Luogo del meteo, profilo di Aurora, prima apertura di AirPlay

## Non verificato

- `install.sh` completo su una Stick nuova: finora ne è stata provata solo l'anteprima
- `disinstalla.sh` su un dispositivo vero
- Il passaggio da Fixer al servizio di Salotto con `install.sh` su una Stick vecchia: mai eseguito su un dispositivo
- Riproduzione dei servizi di streaming
- Audio AirPlay: crepitio mai risolto, diagnosi sospesa
