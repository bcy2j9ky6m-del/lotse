package com.example.lotse

// Lotse aktualisiert sich auf Zuruf selbst: neueste Version bei GitHub suchen,
// selbst herunterladen und die Installation öffnen. Die Installation bestätigt immer ein Mensch.

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

data class NeueVersion(val version: String, val nummer: Int, val adresse: String, val datei: String)

object Updater {

    private const val PROJEKT = "bcy2j9ky6m-del/lotse"
    private val dateiMuster = Regex("""lotse-(\d+)\.(\d+)\.(\d+)\.apk""")

    // Liefert die neue Version, falls es eine gibt, die neuer ist als die installierte
    fun suche(netz: Netz, installierteNummer: Int): NeueVersion? {
        val antwort = JSONObject(netz.hole("https://api.github.com/repos/$PROJEKT/releases/latest"))
        val dateien = antwort.getJSONArray("assets")
        for (i in 0 until dateien.length()) {
            val name = dateien.getJSONObject(i).getString("name")
            val treffer = dateiMuster.matchEntire(name) ?: continue
            val nummer = treffer.groupValues[3].toInt()
            if (nummer <= installierteNummer) return null
            val version = "${treffer.groupValues[1]}.${treffer.groupValues[2]}.${treffer.groupValues[3]}"
            // Direkt-Link über den Zweig "downloads" – zuverlässiger als der normale Release-Link
            val adresse = "https://raw.githubusercontent.com/$PROJEKT/downloads/$name"
            return NeueVersion(version, nummer, adresse, name)
        }
        return null
    }

    // Lädt die Datei selbst herunter (ohne den Download-Manager von Android) und meldet den Fortschritt
    fun ladeHerunter(context: Context, netz: Netz, neu: NeueVersion, fortschritt: (Int) -> Unit): File {
        val ordner = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("Kein Speicherplatz für den Download")
        ordner.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() } // alte Dateien weg
        val ziel = File(ordner, neu.datei)
        netz.ladeDatei(neu.adresse, ziel, fortschritt)
        return ziel
    }

    // Öffnet die Installation – Android fragt dann "Installieren?"
    fun installiere(context: Context, datei: File) {
        val adresse: Uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(context, context.packageName + ".dateien", datei)
        } else {
            Uri.fromFile(datei) // Android 6 und älter kennen nur Dateipfade
        }
        val installation = Intent(Intent.ACTION_VIEW)
            .setDataAndType(adresse, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(installation)
    }
}
