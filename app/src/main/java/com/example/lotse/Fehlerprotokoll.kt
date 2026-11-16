package com.example.lotse

// Merkt sich den letzten Absturz, damit er beim nächsten Start auf dem Bildschirm steht.

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fehlerprotokoll {

    private var eingerichtet = false

    fun einrichten(context: Context) {
        if (eingerichtet) return
        eingerichtet = true
        val app = context.applicationContext
        val vorher = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, fehler ->
            try {
                val stelle = fehler.stackTrace.firstOrNull { it.className.startsWith("com.example.lotse") }
                val ort = stelle?.let { "${it.fileName}:${it.lineNumber}" } ?: "?"
                val zeit = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date())
                val kurz = "$zeit ${fehler.javaClass.simpleName} ($ort)"
                app.getSharedPreferences("lotse", Context.MODE_PRIVATE).edit()
                    .putString("fehlerKurz", kurz)
                    .putString("fehlerVoll", android.util.Log.getStackTraceString(fehler).take(4000))
                    .commit()
            } catch (_: Throwable) {}
            vorher?.uncaughtException(thread, fehler)
        }
    }

    fun letzter(context: Context): String? =
        context.getSharedPreferences("lotse", Context.MODE_PRIVATE).getString("fehlerKurz", null)
}
