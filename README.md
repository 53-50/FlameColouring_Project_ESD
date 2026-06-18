# FlameColouring Project powered by ESD (Exellent Software Design - Emanuel Sarah David)

Android-Prototyp zur lokalen, bildbasierten Analyse von Flammfärbungen mit Referenzvergleich.

## Ziel

Die App soll Flammenbilder lokal auf einem Android-Gerät auswerten und unbekannte Proben mit zuvor aufgenommenen Referenzmessungen vergleichen. Der Fokus liegt auf einem funktionierenden MVP für Android 8/9, ohne Internet und ohne Datenbank.

## Tech Stack

- Kotlin
- Android Studio
- Android Views / XML
- Camera2 API (`TextureView`, manueller Sensor-Lock mit Auto-Freeze-Fallback)
- HSV-Farbvektor-Analyse mit zonenbasierter ROI-Auswertung (kein OpenCV)
- In-Memory-Session (`DataManager` / `MeasurementSession`)
- CSV-Export
- GitHub für Versionsverwaltung

## Labor-Workflow (Session)

1. **Baseline** — Flamme aus, nur Umgebungslicht in der ROI; danach Kamera-Lock für die Session
2. **Reference** — bis zu 5 Elemente, baseline-korrigiert
3. **Sample** — bis zu 3 Proben, baseline-korrigiert
4. **Comparison** — gewichteter HSV-Vergleich (Full + Top/Middle/Bottom)
5. **Export** — CSV mit Zonen-Spalte

**Hinweis:** Beim erneuten Aufnehmen der Baseline werden vorhandene Referenzen und Proben verworfen (Warnung vorher). Session-Daten sind flüchtig — regelmäßig exportieren.

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

```text
app/src/main/java/at/hcw/flaminco/
 ├── MainActivity.kt              # Hauptmenü, Export, Demo
 ├── BaselineActivity.kt          # Baseline-Aufnahme
 ├── ReferenceActivity.kt         # Referenz-Aufnahme
 ├── SampleActivity.kt            # Proben-Aufnahme
 ├── ComparisonActivity.kt        # Vergleich
 ├── CameraPreviewSession.kt      # Kamera-Preview / Auto-Freeze
 ├── CameraSessionSetup.kt        # Session-Kamera-Lock
 ├── ZonedFrameCapture.kt         # ROI + Zonen-Frame-Analyse
 ├── MeasurementSequencer.kt      # Countdown + Messfenster
 ├── DataManager.kt               # Session-State
 └── model/                       # Messdaten, Vektoren, Session
app/src/main/res/
 ├── layout/                      # UI-Layouts
 └── values/strings.xml           # UI-Texte (Englisch)
```
