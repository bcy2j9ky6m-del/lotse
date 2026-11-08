package com.example.lotse

// Lotse – Schritt 4: Adressbuch, Anrufen mit Rückfrage, "Wer hat angerufen?"
// Läuft ab Android 6 (API 23).

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
private val Gruen = Color(0xFF1E6B3A)
private val Rand = Color(0xFFE2DCCF)
private val Sand = Color(0xFFE8D9C4)
private val Braun = Color(0xFF5A4325)

enum class Zustand { RUHE, HOEREN, DENKEN, RUECKFRAGE, ANTWORT }

// Eine offene Frage an Omama: entweder "Soll ich X anrufen?" oder "Wen meinst du?"
data class Rueckfrage(val frage: String, val kandidaten: List<Kontakt>, val bestaetigen: Boolean)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    // Was gerade auf dem Bildschirm zu sehen ist
    private var zustand by mutableStateOf(Zustand.RUHE)
    private var erkannterText by mutableStateOf("")
    private var antwortText by mutableStateOf("")
    private var diagnose by mutableStateOf("Prüfe Sprachfunktionen …")
    private var technik by mutableStateOf("") // kleine Fehlerinfo für Tests, wird nicht vorgelesen
    private var rueckfrage by mutableStateOf<Rueckfrage?>(null)
    private var mithoeren by mutableStateOf(false)   // hört während einer Rückfrage zu
    private var mithoerText by mutableStateOf("")
    private var heuteText by mutableStateOf("") // nächster Termin für den Ruhebildschirm

    private var anrufZiel: Kontakt? = null
    private var nachfragen = 0

    private val netz by lazy { Netz(applicationContext) }
    private var stimme: TextToSpeech? = null
    private var stimmeBereit = false
    private var erkenner: SpeechRecognizer? = null

    private val handler = Handler(Looper.getMainLooper())
    private val zurueckZurRuhe = Runnable { abbrechen() }

    // Alle 30 Sekunden: Termin-Anzeige aktualisieren und prüfen, ob der Morgengruß fällig ist
    private val regelmaessig = object : Runnable {
        override fun run() {
            aktualisiereHeute()
            pruefeMorgengruss()
            handler.postDelayed(this, 30_000)
        }
    }

    private val einstellungen by lazy { getSharedPreferences("lotse", MODE_PRIVATE) }
    private val morgengrussAb = 9 // Uhr

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stimme = TextToSpeech(this, this)

        setContent {
            when (zustand) {
                Zustand.RUHE -> Ruhebildschirm(diagnose = diagnose, heute = heuteText, onTaste = { tasteGedrueckt() })
                Zustand.HOEREN -> HoerenBildschirm(
                    titel = "Ich höre zu …", kartenTitel = "Du sagst:",
                    text = erkannterText, onAbbrechen = { abbrechen() }
                )
                Zustand.DENKEN -> HoerenBildschirm(
                    titel = "Ich überlege …", kartenTitel = "Du hast gesagt:",
                    text = erkannterText, onAbbrechen = { abbrechen() }
                )
                Zustand.RUECKFRAGE -> rueckfrage?.let { r ->
                    RueckfrageBildschirm(
                        rueckfrage = r,
                        gesagt = erkannterText,
                        mithoeren = mithoeren,
                        mithoerText = mithoerText,
                        onJa = { anrufen(r.kandidaten.first()) },
                        onWahl = { k -> bestaetigeAnruf(k) },
                        onNein = { abgebrochen("Gut, ich rufe nicht an.") }
                    )
                }
                Zustand.ANTWORT -> AntwortBildschirm(
                    antwort = antwortText, technik = technik,
                    onNochmal = { tasteGedrueckt() }, onFertig = { abbrechen() }
                )
            }
        }
    }

    // ---------- Sprachausgabe ----------

    override fun onInit(status: Int) {
        val tts = stimme
        if (status == TextToSpeech.SUCCESS && tts != null) {
            val ergebnis = tts.setLanguage(Locale.GERMANY)
            stimmeBereit = ergebnis != TextToSpeech.LANG_MISSING_DATA &&
                ergebnis != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setSpeechRate(0.85f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { runOnUiThread { nachDemSprechen(utteranceId) } }
                @Deprecated("Pflicht-Methode")
                override fun onError(utteranceId: String?) { runOnUiThread { nachDemSprechen(utteranceId) } }
            })
        }
        aktualisiereDiagnose()
    }

    private fun sprich(text: String, id: String) {
        if (stimmeBereit) {
            stimme?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        } else {
            nachDemSprechen(id)
        }
    }

    // Was passieren soll, wenn ein Satz zu Ende gesprochen ist
    private fun nachDemSprechen(id: String?) {
        when (id) {
            "rueckfrage" -> if (zustand == Zustand.RUECKFRAGE) zuhoerenStarten(fuerRueckfrage = true)
            "anruf" -> anrufStarten()
            // Erst wenn die Antwort fertig vorgelesen ist, läuft die Zeit bis zum Ruhebildschirm
            "antwort" -> if (zustand == Zustand.ANTWORT) {
                handler.removeCallbacks(zurueckZurRuhe)
                handler.postDelayed(zurueckZurRuhe, 15000)
            }
        }
    }

    private fun aktualisiereDiagnose() {
        val hoeren = if (SpeechRecognizer.isRecognitionAvailable(this)) "ja" else "NEIN"
        val sprechen = if (stimmeBereit) "ja" else "NEIN"
        val gehirn = if (netz.serverEingerichtet) "ja" else "NEIN"
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        diagnose = "Version $version · Zuhören: $hoeren · Stimme: $sprechen · Server: $gehirn"
    }

    // ---------- Berechtigungen ----------

    private fun erlaubt(recht: String) = checkSelfPermission(recht) == PackageManager.PERMISSION_GRANTED

    private val benoetigteRechte = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CALENDAR
    )

    private fun tasteGedrueckt() {
        val fehlend = benoetigteRechte.filter { !erlaubt(it) }
        if (fehlend.isNotEmpty()) {
            requestPermissions(fehlend.toTypedArray(), 1)
        } else {
            zuhoerenStarten()
        }
    }

    @Deprecated("Für Android 6 bewusst so gelöst")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        aktualisiereHeute()
        if (erlaubt(Manifest.permission.RECORD_AUDIO)) {
            zuhoerenStarten()
        } else {
            antworten("Ich darf das Mikrofon nicht benutzen. Bitte erlaube es in den Einstellungen.")
        }
    }

    // ---------- Zuhören ----------

    private fun zuhoerenStarten(fuerRueckfrage: Boolean = false) {
        if (!fuerRueckfrage) {
            handler.removeCallbacks(zurueckZurRuhe)
            rueckfrage = null
            anrufZiel = null
        }
        stimme?.stop()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            if (!fuerRueckfrage) antworten("Auf diesem Handy fehlt die Spracherkennung.")
            return
        }

        erkenner?.destroy()
        erkenner = SpeechRecognizer.createSpeechRecognizer(this).apply { setRecognitionListener(zuhoerer) }

        if (fuerRueckfrage) {
            mithoeren = true
            mithoerText = ""
        } else {
            erkannterText = ""
            zustand = Zustand.HOEREN
        }

        val auftrag = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        erkenner?.startListening(auftrag)
    }

    private val zuhoerer = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val teil = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (teil.isNullOrBlank()) return
            if (zustand == Zustand.RUECKFRAGE) mithoerText = teil else erkannterText = teil
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (zustand == Zustand.RUECKFRAGE) {
                mithoeren = false
                mithoerText = text
                if (text.isNotBlank()) rueckfrageBeantwortet(text)
                return
            }
            if (text.isBlank()) {
                antworten("Ich habe dich nicht verstanden. Drück einfach nochmal die Taste.")
            } else {
                erkannterText = text
                beantworte(text)
            }
        }

        override fun onError(error: Int) {
            if (zustand == Zustand.RUECKFRAGE) {
                mithoeren = false // Knöpfe bleiben, Omama kann tippen
                return
            }
            if (zustand != Zustand.HOEREN) return
            val meldung = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "Ich habe nichts verstanden. Drück einfach nochmal die Taste."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Ich habe gerade kein Internet. Bitte versuch es gleich nochmal."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Ich darf das Mikrofon nicht benutzen."
                else -> "Da hat etwas nicht geklappt. Drück nochmal die Taste. (Fehler $error)"
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

    // ---------- Verstehen und Antworten ----------

    private fun beantworte(satz: String) {
        technik = ""

        when (val befehl = Befehle.erkenne(satz)) {
            is Befehl.Anrufen -> { anrufVorbereiten(befehl.name); return }
            Befehl.WerHatAngerufen -> {
                if (erlaubt(Manifest.permission.READ_CALL_LOG)) antworten(Kontakte.letzteAnrufe(this))
                else antworten("Ich darf die Anrufliste nicht lesen. Bitte erlaube es in den Einstellungen.")
                return
            }
            is Befehl.TermineAmTag, is Befehl.TermineMehrereTage, is Befehl.WannIst, Befehl.Morgengruss -> {
                kalenderAntwort(befehl); return
            }
            Befehl.TerminEintragen -> {
                antworten("Termine eintragen kann ich noch nicht. Das macht Bafti für dich. Ich kann dir aber sagen, was du vorhast.")
                return
            }
            Befehl.Keiner -> {}
        }

        lokaleAntwort(satz)?.let { antworten(it); return }

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
            runOnUiThread { if (zustand == Zustand.DENKEN) antworten(antwort) }
        }.start()
    }

    // ---------- Kalender ----------

    private fun kalenderAntwort(befehl: Befehl) {
        if (!erlaubt(Manifest.permission.READ_CALENDAR)) {
            antworten("Ich darf den Kalender nicht lesen. Bitte erlaube es in den Einstellungen.")
            return
        }
        val text = try {
            when (befehl) {
                is Befehl.TermineAmTag -> Kalender.tagesUebersicht(this, befehl.versatzTage, befehl.bezeichnung)
                is Befehl.TermineMehrereTage -> Kalender.mehrereTage(this, befehl.tage)
                is Befehl.WannIst -> {
                    val gefunden = Kalender.suche(this, befehl.suchwort)
                    when {
                        gefunden != null -> Kalender.gefundenText(gefunden)
                        befehl.suchwort.isBlank() -> "In den nächsten Wochen hast du keine Termine."
                        else -> "Dazu habe ich in den nächsten Wochen keinen Termin gefunden."
                    }
                }
                Befehl.Morgengruss -> morgengrussText()
                else -> "Dazu habe ich nichts gefunden."
            }
        } catch (e: Exception) {
            technik = "${e.javaClass.simpleName}: ${e.message ?: ""}".take(160)
            "Ich kann den Kalender gerade nicht lesen."
        }
        antworten(text)
    }

    private fun morgengrussText(): String {
        val stunde = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val gruss = when (stunde) {
            in 5..10 -> "Guten Morgen."
            in 11..17 -> "Hallo."
            else -> "Guten Abend."
        }
        val datum = SimpleDateFormat("EEEE, 'der' d. MMMM", Locale.GERMANY).format(Date())
        val termine = if (erlaubt(Manifest.permission.READ_CALENDAR)) " " + Kalender.tagesUebersicht(this, 0, "heute") else ""
        return "$gruss Heute ist $datum.$termine"
    }

    private fun aktualisiereHeute() {
        heuteText = if (erlaubt(Manifest.permission.READ_CALENDAR)) {
            try { Kalender.naechsterHeute(this) } catch (_: Exception) { "" }
        } else ""
    }

    // Einmal am Tag ab 9 Uhr begrüßt Lotse von selbst – nur wenn gerade nichts anderes läuft
    private fun pruefeMorgengruss() {
        if (zustand != Zustand.RUHE) return
        val stunde = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (stunde < morgengrussAb || stunde >= 12) return
        val heute = SimpleDateFormat("yyyyMMdd", Locale.GERMANY).format(Date())
        if (einstellungen.getString("morgengruss", "") == heute) return
        einstellungen.edit().putString("morgengruss", heute).apply()
        technik = ""
        antworten(morgengrussText())
    }

    // Antwort anzeigen und vorlesen, nach 15 Sekunden zurück zum Ruhebildschirm
    private fun antworten(text: String) {
        antwortText = text
        zustand = Zustand.ANTWORT
        handler.removeCallbacks(zurueckZurRuhe)
        // Sicherheitsnetz, falls das Ende des Vorlesens nicht gemeldet wird
        handler.postDelayed(zurueckZurRuhe, 120000)
        sprich(text, "antwort") // danach startet der 15-Sekunden-Zähler
    }

    private fun abbrechen() {
        handler.removeCallbacks(zurueckZurRuhe)
        erkenner?.cancel()
        stimme?.stop()
        rueckfrage = null
        anrufZiel = null
        mithoeren = false
        zustand = Zustand.RUHE
    }

    // ---------- Anrufen ----------

    private fun anrufVorbereiten(name: String) {
        if (!erlaubt(Manifest.permission.READ_CONTACTS)) {
            antworten("Ich darf das Adressbuch nicht lesen. Bitte erlaube es in den Einstellungen.")
            return
        }
        val treffer = Kontakte.suche(this, name)
        when {
            treffer.isEmpty() ->
                antworten("Ich habe niemanden mit dem Namen ${name.replaceFirstChar { it.uppercase() }} im Adressbuch gefunden.")
            treffer.size == 1 -> bestaetigeAnruf(treffer[0])
            else -> {
                val auswahl = treffer.take(3)
                val namen = if (auswahl.size == 2) "${auswahl[0].name} und ${auswahl[1].name}"
                else "${auswahl[0].name}, ${auswahl[1].name} und ${auswahl[2].name}"
                val anzahl = if (auswahl.size == 2) "zwei" else "drei"
                frageNach(Rueckfrage("Ich kenne $anzahl: $namen. Wen meinst du?", auswahl, bestaetigen = false))
            }
        }
    }

    private fun bestaetigeAnruf(kontakt: Kontakt) {
        frageNach(Rueckfrage("Ich rufe jetzt ${kontakt.name} an. Okay?", listOf(kontakt), bestaetigen = true))
    }

    private fun frageNach(r: Rueckfrage) {
        handler.removeCallbacks(zurueckZurRuhe)
        erkenner?.cancel()
        nachfragen = 0
        rueckfrage = r
        mithoeren = false
        mithoerText = ""
        zustand = Zustand.RUECKFRAGE
        sprich(r.frage, "rueckfrage") // danach wird automatisch zugehört
        handler.postDelayed(zurueckZurRuhe, 40000)
    }

    private fun rueckfrageBeantwortet(antwort: String) {
        val r = rueckfrage ?: return
        if (Befehle.istNein(antwort)) {
            abgebrochen(if (r.bestaetigen) "Gut, ich rufe nicht an." else "Gut, dann nicht.")
            return
        }
        if (r.bestaetigen) {
            if (Befehle.istJa(antwort)) { anrufen(r.kandidaten.first()); return }
        } else {
            Befehle.waehle(antwort, r.kandidaten)?.let { bestaetigeAnruf(it); return }
        }
        // Nicht verstanden: einmal freundlich nachfragen, dann nur noch die Knöpfe anbieten
        if (nachfragen < 1) {
            nachfragen++
            val hilfe = if (r.bestaetigen) "Sag bitte Ja oder Nein." else "Sag bitte den Namen, oder tipp auf die richtige Person."
            sprich(hilfe, "rueckfrage")
        }
    }

    private fun abgebrochen(text: String) {
        rueckfrage = null
        erkenner?.cancel()
        mithoeren = false
        antworten(text)
    }

    private fun anrufen(kontakt: Kontakt) {
        handler.removeCallbacks(zurueckZurRuhe)
        erkenner?.cancel()
        mithoeren = false
        rueckfrage = null
        if (!erlaubt(Manifest.permission.CALL_PHONE)) {
            antworten("Ich darf nicht telefonieren. Bitte erlaube es in den Einstellungen.")
            return
        }
        anrufZiel = kontakt
        antwortText = "Ich rufe ${kontakt.name} an …"
        technik = ""
        zustand = Zustand.ANTWORT
        sprich("Es klingelt gleich bei ${kontakt.name}.", "anruf") // nach dem Satz wird gewählt
    }

    private fun anrufStarten() {
        val kontakt = anrufZiel ?: return
        anrufZiel = null
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(kontakt.nummer))))
        } catch (e: Exception) {
            technik = "${e.javaClass.simpleName}: ${e.message ?: ""}".take(160)
            antworten("Das Anrufen hat nicht geklappt.")
            return
        }
        handler.removeCallbacks(zurueckZurRuhe)
        handler.postDelayed(zurueckZurRuhe, 5000)
    }

    override fun onResume() {
        super.onResume()
        aktualisiereDiagnose()
        handler.removeCallbacks(regelmaessig)
        handler.postDelayed(regelmaessig, 2_000) // kurz warten, bis die Stimme bereit ist
    }

    override fun onPause() {
        handler.removeCallbacks(regelmaessig)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(zurueckZurRuhe)
        handler.removeCallbacks(regelmaessig)
        erkenner?.destroy()
        stimme?.shutdown()
        super.onDestroy()
    }
}

