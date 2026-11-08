package com.example.lotse

// Lotse aktualisiert sich auf Zuruf selbst: neueste Version bei GitHub suchen,
// herunterladen und die Installation öffnen. Die Installation bestätigt immer ein Mensch.

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
            val d = dateien.getJSONObject(i)
            val name = d.getString("name")
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

    // Startet den Download über den Download-Manager von Android. Gibt die Download-Nummer zurück.
    fun ladeHerunter(context: Context, neu: NeueVersion): Long {
        val ordner = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        File(ordner, neu.datei).delete() // alte Datei gleichen Namens entfernen
        val auftrag = DownloadManager.Request(Uri.parse(neu.adresse))
            .setTitle("Lotse ${neu.version}")
            .setDescription("Neue Version wird geladen")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, neu.datei)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(auftrag)
    }

    // Zustand eines Downloads: "läuft", "fertig" oder "fehler:<Grund>"
    fun stand(context: Context, id: Long): String {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return "fehler:verschwunden"
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> "fertig"
                DownloadManager.STATUS_FAILED -> "fehler:" + c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                else -> "läuft"
            }
        }
        return "fehler:unbekannt"
    }

    // Öffnet die Installation – Android fragt dann "Installieren?"
    fun installiere(context: Context, id: Long) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val datei = manager.getUriForDownloadedFile(id) ?: throw IllegalStateException("Datei nicht gefunden")
        val installation = Intent(Intent.ACTION_VIEW)
            .setDataAndType(datei, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(installation)
    }
}
