package com.example.lotse

// Lotse – Schritt 1: Ruhebildschirm
// Zeigt Wochentag, Datum, Uhrzeit, Tageszeit und die große Sprechtaste.
// Läuft ab Android 6 (API 23).

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Ruhebildschirm() }
    }
}

@Composable
fun Ruhebildschirm() {
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
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Hintergrund)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(wochentag, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Schrift)
        Text(datum, fontSize = 28.sp, color = Schrift)
        Spacer(Modifier.height(8.dp))
        Text(
            uhrzeit,
            fontSize = 92.sp,
            lineHeight = 96.sp,
            fontWeight = FontWeight.Bold,
            color = Schrift
        )
        Text(tageszeit(stunde), fontSize = 26.sp, color = Grau)
        Spacer(Modifier.height(20.dp))

        // Die große Sprechtaste – das Zuhören kommt in Schritt 2
        Button(
            onClick = {
                Toast.makeText(context, "Zuhören kommt in Schritt 2", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(36.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Blau,
                contentColor = Color.White
            )
        ) {
            Text(
                "Drücken\nund sprechen",
                fontSize = 36.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun tageszeit(stunde: Int): String = when (stunde) {
    in 5..9 -> "Morgen"
    in 10..11 -> "Vormittag"
    in 12..13 -> "Mittag"
    in 14..17 -> "Nachmittag"
    in 18..21 -> "Abend"
    else -> "Nacht"
}