// ---------- Bildschirme ----------

@Composable
fun Ruhebildschirm(diagnose: String, heute: String, onTaste: () -> Unit) {
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
        modifier = Modifier.fillMaxSize().background(Hintergrund).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(wochentag, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Schrift)
        Text(datum, fontSize = 28.sp, color = Schrift)
        Spacer(Modifier.height(8.dp))
        Text(uhrzeit, fontSize = 92.sp, lineHeight = 96.sp, fontWeight = FontWeight.Bold, color = Schrift)
        Text(tageszeit(stunde), fontSize = 26.sp, color = Grau)
        Spacer(Modifier.height(8.dp))
        if (heute.isNotBlank()) {
            Karte {
                Text("Heute", fontSize = 18.sp, color = Grau)
                Text(heute, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = Schrift, maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onTaste,
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blau, contentColor = Color.White)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Mikrofon(groesse = 72.dp)
                Spacer(Modifier.height(12.dp))
                Text("Drücken\nund sprechen", fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        Text(diagnose, fontSize = 13.sp, color = Grau, textAlign = TextAlign.Center)
    }
}

@Composable
fun HoerenBildschirm(titel: String, kartenTitel: String, text: String, onAbbrechen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Hintergrund).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(Modifier.size(200.dp).background(HellBlau, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(146.dp).background(Blau, CircleShape), contentAlignment = Alignment.Center) {
                Mikrofon(groesse = 76.dp)
            }
        }
        Text(titel, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Schrift)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Karte {
                Text(kartenTitel, fontSize = 20.sp, color = Grau)
                Text(if (text.isBlank()) "…" else "„$text“", fontSize = 28.sp, lineHeight = 34.sp, color = Schrift)
            }
        }
        UmrandeterKnopf("Abbrechen", hoehe = 88.dp, onClick = onAbbrechen)
    }
}

