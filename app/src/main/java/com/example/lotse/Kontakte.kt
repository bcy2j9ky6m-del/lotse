package com.example.lotse

// Adressbuch und Anrufliste lesen.

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract.CommonDataKinds.Phone
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Kontakt(val name: String, val nummer: String) {
    val anfangsbuchstabe: String get() = name.trim().take(1).uppercase(Locale.GERMANY)
}

object Kontakte {

    fun suche(context: Context, gesucht: String): List<Kontakt> {
        val g = Befehle.normalisiere(gesucht)
        if (g.isBlank()) return emptyList()

        val alle = linkedMapOf<String, Kontakt>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            null, null,
            Phone.DISPLAY_NAME + " ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val nummer = c.getString(1) ?: continue
                if (name !in alle) alle[name] = Kontakt(name, nummer)
            }
        }

        // Erst genaue Treffer, sonst ähnliche
        val genau = alle.values.filter { kontakt ->
            val n = Befehle.normalisiere(kontakt.name)
            n == g || n.split(" ").contains(g)
        }
        if (genau.isNotEmpty()) return genau
        return alle.values.filter { passt(Befehle.normalisiere(it.name), g) }
    }

    // Passt der gesprochene Name zum Kontaktnamen? Kleine Erkennungsfehler werden verziehen.
    fun passt(name: String, gesucht: String): Boolean {
        if (name.isBlank() || gesucht.isBlank()) return false
        if (name == gesucht || name.contains(gesucht)) return true
        val namensWoerter = name.split(" ")
        return gesucht.split(" ").all { wort -> namensWoerter.any { aehnlich(it, wort) } }
    }

    private fun aehnlich(a: String, b: String): Boolean {
        if (a == b) return true
        if (b.length >= 3 && a.startsWith(b)) return true
        if (a.length >= 4 && b.length >= 4 && abstand(a, b) <= 1) return true
        return false
    }

    // Levenshtein-Abstand: wie viele Buchstaben unterscheiden sich?
    private fun abstand(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val kosten = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + kosten)
        }
        return d[a.length][b.length]
    }

    // Die letzten drei eingehenden oder verpassten Anrufe als vorlesbarer Text
    fun letzteAnrufe(context: Context): String {
        val eintraege = mutableListOf<String>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
            null, null,
            CallLog.Calls.DATE + " DESC"
        )?.use { c ->
            while (c.moveToNext() && eintraege.size < 3) {
                val typ = c.getInt(1)
                if (typ != CallLog.Calls.INCOMING_TYPE && typ != CallLog.Calls.MISSED_TYPE) continue
                val wer = c.getString(0)?.takeIf { it.isNotBlank() } ?: "eine unbekannte Nummer"
                val wann = wannGesprochen(c.getLong(2))
                val verpasst = if (typ == CallLog.Calls.MISSED_TYPE) ", das hast du verpasst" else ""
                eintraege.add("$wer, $wann$verpasst")
            }
        }
        if (eintraege.isEmpty()) return "In letzter Zeit hat niemand angerufen."
        val text = StringBuilder("Zuletzt hat angerufen: ${eintraege[0]}.")
        if (eintraege.size > 1) text.append(" Davor: ${eintraege.drop(1).joinToString(". Und ")}.")
        return text.toString()
    }

    private fun wannGesprochen(zeitpunkt: Long): String {
        val dann = Calendar.getInstance().apply { timeInMillis = zeitpunkt }
        val heute = Calendar.getInstance()
        val gestern = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val stunde = dann.get(Calendar.HOUR_OF_DAY)
        val minute = dann.get(Calendar.MINUTE)
        val uhrzeit = if (minute == 0) "$stunde Uhr" else "$stunde Uhr $minute"
        fun gleicherTag(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return when {
            gleicherTag(dann, heute) -> "heute um $uhrzeit"
            gleicherTag(dann, gestern) -> "gestern um $uhrzeit"
            else -> "am " + SimpleDateFormat("EEEE, 'dem' d. MMMM", Locale.GERMANY).format(Date(zeitpunkt))
        }
    }
}
