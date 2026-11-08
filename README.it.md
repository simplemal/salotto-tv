<p align="center"><img src="distro/docs/immagini/logo-nome.svg" alt="Salotto" width="180"></p>

<p align="center"><a href="README.md">English</a> · <b>Italiano</b></p>

<p align="center"><br><img src="distro/docs/immagini/home.webp" alt="La Home di Salotto" width="100%"></p>

Una Home pulita per Fire TV Stick: le tue app, lo sfondo, l'orario e il meteo. Niente pubblicità, niente contenuti consigliati e niente video automatici.

## Fire TV supportate

- Fire TV Stick 4K 2ª generazione (2023)
- Fire TV Stick 4K Max 2ª generazione (2023)
- Fire TV Stick 4K Max 1ª generazione (2021), con alcune funzioni limitate
- Fire TV Stick 3ª generazione (2020), con alcune funzioni limitate
- Fire TV Stick Lite 1ª generazione (2020), con alcune funzioni limitate

## Installazione

Installa Salotto guida ogni passo dal computer, e con Togli Salotto rimette tutto com'era.

| Computer | Installer |
|---|---|
| Mac Apple Silicon | **[Scarica Installa Salotto per Mac](https://github.com/simplemal/salotto-tv/releases/latest/download/Installa-Salotto-Mac.dmg)** |
| Windows | **[Scarica Installa Salotto per Windows](https://github.com/simplemal/salotto-tv/releases/latest/download/Installa-Salotto-Windows.exe)** |
| Linux | **[Scarica Installa Salotto per Linux](https://github.com/simplemal/salotto-tv/releases/latest/download/Installa-Salotto-Linux.AppImage)** (AppImage: rendilo eseguibile, poi aprilo) |

## Cosa fa

- **Home:** griglia delle tue app, sfondi che seguono il meteo e l'ora della tua città, oppure illustrazioni, sculture 3D e città notturne che cambiano da sole.
- **Dieci lingue:** italiano, inglese, spagnolo, francese, tedesco, portoghese, russo, arabo, hindi e cinese.
- **Primo avvio:** una presentazione guidata prima della Home.
- **Salvaschermi:** le stesse immagini, con solo ora e data.
- **Centro di controllo:** tieni premuto Menu per Wi-Fi, Bluetooth, timer, luminosità e, dove possibile, filtro luce blu.
- **Task manager:** tieni premuto Indietro.
- **Meno tracciamento:** l'installazione spegne i pacchetti di pubblicità, telemetria e Alexa, limita l'identificativo pubblicitario e ferma l'invio dei dati d'uso. Se Fire OS li riattiva, Salotto te lo dice.
- **Aggiornamenti:** firmati e controllati prima di installarli, con un passo per volta a schermo.

Salotto si collega solo a Open-Meteo per il meteo e a GitHub per gli aggiornamenti. Niente root e niente modifiche al sistema: resta una normale app, e si può disinstallare.

## Com'è

<p align="center"><img src="distro/docs/immagini/home-circolare.webp" alt="Home circolare" width="100%"></p>
<p align="center"><sub>Home circolare, si sceglie in Aspetto › Disposizione</sub></p>

<p align="center">
  <img src="distro/docs/immagini/centro-di-controllo.webp" alt="Centro di controllo" width="32%">
  <img src="distro/docs/immagini/sfondo.webp" alt="Scelta dello sfondo" width="32%">
  <img src="distro/docs/immagini/task-manager.webp" alt="Task manager" width="32%">
</p>
<p align="center"><sub>Centro di controllo · Scelta dello sfondo · Task manager</sub></p>

## Come è fatto il repository

- `home/app`: l'app Salotto, in Java, senza librerie esterne a parte un piccolo generatore di codici QR.
- `home/cover`: una piccola app che tiene a schermo la pagina di aggiornamento mentre Android sostituisce Salotto.
- `distro`: script di installazione e disinstallazione, elenchi dei pacchetti, guide e strumenti di pubblicazione.

Per compilare servono JDK 17, Android SDK e Gradle 8.9: `gradle :app:assembleDebug` dalla cartella `home`.

## Aggiornamenti ed errori

Ogni versione è una [release](https://github.com/simplemal/salotto-tv/releases) con l'APK, `update.json` e la sua firma. Salotto installa solo ciò che è firmato con la chiave giusta.

Se sulla TV compare un codice, cercalo in [ERRORI.md](ERRORI.md). Per tutto il resto [apri una segnalazione](https://github.com/simplemal/salotto-tv/issues/new).

## Sostieni Salotto

Salotto è gratuito e open-source. Se ti è stato utile e vuoi sostenerne lo sviluppo, anche per nuove piattaforme, puoi offrirmi un caffè ☕

<p><a href="https://buymeacoffee.com/saggiamente"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me a Coffee" height="50"></a></p>

## Licenza

[PolyForm Noncommercial 1.0.0](LICENSE): si può usare, studiare, modificare e derivare gratis per scopi non commerciali. Per venderlo serve il permesso dell'autore. Crediti e software di altri sono in [NOTICE.md](NOTICE.md).

Salotto è un progetto indipendente. Non è affiliato, sponsorizzato né approvato da Amazon, Apple o dagli altri marchi citati, che appartengono ai rispettivi proprietari.