@Composable
fun RueckfrageBildschirm(
    rueckfrage: Rueckfrage,
    gesagt: String,
    mithoeren: Boolean,
    mithoerText: String,
    onJa: () -> Unit,
    onWahl: (Kontakt) -> Unit,
    onNein: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Hintergrund).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (gesagt.isNotBlank()) {
            Text("Du hast gesagt: „$gesagt“", fontSize = 18.sp, color = Grau, textAlign = TextAlign.Center)
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (rueckfrage.bestaetigen) {
                val k = rueckfrage.kandidaten.first()
                Bildchen(k, 170.dp)
                Text(k.name, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = Schrift, textAlign = TextAlign.Center)
                Text("Soll ich anrufen?", fontSize = 30.sp, color = Schrift)
            } else {
                Text(rueckfrage.frage, fontSize = 26.sp, lineHeight = 32.sp, color = Schrift, textAlign = TextAlign.Center)
                rueckfrage.kandidaten.forEach { k ->
                    Button(
                        onClick = { onWahl(k) },
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Schrift),
                        border = BorderStroke(2.dp, Rand)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Bildchen(k, 64.dp)
                            Spacer(Modifier.width(16.dp))
                            Text(k.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Zeigt, dass Lotse gerade auf die Antwort hört
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mithoeren) {
                Mikrofon(groesse = 26.dp, farbe = Blau)
                Spacer(Modifier.width(8.dp))
            }
            val hinweis = when {
                mithoerText.isNotBlank() -> "„$mithoerText“"
                mithoeren -> if (rueckfrage.bestaetigen) "Ich höre zu … sag „Ja“ oder „Nein“" else "Ich höre zu … sag den Namen"
                else -> "Tipp auf einen Knopf"
            }
            Text(hinweis, fontSize = 18.sp, color = Grau)
        }

        if (rueckfrage.bestaetigen) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onJa,
                    modifier = Modifier.weight(1f).height(130.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gruen, contentColor = Color.White)
                ) { Text("JA", fontSize = 40.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onNein,
                    modifier = Modifier.weight(1f).height(130.dp),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(3.dp, Schrift),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Schrift)
                ) { Text("NEIN", fontSize = 40.sp, fontWeight = FontWeight.Bold) }
            }
        } else {
            UmrandeterKnopf("Abbrechen", hoehe = 84.dp, onClick = onNein)
        }
    }
}

