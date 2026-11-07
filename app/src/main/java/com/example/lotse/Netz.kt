package com.example.lotse

// Verbindung zum Lotse-Server.
// Android 6 kennt einige neuere Sicherheitszertifikate nicht. Deshalb bringt Lotse
// die wichtigsten Stammzertifikate selbst mit und vertraut ihnen zusätzlich zu denen des Systems.

import android.content.Context
import org.json.JSONObject
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

    // Schickt den Satz an den Server und liefert die vorlesbare Antwort.
    // Wirft eine Exception, wenn keine Verbindung möglich ist.
    fun frage(satz: String, jetzt: String): String {
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

            val inhalt = JSONObject().put("text", satz).put("jetzt", jetzt).toString()
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
