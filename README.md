# FlameColouring Project powered by ESD (Exellent Software Design - Emanuel Sarah David)

Android-Prototyp zur lokalen, bildbasierten Analyse von Flammfärbungen mit Referenzvergleich.

## Disclaimer

Diese App wurde KI-gestützt entwickelt.

## Ziel

Die App soll Flammenbilder lokal auf einem Android-Gerät auswerten und unbekannte Proben mit zuvor aufgenommenen Referenzmessungen vergleichen. Der Fokus liegt auf einem funktionierenden MVP für Android 8/9, ohne Internet und ohne Datenbank.

## Tech Stack

- Kotlin
- Android Studio
- Android Views / XML
- CameraX
- OpenCV
- CSV-Export
- GitHub für Versionsverwaltung

## Voraussetzungen

- Android Studio
- Android SDK
- JDK / eingebettetes JDK von Android Studio
- ein Android-Testgerät oder einen Emulator
- Git / GitHub

## Projektstruktur

```text
app/
 └── src/main/
      ├── java/.../activities
      ├── java/.../camera
      ├── java/.../models
      ├── java/.../session_data
      ├── java/.../util
      └── res/
