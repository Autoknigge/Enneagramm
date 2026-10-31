# Die Matrix des Lebendigen – Das Enneagramm

Leseapp für das Buch von Marc Krekler (166 Seiten): Blättern, Zoomen, Inhaltsverzeichnis, Volltextsuche, Lesezeichen, Hell/Dunkel/Lesemodus. Läuft ohne Internet; Google AdMob zeigt einen Banner (Einwilligungsabfrage inklusive).

Paketname: `de.autoknigge.enneagramm`

## Bauen
Unter **Actions → Build** läuft der Build bei jedem Push. Mit hinterlegten Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) entsteht das Artefakt **Enneagramm** mit `Enneagramm.apk` und `Enneagramm.aab` (signiert).

## Inhalte
`app/src/main/assets/buch.pdf` ist das Buch, `text.json` der Text je Seite für die Suche (mit `pdftotext` erzeugt).

Datenschutzerklärung: https://autoknigge.de/enneagramm/datenschutz.html
