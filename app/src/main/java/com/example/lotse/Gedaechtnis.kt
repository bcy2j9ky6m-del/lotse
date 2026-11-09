package com.example.lotse

// Gedächtnis von Lotse:
// - Kurzzeit: die letzten Fragen und Antworten (vergisst nach 30 Minuten Pause)
// - Langzeit: Dinge, die man Lotse ausdrücklich sagt ("Merk dir, dass …") – nur auf dem Handy gespeichert

import android.content.Context
import org.json.JSONArray
import java.util.Locale

data class Wechsel(val frage: String, val antwort: String)

object Gedaechtnis {

    private const val PAUSE_MS = 30 * 60 * 1000L
    private const val MAX_WECHSEL = 6
    private const val MAX_FAKTEN = 30

    private val verlauf = ArrayDeque<Wechsel>()
    private var letzteAktivitaet = 0L

    // ---------- Kurzzeit ----------

    fun verlauf(): List<Wechsel> {
        if (System.currentTimeMillis() - letzteAktivitaet > PAUSE_MS) verlauf.clear()
        return verlauf.toList()
    }

    fun merkeWechsel(frage: String, antwort: String) {
        if (System.currentTimeMillis() - letzteAktivitaet > PAUSE_MS) verlauf.clear()
        verlauf.addLast(Wechsel(frage.take(500), antwort.take(500)))
        while (verlauf.size > MAX_WECHSEL) verlauf.removeFirst()
        letzteAktivitaet = System.currentTimeMillis()
    }

    // ---------- Langzeit ----------

    private fun speicher(context: Context) = context.getSharedPreferences("lotse", Context.MODE_PRIVATE)

    fun fakten(context: Context): List<String> {
        val roh = speicher(context).getString("fakten", "[]") ?: "[]"
        return try {
            val liste = JSONArray(roh)
            (0 until liste.length()).map { liste.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun speichere(context: Context, liste: List<String>) {
        speicher(context).edit().putString("fakten", JSONArray(liste).toString()).apply()
    }

    fun merke(context: Context, fakt: String): String {
        val sauber = fakt.trim().trimEnd('.').replaceFirstChar { it.uppercase(Locale.GERMANY) }
        val liste = fakten(context).toMutableList()
        if (liste.none { it.equals(sauber, ignoreCase = true) }) liste.add(sauber)
        while (liste.size > MAX_FAKTEN) liste.removeAt(0)
        speichere(context, liste)
        return sauber
    }

    // Löscht alle gemerkten Dinge, in denen die wichtigen Wörter vorkommen. Gibt die Anzahl zurück.
    fun vergiss(context: Context, was: String): Int {
        val woerter = Befehle.normalisiere(was).split(" ").filter { it.length >= 3 }
        if (woerter.isEmpty()) return 0
        val liste = fakten(context)
        val bleibt = liste.filterNot { fakt ->
            val f = fakt.lowercase(Locale.GERMANY)
            woerter.all { f.contains(it) }
        }
        speichere(context, bleibt)
        return liste.size - bleibt.size
    }

    fun vergissAlles(context: Context) {
        speichere(context, emptyList())
        verlauf.clear()
    }
}
