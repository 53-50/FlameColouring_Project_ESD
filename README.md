# FlameColouring Project

Android-Prototyp zur lokalen, bildbasierten Analyse von Flammfärbungen mit Referenzvergleich.

## Disclaimer

Diese App wurde KI-gestützt entwickelt.

## Ziel

Die App soll Flammenbilder lokal auf einem Android-Gerät auswerten und unbekannte Proben mit zuvor aufgenommenen Referenzmessungen vergleichen. Der Fokus liegt auf einem funktionierenden MVP für Android 8/9, ohne Internet und ohne Datenbank.

## Tech Stack

- Kotlin
- Android Studio
- Android Views / XML
- Camera2 API (`TextureView`, manueller Sensor-Lock mit Auto-Freeze-Fallback)
- HSV-Farbvektor-Analyse mit zonenbasierter ROI-Auswertung
- In-Memory-Session (`DataManager` / `MeasurementSession`)
- CSV-Export
- GitHub

## Labor-Workflow (Session)

1. **Baseline** — Flamme aus, nur Umgebungslicht in der ROI; danach Kamera-Lock für die Session
2. **Reference** — bis zu 5 Elemente, baseline-korrigiert
3. **Sample** — bis zu 3 Proben, baseline-korrigiert
4. **Comparison** — gewichteter HSV-Vergleich (Full + Top/Middle/Bottom)
5. **Export** — CSV mit Zonen-Spalte

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
