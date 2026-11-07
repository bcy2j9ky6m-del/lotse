# Lotse

Ein Android-Handy, das man nur per Sprache bedient – gebaut für Omama.

## Aktuelle Testversion installieren

1. Auf dem Testhandy einmalig: *Einstellungen → Sicherheit → Unbekannte Herkunft* einschalten.
2. Im Browser des Handys öffnen: **Releases → „Lotse – aktuelle Testversion“ → `lotse.apk`**
   (direkter Link: `https://github.com/<DEIN-NAME>/lotse/releases/download/aktuell/lotse.apk`)
3. Heruntergeladene Datei antippen → *Installieren*.

Neue Versionen werden nach jeder Änderung automatisch gebaut (Reiter *Actions*, dauert ca. 5 Minuten) und lassen sich über die alte Version installieren.

## Stand

- Schritt 1: Ruhebildschirm mit Wochentag, Datum, Uhrzeit, Tageszeit und großer Sprechtaste ✔

## Hinweis

Die App wird mit einem **Test-Schlüssel** (`app/lotse-test.keystore`) signiert. Vor dem Einsatz auf Omamas Handy wird er durch einen geheimen Schlüssel ersetzt. Zugangsschlüssel (z. B. für Claude) gehören nie in dieses Projekt, sondern auf den Server.
