package com.example.lotse

// Feste Befehlserkennung – funktioniert ohne Internet und ohne Sprachmodell.

import java.util.Locale

sealed class Befehl {
    data class Anrufen(val name: String) : Befehl()
    object WerHatAngerufen : Befehl()
    object Keiner : Befehl()
}

object Befehle {

    private val fuellwoerter = setOf(
        "den", "die", "der", "dem", "das", "mein", "meine", "meinen", "meiner", "meinem",
        "bitte", "mal", "doch", "mir", "jetzt", "gleich", "einmal", "kurz",
        "frau", "herr", "herrn", "ich", "will", "möchte", "würde", "gern", "gerne",
        "kannst", "du", "könntest", "mit", "bei", "an"
    )

    // Kleinbuchstaben, Satzzeichen weg, Füllwörter am Anfang und Ende entfernen
    fun normalisiere(text: String): String {
        val woerter = text.lowercase(Locale.GERMANY)
            .replace(Regex("[^a-zäöüß0-9 ]"), " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .toMutableList()
        while (woerter.isNotEmpty() && woerter.first() in fuellwoerter) woerter.removeAt(0)
        while (woerter.isNotEmpty() && woerter.last() in fuellwoerter) woerter.removeAt(woerter.size - 1)
        return woerter.joinToString(" ")
    }

    private fun einfach(text: String): String =
        text.lowercase(Locale.GERMANY).replace(Regex("[^a-zäöüß0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val anrufMuster = listOf(
        Regex("""\b(?:ruf|rufe|ruft)\s+(.+?)\s+an\b"""),
        Regex("""\bmit\s+(.+?)\s+(?:sprechen|telefonieren|reden)\b"""),
        Regex("""\b(?:telefonier\w*|verbinde\s+mich)\s+mit\s+(.+)$"""),
        Regex("""\banrufen\s+bei\s+(.+)$"""),
        Regex("""^(.+?)\s+anrufen\b""")
    )

    fun erkenne(satz: String): Befehl {
        val t = einfach(satz)

        if ((t.contains("wer hat") && t.contains("angerufen")) ||
            t.contains("verpasste anrufe") || t.contains("letzte anrufe") || t.contains("letzten anrufe")
        ) return Befehl.WerHatAngerufen

        for (muster in anrufMuster) {
            val treffer = muster.find(t) ?: continue
            val name = normalisiere(treffer.groupValues[1])
            if (name.isNotBlank()) return Befehl.Anrufen(name)
        }
        return Befehl.Keiner
    }

    fun istNein(antwort: String): Boolean {
        val w = einfach(antwort).split(" ")
        return w.any { it in setOf("nein", "nee", "nicht", "stopp", "stop", "abbrechen", "falsch", "halt", "lieber") }
    }

    fun istJa(antwort: String): Boolean {
        if (istNein(antwort)) return false
        val w = einfach(antwort).split(" ")
        return w.any { it in setOf("ja", "jawohl", "jo", "okay", "ok", "genau", "richtig", "gerne", "gern", "mach", "los", "bitte", "klar") }
    }

    // Auswahl aus mehreren Kontakten: per Name oder "die erste / zweite / dritte"
    fun waehle(antwort: String, kandidaten: List<Kontakt>): Kontakt? {
        val t = einfach(antwort)
        val woerter = t.split(" ")
        val position = when {
            woerter.any { it.startsWith("erst") || it == "eins" } -> 0
            woerter.any { it.startsWith("zweit") || it == "zwei" } -> 1
            woerter.any { it.startsWith("dritt") || it == "drei" } -> 2
            else -> -1
        }
        if (position in kandidaten.indices) return kandidaten[position]

        val gesucht = normalisiere(antwort)
        val passend = kandidaten.filter { Kontakte.passt(normalisiere(it.name), gesucht) }
        return passend.singleOrNull()
    }
}
