# Enneagramm Lern-Heft

Lern- und Info-App zum Enneagramm: Lernposter (Lern-Heft), Typen-Übersicht, Karteikarten und Quiz. Läuft ohne Internet; Google AdMob zeigt einen Banner (Einwilligungsabfrage inklusive).

Paketname: `de.autoknigge.enneagramm`

## Bauen
Unter **Actions → Build** läuft der Build bei jedem Push. Mit hinterlegten Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) entsteht das Artefakt **Enneagramm** mit `Enneagramm.apk` und `Enneagramm.aab` (signiert), sonst eine Test-APK.

## Inhalte
`app/src/main/assets/heft.html` enthält die Lernposter, `data.json` die Tafeln für Typen, Karteikarten und Quiz.

Datenschutzerklärung: https://autoknigge.de/enneagramm/datenschutz.html
