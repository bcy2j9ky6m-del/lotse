package com.example.lotse

// Lotse – Schritt 2: Zuhören und Nachsprechen
// Taste drücken → Spracherkennung (Deutsch) → das Handy sagt laut, was es verstanden hat.
// Läuft ab Android 6 (API 23).

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Farben aus dem Entwurf
private val Hintergrund = Color(0xFFFBF8F2)
private val Schrift = Color(0xFF1A1A1A)
private val Grau = Color(0xFF4A4A4A)
private val Blau = Color(0xFF1F4E8C)
private val HellBlau = Color(0xFFDCE6F3)
private val Rand = Color(0xFFE2DCCF)

enum class Zustand { RUHE, HOEREN, DENKEN, ANTWORT }

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    // Was gerade auf dem Bildschirm zu sehen ist
    private var zustand by mutableStateOf(Zustand.RUHE)
    private var erkannterText by mutableStateOf("")
    private var antwortText by mutableStateOf("")
    private var diagnose by mutableStateOf("Prüfe Sprachfunktionen …")
    private var technik by mutableStateOf("") // kleine Fehlerinfo für Tests, wird nicht vorgelesen

    private val netz by lazy { Netz(applicationContext) }

    private var stimme: TextToSpeech? = null
    private var stimmeBereit = false
    private var erkenner: SpeechRecognizer? = null

    private val handler = Handler(Looper.getMainLooper())
    private val zurueckZurRuhe = Runnable { zustand = Zustand.RUHE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bildschirm bleibt an, solange Lotse offen ist
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stimme = TextToSpeech(this, this)

        setContent {
            when (zustand) {
                Zustand.RUHE -> Ruhebildschirm(
                    diagnose = diagnose,
                    onTaste = { tasteGedrueckt() }
                )
                Zustand.HOEREN -> HoerenBildschirm(
                    titel = "Ich höre zu …",
                    kartenTitel = "Du sagst:",
                    text = erkannterText,
                    onAbbrechen = { abbrechen() }
                )
                Zustand.DENKEN -> HoerenBildschirm(
                    titel = "Ich überlege …",
                    kartenTitel = "Du hast gesagt:",
                    text = erkannterText,
                    onAbbrechen = { abbrechen() }
                )
                Zustand.ANTWORT -> AntwortBildschirm(
                    antwort = antwortText,
                    technik = technik,
                    onNochmal = { tasteGedrueckt() },
                    onFertig = { abbrechen() }
                )
            }
        }
    }

    // Sprachausgabe ist bereit (oder nicht)
    override fun onInit(status: Int) {
        val tts = stimme
        if (status == TextToSpeech.SUCCESS && tts != null) {
            val ergebnis = tts.setLanguage(Locale.GERMANY)
            stimmeBereit = ergebnis != TextToSpeech.LANG_MISSING_DATA &&
                ergebnis != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setSpeechRate(0.85f) // etwas langsamer als normal
        }
        aktualisiereDiagnose()
    }

    private fun aktualisiereDiagnose() {
        val hoeren = if (SpeechRecognizer.isRecognitionAvailable(this)) "ja" else "NEIN"
        val sprechen = if (stimmeBereit) "ja" else "NEIN"
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val gehirn = if (netz.serverEingerichtet) "ja" else "NEIN"
        diagnose = "Version $version · Zuhören: $hoeren · Stimme: $sprechen · Server: $gehirn"
    }

    private fun tasteGedrueckt() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            zuhoerenStarten()
        }
    }

    @Deprecated("Für Android 6 bewusst so gelöst")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            zuhoerenStarten()
        } else {
            antworten("Ich darf das Mikrofon nicht benutzen. Bitte erlaube es in den Einstellungen.")
        }
    }

    private fun zuhoerenStarten() {
        handler.removeCallbacks(zurueckZurRuhe)
        stimme?.stop()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            antworten("Auf diesem Handy fehlt die Spracherkennung.")
            return
        }

        erkenner?.destroy()
        erkenner = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(zuhoerer)
        }
        erkannterText = ""
        zustand = Zustand.HOEREN

        val auftrag = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Länger warten, bevor das Satzende angenommen wird
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        erkenner?.startListening(auftrag)
    }

    private fun abbrechen() {
        handler.removeCallbacks(zurueckZurRuhe)
        erkenner?.cancel()
        stimme?.stop()
        zustand = Zustand.RUHE
    }

    // Erst einfache Fragen selbst beantworten (geht auch ohne Internet), sonst den Server fragen
    private fun beantworte(satz: String) {
        technik = ""
        val lokal = lokaleAntwort(satz)
        if (lokal != null) {
            antworten(lokal)
            return
        }
        if (!netz.serverEingerichtet) {
            antworten("Du hast gesagt: $satz")
            return
        }
        zustand = Zustand.DENKEN
        val jetzt = SimpleDateFormat("EEEE, d. MMMM yyyy, HH:mm 'Uhr'", Locale.GERMANY).format(Date())
        Thread {
            val antwort = try {
                netz.frage(satz, jetzt)
            } catch (e: Exception) {
                technik = "${e.javaClass.simpleName}: ${e.message ?: ""}".take(160)
                "Ich habe gerade keine Verbindung. Versuch es bitte gleich nochmal."
            }
            runOnUiThread {
                if (zustand == Zustand.DENKEN) antworten(antwort)
            }
        }.start()
    }

    // Antwort anzeigen und vorlesen, nach 15 Sekunden zurück zum Ruhebildschirm
    private fun antworten(text: String) {
        antwortText = text
        zustand = Zustand.ANTWORT
        if (stimmeBereit) {
            stimme?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "antwort")
        }
        handler.removeCallbacks(zurueckZurRuhe)
        handler.postDelayed(zurueckZurRuhe, 15000)
    }

    private val zuhoerer = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val teil = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!teil.isNullOrBlank()) erkannterText = teil
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isBlank()) {
                antworten("Ich habe dich nicht verstanden. Drück einfach nochmal die Taste.")
            } else {
                erkannterText = text
                beantworte(text)
            }
        }

        override fun onError(error: Int) {
            if (zustand != Zustand.HOEREN) return
            val meldung = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "Ich habe nichts verstanden. Drück einfach nochmal die Taste."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Ich habe gerade kein Internet. Bitte versuch es gleich nochmal."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Ich darf das Mikrofon nicht benutzen."
                else ->
                    "Da hat etwas nicht geklappt. Drück nochmal die Taste. (Fehler $error)"
            }
            antworten(meldung)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onResume() {
        super.onResume()
        aktualisiereDiagnose()
    }

    override fun onDestroy() {
        handler.removeCallbacks(zurueckZurRuhe)
        erkenner?.destroy()
        stimme?.shutdown()
        super.onDestroy()
    }
}

