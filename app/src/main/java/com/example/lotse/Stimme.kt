package com.example.lotse

// Stimme von Lotse: beste deutsche Stimme auswählen, wechseln und das Tempo einstellen.

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

object Stimme {

    private fun speicher(context: Context) = context.getSharedPreferences("lotse", Context.MODE_PRIVATE)

    // Alle deutschen Stimmen, die ohne Internet funktionieren – beste Qualität zuerst
    fun deutscheStimmen(tts: TextToSpeech): List<Voice> =
        try {
            (tts.voices ?: emptySet())
                .filter { it.locale.language == "de" }
                .filter { !it.isNetworkConnectionRequired }
                .filter { !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
        } catch (_: Exception) {
            emptyList()
        }

    // Gespeicherte Stimme und Tempo anwenden (sonst die beste verfügbare Stimme)
    fun einstellen(context: Context, tts: TextToSpeech) {
        val stimmen = deutscheStimmen(tts)
        val gespeichert = speicher(context).getString("stimme", null)
        val gewaehlt = stimmen.firstOrNull { it.name == gespeichert } ?: stimmen.firstOrNull()
        if (gewaehlt != null) {
            try { tts.voice = gewaehlt } catch (_: Exception) { tts.language = Locale.GERMANY }
        }
        tts.setSpeechRate(tempo(context))
    }

    // Zur nächsten deutschen Stimme wechseln. Gibt (Nummer, Anzahl) zurück.
    fun naechste(context: Context, tts: TextToSpeech): Pair<Int, Int> {
        val stimmen = deutscheStimmen(tts)
        if (stimmen.isEmpty()) return 0 to 0
        val aktuell = stimmen.indexOfFirst { it.name == tts.voice?.name }
        val neu = (aktuell + 1) % stimmen.size
        tts.voice = stimmen[neu]
        speicher(context).edit().putString("stimme", stimmen[neu].name).apply()
        return (neu + 1) to stimmen.size
    }

    fun tempo(context: Context): Float = speicher(context).getFloat("tempo", 0.85f)

    // Tempo ändern (zwischen 0,6 und 1,2)
    fun tempoAendern(context: Context, tts: TextToSpeech, schritt: Float?): Float {
        val neu = if (schritt == null) 0.85f else (tempo(context) + schritt).coerceIn(0.6f, 1.2f)
        speicher(context).edit().putFloat("tempo", neu).apply()
        tts.setSpeechRate(neu)
        return neu
    }
}
