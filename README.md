# Ancient Reminder 2.1 (Fabric, Minecraft 1.21.11)

## Bauen
GitHub Actions (Workflow aus der Anleitung) oder lokal mit JDK 21 und `gradle build`.
Ergebnis: `build/libs/ancient-reminder-2.1.0.jar` (die Datei ohne `-sources`).
Alte Ancient-Reminder-jars im mods-Ordner löschen (gleiche Mod-ID).

## Bedienung
- Menü: Rechte Umschalt-Taste oder `/ancient` (Seitenleiste, Schalter, Schieberegler, Mausrad)
- Chunkgrenzen an/aus: F9
- `/ancient hud`, `/ancient stats`, `/ancient rescan`, `/ancient calibrate reset`,
  `/ancient tunnel clear`, `/ancient chunks`

## Neu in 2.1
- Komplett neues Menü und neue HUD-Optik (Titelzeile, Akzentstreifen, Fortschrittsbalken)
- HUD-Editor mit Einrasten und Hilfslinien
- Chunk-Bewertung: Chunkgrenzen im Nether nach dem Scan eingefärbt, Richtung zum besten Chunk im HUD
- Gang an der Chunkgrenze (Aus / Bevorzugen / Nur dort), Statistik vergleicht mit und ohne Grenze
- Realistischere Skala für "% abgebaut", Rohwert ("Offen ... % | Grund ... %") im HUD