// ---------- Bildschirme ----------

@Composable
fun Ruhebildschirm(diagnose: String, onTaste: () -> Unit) {
    // Uhrzeit jede Sekunde aktualisieren
    var jetzt by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            jetzt = Date()
            delay(1000)
        }
    }

    val deutsch = Locale.GERMANY
    val wochentag = SimpleDateFormat("EEEE", deutsch).format(jetzt)
    val datum = SimpleDateFormat("d. MMMM yyyy", deutsch).format(jetzt)
    val uhrzeit = SimpleDateFormat("HH:mm", deutsch).format(jetzt)
    val stunde = Calendar.getInstance().apply { time = jetzt }.get(Calendar.HOUR_OF_DAY)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Hintergrund)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(wochentag, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Schrift)
        Text(datum, fontSize = 28.sp, color = Schrift)
        Spacer(Modifier.height(8.dp))
        Text(uhrzeit, fontSize = 92.sp, lineHeight = 96.sp, fontWeight = FontWeight.Bold, color = Schrift)
        Text(tageszeit(stunde), fontSize = 26.sp, color = Grau)
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onTaste,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blau, contentColor = Color.White)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Mikrofon(groesse = 72.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Drücken\nund sprechen",
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(diagnose, fontSize = 13.sp, color = Grau, textAlign = TextAlign.Center)
    }
}

