package com.example.lotse

// Natürliche KI-Stimme (Gemini über den Lotse-Server).
// Spielt rohes PCM-Audio ab; bei Problemen springt die Handy-Stimme ein.

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

object KiStimme {

    val stimmen = listOf("Sulafat", "Achird", "Kore", "Aoede", "Charon", "Puck")

    private fun speicher(context: Context) = context.getSharedPreferences("lotse", Context.MODE_PRIVATE)

    fun aktiv(context: Context): Boolean = speicher(context).getBoolean("kistimme", true)
    fun setzeAktiv(context: Context, an: Boolean) { speicher(context).edit().putBoolean("kistimme", an).apply() }

    fun name(context: Context): String = speicher(context).getString("kistimmeName", stimmen.first()) ?: stimmen.first()

    // Nächste Stimme wählen; gibt (Nummer, Anzahl) zurück
    fun naechste(context: Context): Pair<Int, Int> {
        val neu = (stimmen.indexOf(name(context)) + 1) % stimmen.size
        speicher(context).edit().putString("kistimmeName", stimmen[neu]).apply()
        return (neu + 1) to stimmen.size
    }

    @Volatile private var spur: AudioTrack? = null

    // Spielt PCM 16 Bit mono ab und kehrt erst zurück, wenn fertig oder gestoppt. Gibt true bei Erfolg.
    @Suppress("DEPRECATION")
    fun abspielen(pcm: ByteArray, rate: Int, tempo: Float): Boolean {
        // Tempo über die Abtastrate leicht anpassen (0,85 = etwas langsamer)
        val wiedergabeRate = (rate * (tempo / 0.85f).coerceIn(0.8f, 1.25f)).toInt()
        val puffer = maxOf(
            AudioTrack.getMinBufferSize(wiedergabeRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
            8192
        )
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC, wiedergabeRate, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, puffer * 4, AudioTrack.MODE_STREAM
        )
        spur = track
        try {
            track.play()
            var pos = 0
            while (pos < pcm.size) {
                if (spur !== track) return false // wurde gestoppt
                val n = track.write(pcm, pos, minOf(puffer, pcm.size - pos))
                if (n <= 0) return false
                pos += n
            }
            // Warten, bis alles abgespielt ist
            val frames = pcm.size / 2
            while (spur === track && track.playbackHeadPosition < frames) Thread.sleep(50)
            return spur === track
        } catch (_: Exception) {
            return false
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
            if (spur === track) spur = null
        }
    }

    fun stoppen() {
        val t = spur
        spur = null
        try { t?.pause(); t?.flush() } catch (_: Exception) {}
    }
}
