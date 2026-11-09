package com.example.lotse

// Verbindung zum Lotse-Server.
// Android 6 kennt einige neuere Sicherheitszertifikate nicht. Deshalb bringt Lotse
// die wichtigsten Stammzertifikate selbst mit und vertraut ihnen zusätzlich zu denen des Systems.

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

class Netz(private val context: Context) {

    val serverEingerichtet: Boolean
        get() = BuildConfig.LOTSE_SERVER.startsWith("https://") && BuildConfig.LOTSE_TOKEN.isNotEmpty()

    private val sslFabrik: SSLSocketFactory by lazy { baueSslFabrik() }

    private fun baueSslFabrik(): SSLSocketFactory {
        val speicher = KeyStore.getInstance(KeyStore.getDefaultType())
        speicher.load(null, null)

        // Zertifikate des Systems übernehmen
        try {
            val system = KeyStore.getInstance("AndroidCAStore")
            system.load(null, null)
            for (alias in system.aliases()) {
                system.getCertificate(alias)?.let { speicher.setCertificateEntry("system_$alias", it) }
            }
        } catch (_: Exception) {
            // Falls das System seine Zertifikate nicht herausgibt, nur die mitgebrachten nutzen
        }

        // Mitgebrachte Stammzertifikate ergänzen
        val fabrik = CertificateFactory.getInstance("X.509")
        val mitgebracht = listOf(
            R.raw.isrg_root_x1, R.raw.isrg_root_x2,
            R.raw.gts_root_r1, R.raw.gts_root_r3, R.raw.gts_root_r4,
            R.raw.digicert_global_root_g2
        )
        mitgebracht.forEachIndexed { i, id ->
            context.resources.openRawResource(id).use { strom ->
                speicher.setCertificateEntry("lotse_$i", fabrik.generateCertificate(strom))
            }
        }

        val vertrauen = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        vertrauen.init(speicher)
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(null, vertrauen.trustManagers, null)
        return ssl.socketFactory
    }

    // Einfache Abfrage einer Internetadresse (z. B. GitHub nach neuen Versionen fragen)
    fun hole(adresse: String): String {
        val verbindung = URL(adresse).openConnection() as HttpURLConnection
        if (verbindung is HttpsURLConnection) verbindung.sslSocketFactory = sslFabrik
        try {
            verbindung.connectTimeout = 10_000
            verbindung.readTimeout = 20_000
            verbindung.setRequestProperty("User-Agent", "Lotse-App")
            verbindung.setRequestProperty("Accept", "application/vnd.github+json")
            val code = verbindung.responseCode
            if (code !in 200..299) throw IllegalStateException("Antwort $code")
            return verbindung.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            verbindung.disconnect()
        }
    }

    // Datei herunterladen und speichern; meldet den Fortschritt in Prozent
    fun ladeDatei(adresse: String, ziel: File, fortschritt: (Int) -> Unit) {
        val verbindung = URL(adresse).openConnection() as HttpURLConnection
        if (verbindung is HttpsURLConnection) verbindung.sslSocketFactory = sslFabrik
        try {
            verbindung.connectTimeout = 15_000
            verbindung.readTimeout = 30_000
            verbindung.instanceFollowRedirects = true
            verbindung.setRequestProperty("User-Agent", "Lotse-App")
            val code = verbindung.responseCode
            if (code !in 200..299) throw IOException("Download-Antwort $code")
            val gesamt = verbindung.contentLength.toLong()
            var geladen = 0L
            var letzterStand = -1
            verbindung.inputStream.use { ein ->
                FileOutputStream(ziel).use { aus ->
                    val puffer = ByteArray(32 * 1024)
                    while (true) {
                        val n = ein.read(puffer)
                        if (n < 0) break
                        aus.write(puffer, 0, n)
                        geladen += n
                        if (gesamt > 0) {
                            val stand = (geladen * 100 / gesamt).toInt()
                            if (stand != letzterStand) { letzterStand = stand; fortschritt(stand) }
                        }
                    }
                }
            }
            if (gesamt > 0 && geladen != gesamt) throw IOException("Download unvollständig ($geladen von $gesamt)")
        } finally {
            verbindung.disconnect()
        }
    }

    // KI-Stimme: Text an den Server, zurück kommt Audio (PCM 16 Bit mono) und die Abtastrate
    fun sprachAudio(text: String, stimme: String): Pair<ByteArray, Int> {
        val verbindung = URL(BuildConfig.LOTSE_SERVER.trimEnd('/') + "/sprich").openConnection() as HttpURLConnection
        if (verbindung is HttpsURLConnection) verbindung.sslSocketFactory = sslFabrik
        try {
            verbindung.requestMethod = "POST"
            verbindung.connectTimeout = 8_000
            verbindung.readTimeout = 20_000
            verbindung.doOutput = true
            verbindung.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            verbindung.setRequestProperty("X-Lotse-Token", BuildConfig.LOTSE_TOKEN)
            val inhalt = JSONObject().put("text", text).put("stimme", stimme).toString()
            verbindung.outputStream.use { it.write(inhalt.toByteArray(Charsets.UTF_8)) }
            val code = verbindung.responseCode
            if (code !in 200..299) throw IOException("Stimme: Antwort $code")
            val rate = verbindung.getHeaderField("X-Rate")?.toIntOrNull() ?: 24000
            val daten = verbindung.inputStream.use { it.readBytes() }
            if (daten.size < 1000) throw IOException("Stimme: zu wenig Audio")
            return daten to rate
        } finally {
            verbindung.disconnect()
        }
    }

    // Schickt den Satz an den Server und liefert die vorlesbare Antwort.
    // Wirft eine Exception, wenn keine Verbindung möglich ist.
    fun frage(satz: String, jetzt: String, verlauf: List<Wechsel> = emptyList(), fakten: List<String> = emptyList()): String {
        val verbindung = URL(BuildConfig.LOTSE_SERVER.trimEnd('/') + "/frage").openConnection() as HttpURLConnection
        if (verbindung is HttpsURLConnection) {
            verbindung.sslSocketFactory = sslFabrik
        }
        try {
            verbindung.requestMethod = "POST"
            verbindung.connectTimeout = 10_000
            verbindung.readTimeout = 25_000
            verbindung.doOutput = true
            verbindung.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            verbindung.setRequestProperty("X-Lotse-Token", BuildConfig.LOTSE_TOKEN)

            val bisher = org.json.JSONArray()
            verlauf.forEach { bisher.put(JSONObject().put("frage", it.frage).put("antwort", it.antwort)) }
            val inhalt = JSONObject()
                .put("text", satz)
                .put("jetzt", jetzt)
                .put("verlauf", bisher)
                .put("fakten", org.json.JSONArray(fakten))
                .toString()
            verbindung.outputStream.use { it.write(inhalt.toByteArray(Charsets.UTF_8)) }

            val code = verbindung.responseCode
            if (code == 401) return "Mein Server kennt mich nicht. Bafti muss das Passwort prüfen."
            val strom = if (code in 200..299) verbindung.inputStream else verbindung.errorStream
            val text = strom?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (text.isBlank()) return "Darauf weiß ich gerade keine Antwort."
            return JSONObject(text).optString("antwort", "Darauf weiß ich gerade keine Antwort.")
        } finally {
            verbindung.disconnect()
        }
    }
}
