package com.example.lotse

// SMS lesen, vorlesbar machen, auf Betrug prüfen und verschicken.
// Lotse ist dafür NICHT die Standard-SMS-App – die bisherige App speichert weiter alle Nachrichten.

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import java.util.Locale

data class Sms(val nummer: String, val name: String?, val text: String, val datum: Long) {
    val absender: String get() = name ?: "eine unbekannte Nummer"
}

// Verbindung zwischen Empfänger und geöffneter App
object Ereignisse {
    var neueSms: ((Sms) -> Unit)? = null
}

// Wird von Android bei jeder eingehenden SMS aufgerufen
class SmsEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val teile = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        teile.groupBy { it.displayOriginatingAddress ?: "" }.forEach { (nummer, stuecke) ->
            val text = stuecke.joinToString("") { it.displayMessageBody ?: "" }
            val name = Nachrichten.nameZuNummer(context, nummer)
            Ereignisse.neueSms?.invoke(Sms(nummer, name, text, System.currentTimeMillis()))
        }
    }
}

object Nachrichten {

    fun nameZuNummer(context: Context, nummer: String): String? {
        if (nummer.isBlank()) return null
        return try {
            val adresse = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(nummer))
            context.contentResolver.query(adresse, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) {
            null
        }
    }

    // Nachrichten aus dem Posteingang, die nach "seit" angekommen sind (neueste zuerst)
    fun seit(context: Context, seit: Long, hoechstens: Int = 5): List<Sms> {
        val liste = mutableListOf<Sms>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?", arrayOf(seit.toString()),
            Telephony.Sms.DATE + " DESC"
        )?.use { c ->
            while (c.moveToNext() && liste.size < hoechstens) {
                val nummer = c.getString(0) ?: ""
                liste.add(Sms(nummer, nameZuNummer(context, nummer), c.getString(1) ?: "", c.getLong(2)))
            }
        }
        return liste
    }

    fun zaehleSeit(context: Context, seit: Long): Int =
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.DATE} > ?", arrayOf(seit.toString()), null
        )?.use { it.count } ?: 0

    // ---------- Betrugsschutz ----------

    private val verdaechtigeWoerter = listOf(
        "geld", "überweis", "konto", "iban", "pin", "tan", "passwort", "kennwort", "paket", "zoll",
        "gebühr", "bezahl", "zahlung", "gewinn", "gewonnen", "neue nummer", "notfall", "dringend",
        "bank", "karte gesperrt", "bestätigen sie", "klicken sie"
    )

    fun betrugsVerdacht(text: String): Boolean {
        val t = text.lowercase(Locale.GERMANY)
        val link = Regex("""(https?://|www\.|\b[a-z0-9-]+\.(de|com|net|org|info|io|ly|me)\b)""").containsMatchIn(t)
        return link || verdaechtigeWoerter.any { t.contains(it) }
    }

    // Vorlesbarer Satz mit Warnungen, z. B. "Anna schreibt, heute um 10 Uhr: Ich komme um drei."
    fun vorlesbar(sms: Sms): String {
        val wann = Kalender.tagGesprochen(sms.datum) + " um " + Kalender.uhrzeit(sms.datum)
        val warnung = when {
            sms.name == null && betrugsVerdacht(sms.text) ->
                " Vorsicht! Diese Nachricht kommt von einer unbekannten Nummer und sieht nach Betrug aus. " +
                    "Tippe keine Links an, gib keine Daten weiter und überweise kein Geld. Frag lieber Bafti."
            betrugsVerdacht(sms.text) ->
                " Vorsicht, in dieser Nachricht geht es um Geld, Daten oder einen Link. Frag im Zweifel lieber Bafti."
            sms.name == null -> " Diese Nummer kenne ich nicht."
            else -> ""
        }
        return "${sms.absender.replaceFirstChar { it.uppercase(Locale.GERMANY) }} schreibt, $wann: ${sms.text.trim()}.$warnung"
    }

    @Suppress("DEPRECATION")
    fun sende(nummer: String, text: String) {
        val manager = SmsManager.getDefault()
        val teile = manager.divideMessage(text)
        manager.sendMultipartTextMessage(nummer, null, teile, null, null)
    }
}