@Composable
fun AntwortBildschirm(antwort: String, technik: String, onNochmal: () -> Unit, onFertig: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Hintergrund).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Langer Text wird scrollbar, die Knöpfe bleiben immer unten sichtbar
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Lotse sagt:", fontSize = 22.sp, color = Grau)
            Karte { Text(antwort, fontSize = 32.sp, lineHeight = 40.sp, color = Schrift) }
            if (technik.isNotBlank()) Text("Test-Info: $technik", fontSize = 13.sp, color = Grau)
        }
        Button(
            onClick = onNochmal,
            modifier = Modifier.fillMaxWidth().height(130.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blau, contentColor = Color.White)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Mikrofon(groesse = 48.dp)
                Spacer(Modifier.height(6.dp))
                Text("Nochmal sprechen", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        UmrandeterKnopf("Fertig", hoehe = 80.dp, onClick = onFertig)
    }
}

// ---------- Bausteine ----------

@Composable
fun UmrandeterKnopf(text: String, hoehe: Dp, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(hoehe),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(3.dp, Schrift),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Schrift)
    ) { Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
}

// Runder Platzhalter mit Anfangsbuchstaben (später: Foto aus dem Adressbuch)
@Composable
fun Bildchen(kontakt: Kontakt, groesse: Dp) {
    Box(Modifier.size(groesse).background(Sand, CircleShape), contentAlignment = Alignment.Center) {
        Text(kontakt.anfangsbuchstabe, fontSize = (groesse.value * 0.45f).sp, fontWeight = FontWeight.Bold, color = Braun)
    }
}

@Composable
fun Karte(inhalt: @Composable () -> Unit) {
    val form = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.White, form).border(2.dp, Rand, form).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) { inhalt() }
}

// Selbst gezeichnetes Mikrofon-Symbol
@Composable
fun Mikrofon(groesse: Dp, farbe: Color = Color.White) {
    Canvas(modifier = Modifier.size(groesse)) {
        val w = size.width
        val h = size.height
        val strich = w * 0.08f
        val linie = Stroke(width = strich, cap = StrokeCap.Round)
        drawRoundRect(
            color = farbe, topLeft = Offset(w * 0.36f, h * 0.06f), size = Size(w * 0.28f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f), style = linie
        )
        drawArc(
            color = farbe, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.22f), size = Size(w * 0.56f, h * 0.5f), style = linie
        )
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
