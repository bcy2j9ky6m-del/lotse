package com.example.lotse

// Schritt 7: Lotse als Telefon-App – eigener, großer Anrufbildschirm für eingehende und ausgehende Anrufe.

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

private val AHintergrund = Color(0xFFFBF8F2)
private val ASchrift = Color(0xFF1A1A1A)
private val AGrau = Color(0xFF4A4A4A)
private val AGruen = Color(0xFF1E6B3A)
private val ARot = Color(0xFFB3261E)

// Gemeinsamer Zustand des aktuellen Anrufs
object Anrufe {
    var dienst: AnrufDienst? = null
    var anruf: Call? = null

    var zustand by mutableStateOf(Call.STATE_DISCONNECTED)
    var name by mutableStateOf<String?>(null)
    var nummer by mutableStateOf("")
    var eingehend by mutableStateOf(false)
    var beginnGespraech by mutableLongStateOf(0L)
    var lautsprecher by mutableStateOf(false)

    private val rueckmeldung = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (call != anruf) return
            zustand = state
            if (state == Call.STATE_ACTIVE && beginnGespraech == 0L) beginnGespraech = System.currentTimeMillis()
        }
    }

    @Suppress("DEPRECATION")
    fun hinzu(context: Context, call: Call) {
        anruf = call
        call.registerCallback(rueckmeldung)
        nummer = call.details?.handle?.schemeSpecificPart ?: ""
        name = Nachrichten.nameZuNummer(context, nummer)
        zustand = call.state
        eingehend = call.state == Call.STATE_RINGING
        beginnGespraech = 0L
        lautsprecher = false
    }

    fun entfernt(call: Call) {
        call.unregisterCallback(rueckmeldung)
        if (call == anruf) {
            anruf = null
            zustand = Call.STATE_DISCONNECTED
        }
    }

    fun annehmen() { anruf?.answer(VideoProfile.STATE_AUDIO_ONLY) }
    fun ablehnen() { anruf?.reject(false, null) }
    fun auflegen() { anruf?.disconnect() }

    fun lautsprecherUmschalten() {
        val d = dienst ?: return
        d.setAudioRoute(if (lautsprecher) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }
}

// Android meldet jeden Anruf an diesen Dienst, solange Lotse die Telefon-App ist
class AnrufDienst : InCallService() {
    override fun onCreate() {
        super.onCreate()
        Anrufe.dienst = this
    }

    override fun onDestroy() {
        if (Anrufe.dienst == this) Anrufe.dienst = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Anrufe.hinzu(this, call)
        startActivity(Intent(this, AnrufActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Anrufe.entfernt(call)
    }

    @Deprecated("Für Android 6 bewusst so gelöst")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        Anrufe.lautsprecher = audioState?.route == CallAudioState.ROUTE_SPEAKER
    }
}

class AnrufActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var stimme: TextToSpeech? = null
    private var naeherung: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Auch bei gesperrtem Bildschirm anzeigen und den Bildschirm einschalten
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        stimme = TextToSpeech(this, this)

        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            naeherung = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "lotse:anruf")
        }

        setContent {
            val z = Anrufe.zustand
            // Am Ohr: Bildschirm aus, damit die Wange nichts drückt (nicht bei Lautsprecher)
            LaunchedEffect(z, Anrufe.lautsprecher) {
                val wl = naeherung ?: return@LaunchedEffect
                if (z == Call.STATE_ACTIVE && !Anrufe.lautsprecher) { if (!wl.isHeld) wl.acquire(60 * 60 * 1000L) }
                else if (wl.isHeld) wl.release()
            }
            LaunchedEffect(z) {
                if (z == Call.STATE_DISCONNECTED) { delay(1500); finish() }
            }
            AnrufBildschirm(
                zustand = z,
                name = Anrufe.name,
                nummer = Anrufe.nummer,
                eingehend = Anrufe.eingehend,
                beginn = Anrufe.beginnGespraech,
                lautsprecher = Anrufe.lautsprecher,
                onAnnehmen = { stimme?.stop(); Anrufe.annehmen() },
                onAblehnen = { stimme?.stop(); Anrufe.ablehnen() },
                onAuflegen = { Anrufe.auflegen() },
                onLautsprecher = { Anrufe.lautsprecherUmschalten() }
            )
        }
    }

    // Anrufer laut ansagen
    override fun onInit(status: Int) {
        val tts = stimme ?: return
        if (status != TextToSpeech.SUCCESS) return
        tts.setLanguage(Locale.GERMANY)
        tts.setSpeechRate(0.85f)
        if (Anrufe.zustand == Call.STATE_RINGING) {
            val wer = Anrufe.name ?: "Eine unbekannte Nummer"
            tts.speak("$wer ruft an.", TextToSpeech.QUEUE_FLUSH, null, "anrufer")
        }
    }

    override fun onDestroy() {
        naeherung?.let { if (it.isHeld) it.release() }
        stimme?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun AnrufBildschirm(
    zustand: Int,
    name: String?,
    nummer: String,
    eingehend: Boolean,
    beginn: Long,
    lautsprecher: Boolean,
    onAnnehmen: () -> Unit,
    onAblehnen: () -> Unit,
    onAuflegen: () -> Unit,
    onLautsprecher: () -> Unit
) {
    var sekunden by remember { mutableLongStateOf(0L) }
    LaunchedEffect(beginn) {
        while (beginn > 0) {
            sekunden = (System.currentTimeMillis() - beginn) / 1000
            delay(1000)
        }
    }
    val anzeigeName = name ?: "Unbekannte Nummer"
    val kontakt = Kontakt(name ?: "?", nummer)

    Column(
        modifier = Modifier.fillMaxSize().background(AHintergrund).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val oben = when {
            zustand == Call.STATE_RINGING -> "Anruf"
            zustand == Call.STATE_ACTIVE -> "Gespräch läuft · %d:%02d".format(sekunden / 60, sekunden % 60)
            zustand == Call.STATE_DISCONNECTED || zustand == Call.STATE_DISCONNECTING -> "Gespräch beendet"
            zustand == Call.STATE_HOLDING -> "Gespräch gehalten"
            else -> "Es klingelt bei …"
        }
        Text(oben, fontSize = 24.sp, color = AGrau)
        Bildchen(kontakt, 170.dp)
        Text(anzeigeName, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = ASchrift, textAlign = TextAlign.Center)
        if (name == null && nummer.isNotBlank()) Text(nummer, fontSize = 24.sp, color = AGrau)

        if (zustand == Call.STATE_RINGING && name == null) {
            Karte {
                Text(
                    "Vorsicht: Diese Nummer kenne ich nicht. Gib keine Daten weiter und überweise kein Geld.",
                    fontSize = 20.sp, lineHeight = 26.sp, color = ASchrift
                )
            }
        }

        Spacer(Modifier.weight(1f))

        when (zustand) {
            Call.STATE_RINGING -> if (eingehend) {
                Button(
                    onClick = onAnnehmen,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AGruen, contentColor = Color.White)
                ) { Text("ANNEHMEN", fontSize = 40.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onAblehnen,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ARot, contentColor = Color.White)
                ) { Text("Ablehnen", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            }
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {}
            else -> {
                OutlinedButton(
                    onClick = onLautsprecher,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(3.dp, ASchrift),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = ASchrift)
                ) { Text(if (lautsprecher) "Lautsprecher aus" else "Lautsprecher an", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onAuflegen,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ARot, contentColor = Color.White)
                ) { Text("AUFLEGEN", fontSize = 40.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
