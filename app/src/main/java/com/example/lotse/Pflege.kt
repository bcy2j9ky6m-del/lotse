package com.example.lotse

// Pflege-Menü hinter der PIN: Einstellungen öffnen oder (am Testgerät) den Kiosk-Modus ausschalten.

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
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

private val PHintergrund = Color(0xFFFBF8F2)
private val PSchrift = Color(0xFF1A1A1A)
private val PGrau = Color(0xFF4A4A4A)
private val PBlau = Color(0xFF1F4E8C)
private val PRot = Color(0xFFB3261E)

private enum class Schritt { NEU, NEU_WIEDERHOLEN, PRUEFEN, MENUE }

@Composable
fun PflegeBildschirm(
    hatPin: Boolean,
    kioskAktiv: Boolean,
    onPinNeu: (String) -> Unit,
    onPinPruefen: (String) -> Boolean,
    onEinstellungen: () -> Unit,
    onKioskAus: () -> Unit,
    onZurueck: () -> Unit
) {
    var schritt by remember { mutableStateOf(if (hatPin) Schritt.PRUEFEN else Schritt.NEU) }
    var eingabe by remember { mutableStateOf("") }
    var ersteEingabe by remember { mutableStateOf("") }
    var meldung by remember { mutableStateOf("") }
    var sicherheitsfrage by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(PHintergrund).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pflege-Zugang", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = PSchrift)

        if (schritt == Schritt.MENUE) {
            Text("Nur für Familie und Pflege", fontSize = 18.sp, color = PGrau)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onEinstellungen,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PBlau, contentColor = Color.White)
            ) { Text("Einstellungen öffnen", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            Text(
                "Mit der Home-Taste kommst du zurück zu Lotse. Lotse sperrt sich dann wieder.",
                fontSize = 16.sp, color = PGrau, textAlign = TextAlign.Center
            )
            if (kioskAktiv) {
                Spacer(Modifier.height(8.dp))
                if (!sicherheitsfrage) {
                    OutlinedButton(
                        onClick = { sicherheitsfrage = true },
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(3.dp, PRot),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = PRot)
                    ) { Text("Kiosk-Modus ganz ausschalten", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                } else {
                    Text(
                        "Wirklich ausschalten? Zum Wiedereinschalten braucht man wieder das USB-Kabel.",
                        fontSize = 18.sp, color = PRot, textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onKioskAus,
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PRot, contentColor = Color.White)
                    ) { Text("Ja, ausschalten", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.weight(1f))
            ZurueckKnopf(onZurueck)
            return@Column
        }

        val anleitung = when (schritt) {
            Schritt.NEU -> "Neue PIN festlegen (4 Ziffern)"
            Schritt.NEU_WIEDERHOLEN -> "PIN zur Sicherheit wiederholen"
            else -> "PIN eingeben"
        }
        Text(anleitung, fontSize = 22.sp, color = PSchrift)
        Text(
            if (eingabe.isEmpty()) "– – – –" else "● ".repeat(eingabe.length).trim(),
            fontSize = 40.sp, fontWeight = FontWeight.Bold, color = PSchrift
        )
        if (meldung.isNotBlank()) Text(meldung, fontSize = 18.sp, color = PRot, textAlign = TextAlign.Center)

        // Ziffernblock
        val reihen = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
        reihen.forEach { reihe ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                reihe.forEach { taste ->
                    Button(
                        onClick = {
                            meldung = ""
                            when (taste) {
                                "⌫" -> eingabe = eingabe.dropLast(1)
                                "OK" -> {
                                    if (eingabe.length != 4) { meldung = "Bitte genau 4 Ziffern eingeben."; return@Button }
                                    when (schritt) {
                                        Schritt.NEU -> { ersteEingabe = eingabe; schritt = Schritt.NEU_WIEDERHOLEN }
                                        Schritt.NEU_WIEDERHOLEN -> if (eingabe == ersteEingabe) {
                                            onPinNeu(eingabe); schritt = Schritt.MENUE
                                        } else {
                                            meldung = "Die PINs waren verschieden. Bitte nochmal."; schritt = Schritt.NEU
                                        }
                                        Schritt.PRUEFEN -> if (onPinPruefen(eingabe)) schritt = Schritt.MENUE
                                        else meldung = "Falsche PIN."
                                        Schritt.MENUE -> {}
                                    }
                                    eingabe = ""
                                }
                                else -> if (eingabe.length < 4) eingabe += taste
                            }
                        },
                        modifier = Modifier.weight(1f).height(62.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (taste == "OK") PBlau else Color.White,
                            contentColor = if (taste == "OK") Color.White else PSchrift
                        ),
                        border = if (taste == "OK") null else BorderStroke(2.dp, Color(0xFFE2DCCF))
                    ) { Text(taste, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        ZurueckKnopf(onZurueck)
    }
}

@Composable
private fun ZurueckKnopf(onZurueck: () -> Unit) {
    OutlinedButton(
        onClick = onZurueck,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(3.dp, PSchrift),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = PSchrift)
    ) { Text("Zurück zu Lotse", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}
