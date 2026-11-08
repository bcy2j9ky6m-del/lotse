package com.example.lotse

// Feste Befehlserkennung – funktioniert ohne Internet und ohne Sprachmodell.

import java.util.Locale

sealed class Befehl {
    data class Anrufen(val name: String) : Befehl()
    object WerHatAngerufen : Befehl()
    data class TermineAmTag(val versatzTage: Int, val bezeichnung: String) : Befehl()
    data class TermineMehrereTage(val tage: Int) : Befehl()
    data class WannIst(val suchwort: String) : Befehl()
    object TerminEintragen : Befehl()
    object Morgengruss : Befehl()
    object KalenderPruefen : Befehl()
    object Update : Befehl()
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

        val w = t.split(" ")
        if (w.any { it in setOf("update", "updates", "apdate", "abdate", "updaten", "upgrade") } ||
            t.contains("aktualisier") || t.contains("neue version") || t.contains("neueste version")
        ) return Befehl.Update

        kalenderBefehl(t)?.let { return it }

        for (muster in anrufMuster) {
            val treffer = muster.find(t) ?: continue
            val name = normalisiere(treffer.groupValues[1])
            if (name.isNotBlank()) return Befehl.Anrufen(name)
        }
        return Befehl.Keiner
    }

    private val wochentage = listOf("sonntag", "montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag")

    private val wannMuster = Regex(
        """^wann\s+(?:kommt|kommen|ist|sind|habe ich|hab ich|muss ich|geht|gehe ich|fängt|beginnt|sehe ich|besucht mich|treffe ich|war)\s+(.+)$"""
    )

    private fun kalenderBefehl(t: String): Befehl? {
        val woerter = t.split(" ")

        if (t.startsWith("guten morgen")) return Befehl.Morgengruss
        if ((t.contains("kalender") && (t.contains("prüf") || t.contains("test") || t.contains("check"))) ||
            t.contains("kalendertest")
        ) return Befehl.KalenderPruefen

        if ((t.contains("trag") && t.contains("ein")) || t.contains("termin eintragen") ||
            t.contains("termin machen") || t.contains("neuen termin")
        ) return Befehl.TerminEintragen

        // Fragen nach dem Datum beantwortet lokaleAntwort, nicht der Kalender
        if (t.contains("welcher tag") || t.contains("für ein tag") || t.contains("datum") || t.contains("wievielte")) return null

        wannMuster.find(t)?.let { treffer ->
            val suchwort = treffer.groupValues[1]
                .replace(Regex("""\b(?:an|los|vorbei|zu mir|zu besuch|wieder|denn|eigentlich)\b"""), " ")
            val wort = normalisiere(suchwort)
            return Befehl.WannIst(if (wort.contains("termin")) "" else wort)
        }
        if (t.contains("nächste termin") || t.contains("nächster termin") || t.contains("nächsten termin")) {
            return Befehl.WannIst("")
        }

        val kalenderFrage = listOf("vor", "termin", "termine", "was ist", "was steht", "was gibt", "los", "geplant", "programm", "plan")
            .any { if (it.contains(" ")) t.contains(it) else woerter.contains(it) || woerter.any { w -> w.startsWith(it) && it.length > 4 } }
        if (!kalenderFrage) return null

        if (t.contains("woche") || t.contains("nächsten tage")) return Befehl.TermineMehrereTage(7)
        if (woerter.contains("übermorgen")) return Befehl.TermineAmTag(2, "übermorgen")
        if (woerter.contains("morgen")) return Befehl.TermineAmTag(1, "morgen")
        if (woerter.contains("heute")) return Befehl.TermineAmTag(0, "heute")

        val tag = wochentage.indexOfFirst { woerter.contains(it) }
        if (tag >= 0) {
            val heute = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0 = Sonntag
            val versatz = (tag - heute + 7) % 7
            val name = wochentage[tag].replaceFirstChar { it.uppercase(Locale.GERMANY) }
            return Befehl.TermineAmTag(versatz, if (versatz == 0) "heute" else "am $name")
        }
        // "Was habe ich vor?" ohne Tag → heute
        if (t.contains("habe ich") || t.contains("hab ich") || woerter.contains("termine")) return Befehl.TermineAmTag(0, "heute")
        return null
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
