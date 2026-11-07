# Lotse

Ein Android-Handy, das man nur per Sprache bedient – gebaut für Omama.

## Aktuelle Testversion installieren

1. Auf dem Testhandy einmalig: *Einstellungen → Sicherheit → Unbekannte Herkunft* einschalten.
2. Im Browser des Handys öffnen: `https://github.com/bcy2j9ky6m-del/lotse/releases/latest`
3. Unter *Assets* die Datei `lotse-0.1.<Nummer>.apk` antippen → herunterladen → *Installieren*.
4. Kontrolle: Die Versionsnummer steht unten auf dem Ruhebildschirm von Lotse.

Neue Versionen werden nach jeder Änderung automatisch gebaut (Reiter *Actions*, dauert ca. 5 Minuten) und lassen sich über die alte Version installieren.

## Stand

- Schritt 1: Ruhebildschirm mit Wochentag, Datum, Uhrzeit, Tageszeit und großer Sprechtaste ✔
- Schritt 2: Zuhören (Spracherkennung Deutsch) und Nachsprechen (Sprachausgabe) ✔
- Schritt 3: Antworten über den Lotse-Server (`server/worker.js`, Cloudflare Worker, zum Testen mit Gemini); Uhrzeit und Datum beantwortet Lotse auch ohne Internet ✔
- Schritt 4: Adressbuch, Anrufen mit Rückfrage („Ich rufe jetzt … an. Okay?“), Auswahl bei mehreren Treffern, „Wer hat angerufen?“ ✔

## Hinweis

Die App wird mit einem **Test-Schlüssel** (`app/lotse-test.keystore`) signiert. Vor dem Einsatz auf Omamas Handy wird er durch einen geheimen Schlüssel ersetzt. Zugangsschlüssel (z. B. für Claude) gehören nie in dieses Projekt, sondern auf den Server.
