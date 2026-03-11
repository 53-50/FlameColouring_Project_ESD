# FlameColouring Project powered by ESD (Exellent Software Design - Emanuel Sarah David)

Android-Prototyp zur lokalen, bildbasierten Analyse von Flammfärbungen mit Referenzvergleich.

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

Für den Start des Projekts braucht ihr:

- Android Studio
- Android SDK
- JDK / eingebettetes JDK von Android Studio
- ein Android-Testgerät oder einen Emulator
- Git / GitHub

## Kotlin / Android Setup

Für Kotlin selbst ist in der Regel nichts extra nötig, da Kotlin in Android Studio direkt integriert ist.

### Benötigt

- Android Studio installieren
- neues Android-Projekt mit **Kotlin** anlegen
- `minSdk` passend setzen, z. B. Android 8 / API 26
- Projekt einmal erfolgreich starten
- GitHub-Repository verbinden

### Empfehlung für dieses Projekt

- Projekttyp: **Empty Activity**
- Sprache: **Kotlin**
- UI: **Views / XML**
- Minimum SDK: **API 26** für den Haupt-MVP
- Zielplattform: Android 8/9

## Projektstruktur

Empfohlene grobe Struktur:

```text
app/
 └── src/main/
      ├── java/.../ui
      ├── java/.../camera
      ├── java/.../analysis
      ├── java/.../session
      ├── java/.../export
      └── res/
