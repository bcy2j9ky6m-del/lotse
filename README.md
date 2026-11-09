# Lotse

Ein Android-Handy, das man nur per Sprache bedient – gebaut für Omama.

## Aktuelle Testversion installieren

1. Auf dem Testhandy einmalig: *Einstellungen → Sicherheit → Unbekannte Herkunft* einschalten.
2. Im Browser des Handys öffnen: `https://raw.githubusercontent.com/bcy2j9ky6m-del/lotse/downloads/lotse-0.1.<Nummer>.apk`
3. Heruntergeladene Datei antippen → *Installieren*.
   Ab Version 0.1.15 geht es auch per Sprachbefehl: „Update“.
4. Kontrolle: Die Versionsnummer steht unten auf dem Ruhebildschirm von Lotse.

Neue Versionen werden nach jeder Änderung automatisch gebaut (Reiter *Actions*, dauert ca. 5 Minuten) und lassen sich über die alte Version installieren.

## Stand

- Schritt 1: Ruhebildschirm mit Wochentag, Datum, Uhrzeit, Tageszeit und großer Sprechtaste ✔
- Schritt 2: Zuhören (Spracherkennung Deutsch) und Nachsprechen (Sprachausgabe) ✔
- Schritt 3: Antworten über den Lotse-Server (`server/worker.js`, Cloudflare Worker, zum Testen mit Gemini); Uhrzeit und Datum beantwortet Lotse auch ohne Internet ✔
- Schritt 4: Adressbuch, Anrufen mit Rückfrage („Ich rufe jetzt … an. Okay?“), Auswahl bei mehreren Treffern, „Wer hat angerufen?“ ✔
- Schritt 5: Kalender vorlesen (heute, morgen, Wochentag, nächste Woche), „Wann kommt …?“, nächster Termin auf dem Ruhebildschirm, Morgengruß ab 9 Uhr ✔
- Schritt 6: SMS vorlesen („Lies meine Nachrichten vor“), Ansage bei neuer SMS, schreiben („Schreib Anna …“), antworten („Antworte …“), Warnung bei unbekannten Nummern und Betrugsverdacht ✔
- Schritt 7: Lotse als Telefon-App („Telefon einrichten“) – eigener Anrufbildschirm mit Ansage des Anrufers, ANNEHMEN, Ablehnen, Lautsprecher, AUFLEGEN, Warnung bei unbekannten Nummern ✔
- Schritt 9: Kiosk-Modus (Geräteinhaber per adb), Lotse als Startbildschirm, keine Statusleiste, kein Sperrbildschirm; Pflege-Zugang: 7× auf den Wochentag tippen → PIN → Einstellungen / Kiosk ausschalten ✔
- Gedächtnis: letzte Fragen/Antworten (30 Min.), „Merk dir, dass …“, „Was weißt du über mich?“, „Vergiss …“ – nur auf dem Handy gespeichert ✔
- Stimme: beste deutsche Offline-Stimme automatisch, „Andere Stimme“, „Sprich langsamer/schneller/normal“ ✔
- Natürliche KI-Stimme (Gemini TTS über den Server, 6 Stimmen, „Andere Stimme“, „Handystimme“ / „Natürliche Stimme“), Rückfall auf die Handy-Stimme ✔
- Selbst-Update per Sprachbefehl („Update“, „Aktualisiere dich“): sucht die neueste Version, lädt sie und öffnet die Installation ✔

## Hinweis

Die App wird mit einem **Test-Schlüssel** (`app/lotse-test.keystore`) signiert. Vor dem Einsatz auf Omamas Handy wird er durch einen geheimen Schlüssel ersetzt. Zugangsschlüssel (z. B. für Claude) gehören nie in dieses Projekt, sondern auf den Server.
