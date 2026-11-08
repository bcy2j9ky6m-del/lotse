package com.example.lotse

// Schritt 9: Kiosk-Modus. Lotse wird "Geräteinhaber" (einmalig per adb eingerichtet),
// ist dann der Startbildschirm und lässt sich nicht verlassen – außer mit der Pflege-PIN.

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.security.MessageDigest

// Empfänger, den Android für die Geräteverwaltung braucht
class LotseAdmin : DeviceAdminReceiver()

object Kiosk {

    private val erlaubtePakete = listOf(
        "com.android.packageinstaller",        // Updates installieren
        "com.google.android.packageinstaller",
        "com.android.server.telecom",          // Telefonie
        "com.android.phone"
    )

    private fun admin(context: Context) = ComponentName(context, LotseAdmin::class.java)
    private fun dpm(context: Context) = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun istGeraeteinhaber(context: Context): Boolean =
        try { dpm(context).isDeviceOwnerApp(context.packageName) } catch (_: Exception) { false }

    fun istGesperrt(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    // Einstellungen setzen (mehrfaches Aufrufen schadet nicht)
    fun einrichten(context: Context) {
        if (!istGeraeteinhaber(context)) return
        val d = dpm(context)
        val a = admin(context)
        try { d.setLockTaskPackages(a, (listOf(context.packageName) + erlaubtePakete).toTypedArray()) } catch (_: Exception) {}
        try {
            val startbildschirm = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            d.addPersistentPreferredActivity(a, startbildschirm, ComponentName(context, MainActivity::class.java))
        } catch (_: Exception) {}
        try { d.setKeyguardDisabled(a, true) } catch (_: Exception) {}   // kein Wischen zum Entsperren
        try { d.setStatusBarDisabled(a, true) } catch (_: Exception) {}  // keine Benachrichtigungsleiste
    }

    fun sperren(activity: Activity) {
        if (!istGeraeteinhaber(activity) || istGesperrt(activity)) return
        try { activity.startLockTask() } catch (_: Exception) {}
    }

    // Für die Pflege: Lotse vorübergehend verlassen (z. B. für Einstellungen)
    fun entsperren(activity: Activity) {
        try { activity.stopLockTask() } catch (_: Exception) {}
        try { dpm(activity).setStatusBarDisabled(admin(activity), false) } catch (_: Exception) {}
    }

    // Kiosk-Modus komplett entfernen (nur fürs Testgerät gedacht)
    @Suppress("DEPRECATION")
    fun ganzAusschalten(activity: Activity) {
        entsperren(activity)
        val d = dpm(activity)
        val a = admin(activity)
        try { d.clearPackagePersistentPreferredActivities(a, activity.packageName) } catch (_: Exception) {}
        try { d.setKeyguardDisabled(a, false) } catch (_: Exception) {}
        try { d.clearDeviceOwnerApp(activity.packageName) } catch (_: Exception) {}
    }

    // ---------- Pflege-PIN ----------

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(("lotse:" + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun hatPin(context: Context): Boolean =
        context.getSharedPreferences("lotse", Context.MODE_PRIVATE).contains("pin")

    fun pinSetzen(context: Context, pin: String) {
        context.getSharedPreferences("lotse", Context.MODE_PRIVATE).edit().putString("pin", hash(pin)).apply()
    }

    fun pinRichtig(context: Context, pin: String): Boolean =
        context.getSharedPreferences("lotse", Context.MODE_PRIVATE).getString("pin", null) == hash(pin)
}