@Composable
fun HoerenBildschirm(titel: String, kartenTitel: String, text: String, onAbbrechen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Hintergrund)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(HellBlau, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(Blau, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Mikrofon(groesse = 80.dp)
            }
        }
        Text(titel, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Schrift)

        Karte {
            Text(kartenTitel, fontSize = 20.sp, color = Grau)
            Text(
                if (text.isBlank()) "…" else "„$text“",
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = Schrift
            )
        }

        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onAbbrechen,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(3.dp, Schrift),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Schrift)
        ) {
            Text("Abbrechen", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AntwortBildschirm(antwort: String, technik: String, onNochmal: () -> Unit, onFertig: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Hintergrund)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Lotse sagt:", fontSize = 22.sp, color = Grau)
        Karte {
            Text(antwort, fontSize = 32.sp, lineHeight = 40.sp, color = Schrift)
        }
        if (technik.isNotBlank()) {
            Text("Test-Info: $technik", fontSize = 13.sp, color = Grau)
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onNochmal,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blau, contentColor = Color.White)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Mikrofon(groesse = 52.dp)
                Spacer(Modifier.height(8.dp))
                Text("Nochmal sprechen", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        OutlinedButton(
            onClick = onFertig,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(3.dp, Schrift),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Schrift)
        ) {
            Text("Fertig", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- Bausteine ----------

@Composable
fun Karte(inhalt: @Composable () -> Unit) {
    val form = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, form)
            .border(2.dp, Rand, form)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        inhalt()
    }
}

// Selbst gezeichnetes Mikrofon-Symbol
@Composable
fun Mikrofon(groesse: Dp, farbe: Color = Color.White) {
    Canvas(modifier = Modifier.size(groesse)) {
        val w = size.width
        val h = size.height
        val strich = w * 0.08f
        val linie = Stroke(width = strich, cap = StrokeCap.Round)
        // Mikrofonkopf
        drawRoundRect(
            color = farbe,
            topLeft = Offset(w * 0.36f, h * 0.06f),
            size = Size(w * 0.28f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
            style = linie
        )
        // Bügel
        drawArc(
            color = farbe,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.5f),
            style = linie
        )
        // Stiel und Fuß
        drawLine(farbe, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.9f), strich, StrokeCap.Round)
        drawLine(farbe, Offset(w * 0.34f, h * 0.92f), Offset(w * 0.66f, h * 0.92f), strich, StrokeCap.Round)
    }
}

// Einfache Fragen, die Lotse ohne Internet beantwortet
fun lokaleAntwort(satz: String): String? {
    val t = satz.lowercase(Locale.GERMANY)
    val jetzt = Calendar.getInstance()
    val stunde = jetzt.get(Calendar.HOUR_OF_DAY)
    val minute = jetzt.get(Calendar.MINUTE)

    if ("wie spät" in t || "wie viel uhr" in t || "wieviel uhr" in t || "uhrzeit" in t) {
        return if (minute == 0) "Es ist $stunde Uhr." else "Es ist $stunde Uhr $minute."
    }
    if ("welcher tag" in t || "welchen tag" in t || "welches datum" in t ||
        "der wievielte" in t || "den wievielten" in t || "datum" in t
    ) {
        val datum = SimpleDateFormat("EEEE, 'der' d. MMMM yyyy", Locale.GERMANY).format(jetzt.time)
        return "Heute ist $datum."
    }
    return null
}

fun tageszeit(stunde: Int): String = when (stunde) {
    in 5..9 -> "Morgen"
    in 10..11 -> "Vormittag"
    in 12..13 -> "Mittag"
    in 14..17 -> "Nachmittag"
    in 18..21 -> "Abend"
    else -> "Nacht"
}
