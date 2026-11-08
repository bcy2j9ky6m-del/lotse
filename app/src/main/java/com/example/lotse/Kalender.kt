package com.example.lotse

// Termine aus dem Kalender des Handys lesen und vorlesbar formulieren.
// Funktioniert mit allen Kalendern, die auf dem Handy eingerichtet sind (auch Google Kalender).

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class Termin(val titel: String, val beginn: Long, val ganztags: Boolean, val ort: String?)

object Kalender {

    private val deutsch = Locale.GERMANY

    fun tagesbeginn(versatzTage: Int = 0): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, versatzTage)
    }

    // Alle Termine ab "von" (Tagesbeginn) für die angegebene Zahl von Tagen
    fun termine(context: Context, von: Calendar, tage: Int): List<Termin> {
        val anfang = von.timeInMillis
        val ende = (von.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, tage) }.timeInMillis
        val tag = 24L * 60 * 60 * 1000

        val adresse = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, anfang - tag)
            ContentUris.appendId(it, ende + tag)
        }.build()

        val liste = mutableListOf<Termin>()
        context.contentResolver.query(
            adresse,
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION
            ),
            null, null,
            CalendarContract.Instances.BEGIN + " ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val titel = c.getString(0)?.takeIf { it.isNotBlank() } ?: "Termin"
                val beginn = c.getLong(1)
                val ganztags = c.getInt(2) == 1
                val ort = c.getString(3)?.takeIf { it.isNotBlank() }
                // Ganztägige Termine sind in UTC gespeichert – auf den lokalen Kalendertag umrechnen
                val vergleich = if (ganztags) ganztagsBeginnLokal(beginn) else beginn
                if (vergleich in anfang until ende) liste.add(Termin(titel, vergleich, ganztags, ort))
            }
        }
        // Zweiter Weg: einfache (nicht wiederkehrende) Termine direkt aus der Termin-Tabelle lesen.
        // Manche ältere Handys füllen die "Instances"-Liste nicht zuverlässig.
        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.EVENT_LOCATION
                ),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ? AND ${CalendarContract.Events.DELETED} = 0",
                arrayOf((anfang - tag).toString(), (ende + tag).toString()),
                CalendarContract.Events.DTSTART + " ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val titel = c.getString(0)?.takeIf { it.isNotBlank() } ?: "Termin"
                    val beginn = c.getLong(1)
                    val ganztags = c.getInt(2) == 1
                    val ort = c.getString(3)?.takeIf { it.isNotBlank() }
                    val vergleich = if (ganztags) ganztagsBeginnLokal(beginn) else beginn
                    val schonDa = liste.any { it.titel == titel && it.beginn == vergleich }
                    if (!schonDa && vergleich in anfang until ende) liste.add(Termin(titel, vergleich, ganztags, ort))
                }
            }
        } catch (_: Exception) {
            // Wenn das nicht geht, bleibt es bei der ersten Liste
        }

        return liste.sortedWith(compareBy({ !it.ganztags }, { it.beginn }))
    }

    // Einen Kalender finden, in den Lotse schreiben darf – sonst einen eigenen "Lotse"-Kalender anlegen
    private fun beschreibbarerKalender(context: Context): Long {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_TYPE),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null
        )?.use { c ->
            var ersatz = -1L
            while (c.moveToNext()) {
                val typ = c.getString(1) ?: ""
                // Bevorzugt einen Google-Kalender oder einen lokalen Kalender
                if (typ == "com.google" || typ == CalendarContract.ACCOUNT_TYPE_LOCAL) return c.getLong(0)
                if (ersatz < 0) ersatz = c.getLong(0)
            }
            if (ersatz >= 0) return ersatz
        }

        val adresse = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "Lotse")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val werte = android.content.ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "Lotse")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "Lotse")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Lotse")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF1F4E8C.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "Lotse")
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val neu = context.contentResolver.insert(adresse, werte) ?: throw IllegalStateException("Kalender nicht angelegt")
        return ContentUris.parseId(neu)
    }

    // Testtermin in einer Stunde anlegen, 30 Minuten lang
    fun testterminAnlegen(context: Context): String {
        val kalenderId = beschreibbarerKalender(context)
        val beginn = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val werte = android.content.ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, kalenderId)
            put(CalendarContract.Events.TITLE, "Lotse-Test")
            put(CalendarContract.Events.DTSTART, beginn)
            put(CalendarContract.Events.DTEND, beginn + 30 * 60 * 1000)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, werte)
            ?: throw IllegalStateException("Termin nicht angelegt")
        return "Ich habe einen Testtermin eingetragen: ${tagGesprochen(beginn)} um ${uhrzeit(beginn)}, Lotse-Test."
    }

    // Für Tests: Was sieht Lotse im Kalender-Speicher des Handys?
    fun pruefung(context: Context): String {
        val teile = mutableListOf<String>()
        try {
            val kalender = mutableListOf<String>()
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.VISIBLE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val sichtbar = if (c.getInt(2) == 1) "sichtbar" else "versteckt"
                    kalender.add("${c.getString(0)} (${c.getString(1)?.substringAfterLast('.') ?: "?"}, $sichtbar)")
                }
            }
            teile.add("Ich sehe ${kalender.size} Kalender: ${kalender.joinToString("; ")}.")
        } catch (e: Exception) {
            teile.add("Kalenderliste nicht lesbar: ${e.javaClass.simpleName}.")
        }

        val heute = tagesbeginn()
        val anfang = heute.timeInMillis
        val ende = tagesbeginn(1).timeInMillis
        try {
            var anzahl = 0
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.TITLE),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?",
                arrayOf(anfang.toString(), ende.toString()), null
            )?.use { c -> anzahl = c.count }
            teile.add("In der Termin-Tabelle stehen heute $anzahl Termine.")
        } catch (e: Exception) {
            teile.add("Termin-Tabelle nicht lesbar: ${e.javaClass.simpleName}.")
        }
        try {
            var anzahl = 0
            val adresse = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, anfang); ContentUris.appendId(it, ende)
            }.build()
            context.contentResolver.query(adresse, arrayOf(CalendarContract.Instances.TITLE), null, null, null)
                ?.use { c -> anzahl = c.count }
            teile.add("In der Terminliste stehen heute $anzahl Termine.")
        } catch (e: Exception) {
            teile.add("Terminliste nicht lesbar: ${e.javaClass.simpleName}.")
        }
        // Die zuletzt angelegten Termine – egal für welchen Tag
        try {
            var gesamt = 0
            val zuletzt = mutableListOf<String>()
            val format = SimpleDateFormat("EEEE d.M.yyyy HH:mm", deutsch)
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Events.DELETED
                ),
                null, null,
                CalendarContract.Events._ID + " DESC"
            )?.use { c ->
                gesamt = c.count
                while (c.moveToNext() && zuletzt.size < 3) {
                    val geloescht = if (c.getInt(3) == 1) ", gelöscht" else ""
                    zuletzt.add("„${c.getString(0)}“ am ${format.format(c.getLong(1))} im Kalender ${c.getString(2)}$geloescht")
                }
            }
            teile.add("Insgesamt sind $gesamt Termine gespeichert.")
            if (zuletzt.isNotEmpty()) teile.add("Zuletzt angelegt: ${zuletzt.joinToString(". ")}.")
        } catch (e: Exception) {
            teile.add("Letzte Termine nicht lesbar: ${e.javaClass.simpleName}.")
        }
        return teile.joinToString(" ")
    }

    private fun ganztagsBeginnLokal(utc: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
        return Calendar.getInstance().apply {
            clear()
            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    // Nächster Termin, dessen Titel oder Ort zum Suchwort passt (in den nächsten 60 Tagen)
    fun suche(context: Context, suchwort: String): Termin? {
        val jetzt = System.currentTimeMillis()
        val woerter = Befehle.normalisiere(suchwort).split(" ").filter { it.length >= 3 }
        return termine(context, tagesbeginn(), 60)
            .filter { it.ganztags || it.beginn >= jetzt - 30 * 60 * 1000 }
            .firstOrNull { termin ->
                if (woerter.isEmpty()) return@firstOrNull true
                val inhalt = Befehle.normalisiere("${termin.titel} ${termin.ort ?: ""}").split(" ")
                woerter.any { w -> inhalt.any { Kontakte.aehnlich(it, w) } }
            }
    }

    // ---------- Vorlesbare Sätze ----------

    fun uhrzeit(zeitpunkt: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = zeitpunkt }
        val h = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE)
        return if (m == 0) "$h Uhr" else "$h Uhr $m"
    }

    fun tagGesprochen(zeitpunkt: Long): String {
        val tageEntfernt = ((tagesbeginnVon(zeitpunkt) - tagesbeginn().timeInMillis) / (24L * 60 * 60 * 1000)).toInt()
        return when (tageEntfernt) {
            0 -> "heute"
            1 -> "morgen"
            2 -> "übermorgen"
            else -> "am " + SimpleDateFormat("EEEE, 'dem' d. MMMM", deutsch).format(zeitpunkt)
        }
    }

    private fun tagesbeginnVon(zeitpunkt: Long): Long = Calendar.getInstance().apply {
        timeInMillis = zeitpunkt
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun einzelterminText(t: Termin): String =
        if (t.ganztags) "den ganzen Tag: ${t.titel}" else "um ${uhrzeit(t.beginn)}: ${t.titel}"

    private val zahlwoerter = listOf("keine", "einen", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn")

    // z. B. "Heute hast du zwei Termine. Um 10 Uhr: Arzt. Und um 15 Uhr: Anna kommt."
    fun tagesUebersicht(context: Context, versatzTage: Int, bezeichnung: String): String {
        val liste = termine(context, tagesbeginn(versatzTage), 1)
        val anfang = bezeichnung.replaceFirstChar { it.uppercase(deutsch) }
        if (liste.isEmpty()) return "$anfang hast du keine Termine."
        val anzahl = zahlwoerter.getOrElse(liste.size) { liste.size.toString() }
        val wort = if (liste.size == 1) "Termin" else "Termine"
        val teile = liste.mapIndexed { i, t ->
            val satz = einzelterminText(t)
            if (i == liste.lastIndex && liste.size > 1) "Und $satz" else satz.replaceFirstChar { it.uppercase(deutsch) }
        }
        return "$anfang hast du $anzahl $wort. " + teile.joinToString(". ") + "."
    }

    // Übersicht über mehrere Tage, z. B. "die nächsten sieben Tage"
    fun mehrereTage(context: Context, tage: Int): String {
        val liste = termine(context, tagesbeginn(), tage)
            .filter { it.ganztags || it.beginn >= System.currentTimeMillis() - 30 * 60 * 1000 }
        if (liste.isEmpty()) return "In den nächsten $tage Tagen hast du keine Termine."
        val teile = liste.take(6).map { t ->
            val tagText = tagGesprochen(t.beginn).replaceFirstChar { it.uppercase(deutsch) }
            if (t.ganztags) "$tagText den ganzen Tag: ${t.titel}" else "$tagText um ${uhrzeit(t.beginn)}: ${t.titel}"
        }
        val rest = if (liste.size > 6) " Und noch ${liste.size - 6} weitere." else ""
        return teile.joinToString(". ") + "." + rest
    }

    fun gefundenText(t: Termin): String {
        val tagText = tagGesprochen(t.beginn).replaceFirstChar { it.uppercase(deutsch) }
        return if (t.ganztags) "$tagText den ganzen Tag: ${t.titel}."
        else "$tagText um ${uhrzeit(t.beginn)}: ${t.titel}."
    }

    // Kurzer Text für den Ruhebildschirm: nächster Termin heute
    fun naechsterHeute(context: Context): String {
        val jetzt = System.currentTimeMillis()
        val heute = termine(context, tagesbeginn(), 1)
        val naechster = heute.firstOrNull { !it.ganztags && it.beginn >= jetzt - 15 * 60 * 1000 }
            ?: heute.firstOrNull { it.ganztags }
        return when {
            naechster == null && heute.isEmpty() -> "Heute keine Termine"
            naechster == null -> "Heute keine Termine mehr"
            naechster.ganztags -> "Heute: ${naechster.titel}"
            else -> "${uhrzeit(naechster.beginn)} · ${naechster.titel}"
        }
    }
}
