<p align="center"><img src="distro/docs/immagini/logo-nome.svg" alt="Salotto" width="180"></p>

<p align="center"><b>English</b> · <a href="README.it.md">Italiano</a></p>

<p align="center"><br><img src="distro/docs/immagini/home.webp" alt="Salotto's Home" width="100%"></p>

A clean Home for Fire TV Stick: your apps, the backdrop, the time and the weather. No ads, no recommended content and no autoplaying videos.

## Supported Fire TVs

- Fire TV Stick 4K 2nd generation (2023)
- Fire TV Stick 4K Max 2nd generation (2023)
- Fire TV Stick 4K Max 1st generation (2021), with some features limited
- Fire TV Stick 3rd generation (2020), with some features limited

## Installation

Installa Salotto guides every step from the computer, and with Remove Salotto it puts everything back as it was.

| Computer | Installer |
|---|---|
| Mac Apple Silicon | **[Download Installa Salotto for Mac](https://github.com/simplemal/salotto-tv/releases/latest/download/Installa-Salotto-Mac.dmg)** |
| Windows | **[Download Installa Salotto for Windows](https://github.com/simplemal/salotto-tv/releases/latest/download/Installa-Salotto-Windows.exe)** |
| Linux | **[Download Installa Salotto for Linux](https://github.com/simplemal/salotto-tv/releases/latest/download/Installa-Salotto-Linux.AppImage)** (AppImage: make it executable, then open it) |

## What it does

- **Home:** a grid of your apps, backdrops that follow the weather and the time of your city, or illustrations, 3D sculptures and night cities that change on their own.
- **Ten languages:** Italian, English, Spanish, French, German, Portuguese, Russian, Arabic, Hindi and Chinese.
- **First start:** a guided tour before the Home.
- **Screensavers:** the same pictures, with just the time and date.
- **Control center:** hold Menu for Wi-Fi, Bluetooth, timer, brightness and, where possible, the blue light filter.
- **Task manager:** hold Back.
- **Less tracking:** the installation turns off the advertising, telemetry and Alexa packages, limits the advertising ID and stops sending usage data. If Fire OS turns them back on, Salotto tells you.
- **Updates:** signed and checked before they are installed, one step at a time on screen.

Salotto connects only to Open-Meteo for the weather and to GitHub for updates. No root and no changes to the system: it stays a normal app, and it can be uninstalled.

## How it looks

<p align="center">
  <img src="distro/docs/immagini/centro-di-controllo.webp" alt="Control center" width="32%">
  <img src="distro/docs/immagini/sfondo.webp" alt="Choosing the backdrop" width="32%">
  <img src="distro/docs/immagini/task-manager.webp" alt="Task manager" width="32%">
</p>
<p align="center"><sub>Control center · Choosing the backdrop · Task manager</sub></p>

## How the repository is made

- `home/app`: the Salotto app, in Java, with no external libraries apart from a small QR code generator.
- `home/cover`: a small app that keeps the update page on screen while Android replaces Salotto.
- `distro`: install and uninstall scripts, package lists, guides and publishing tools.

To build you need JDK 17, the Android SDK and Gradle 8.9: `gradle :app:assembleDebug` from the `home` folder.

## Updates and errors

Every version is a [release](https://github.com/simplemal/salotto-tv/releases) with the APK, `update.json` and its signature. Salotto installs only what is signed with the right key.

If a code appears on the TV, look for it in [ERRORI.md](ERRORI.md). For anything else [open an issue](https://github.com/simplemal/salotto-tv/issues/new).

## License

[PolyForm Noncommercial 1.0.0](LICENSE): it can be used, studied, modified and built upon for free for non-commercial purposes. Selling it requires the author's permission. Credits and third-party software are in [NOTICE.md](NOTICE.md).

Salotto is an independent project. It is not affiliated with, sponsored or endorsed by Amazon, Apple or the other brands mentioned, which belong to their respective owners.
