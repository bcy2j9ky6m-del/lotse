// Lotse-Server (Cloudflare Worker)
// Nimmt den erkannten Satz von der Lotse-App entgegen, fragt das Sprachmodell
// und schickt eine kurze, vorlesbare Antwort zurück.
//
// Benötigte Geheimnisse (Cloudflare → Worker → Settings → Variables and Secrets):
//   GEMINI_KEY  – API-Schlüssel aus Google AI Studio
//   LOTSE_TOKEN – Passwort, das nur die Lotse-App kennt
// Optional (normale Variable):
//   MODELL      – z. B. "gemini-flash-lite-latest" (Standard) oder "gemini-flash-latest"

const ANWEISUNG = `Du bist „Lotse“, die Stimme eines einfachen Sprach-Handys für eine ältere Dame in Bremen.
Alles, was du schreibst, wird laut vorgelesen.

So antwortest du:
- Auf Deutsch, freundlich, ruhig und geduldig. Du duzt sie.
- Höchstens zwei kurze Sätze. Das Wichtigste zuerst.
- Keine Listen, keine Aufzählungszeichen, keine Sternchen, keine Emojis, keine Abkürzungen.
- Zahlen und Uhrzeiten so, wie man sie spricht: „um 14 Uhr“, „am Dienstag, dem 10. November“.
- Wenn sie dasselbe mehrmals fragt, antwortest du jedes Mal wieder freundlich und vollständig.
- Wenn du etwas nicht sicher weißt, sag das ehrlich.

Grenzen:
- Anrufen, Nachrichten und Termine erledigt das Handy selbst, wenn sie es so sagt:
  „Ruf Anna an“, „Schreib Anna, ich komme um drei“, „Was habe ich heute vor?“.
  Wenn sie so etwas möchte, aber anders formuliert hat, erkläre ihr freundlich und kurz, wie sie es sagen kann.
- Wenn sie möchte, dass du dir etwas dauerhaft merkst, sag ihr: „Sag einfach: Merk dir, dass …“.
- Beziehe dich auf frühere Fragen in diesem Gespräch, wenn sie nachfragt (zum Beispiel „Und morgen?“).
- Bei Fragen zu Gesundheit oder Medikamenten gibst du keine Ratschläge zur Dosis,
  sondern empfiehlst, die Pflegekräfte oder den Arzt zu fragen.
- Wenn jemand nach Geld, PIN, Passwörtern oder Kontodaten fragt oder es nach Betrug klingt,
  warnst du deutlich und rätst, mit Bafti zu sprechen.`;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Einfache Prüfseite: im Browser aufrufen → "Lotse-Server läuft"
    if (request.method === "GET") {
      return text("Lotse-Server läuft.");
    }

    if (request.method !== "POST" || url.pathname !== "/frage") {
      return json({ fehler: "unbekannt" }, 404);
    }

    if (!env.LOTSE_TOKEN || request.headers.get("X-Lotse-Token") !== env.LOTSE_TOKEN) {
      return json({ fehler: "nicht erlaubt" }, 401);
    }

    let eingabe;
    try {
      eingabe = await request.json();
    } catch {
      return json({ fehler: "ungültige Anfrage" }, 400);
    }

    const satz = String(eingabe.text || "").slice(0, 500);
    const jetzt = String(eingabe.jetzt || "").slice(0, 100);
    if (!satz) return json({ antwort: "Ich habe nichts gehört." });

    // Gedächtnis: gemerkte Dinge (dauerhaft, vom Handy) und die letzten Fragen und Antworten
    const fakten = (Array.isArray(eingabe.fakten) ? eingabe.fakten : [])
      .slice(0, 30).map((f) => String(f).slice(0, 200));
    const verlauf = (Array.isArray(eingabe.verlauf) ? eingabe.verlauf : []).slice(-6);

    let anweisung = `${ANWEISUNG}\n\nJetzt ist: ${jetzt}.`;
    if (fakten.length > 0) {
      anweisung += `\n\nDas hat sie dir gesagt, damit du es dir merkst (nutze es, wenn es passt):\n- ${fakten.join("\n- ")}`;
    }

    const contents = [];
    for (const w of verlauf) {
      const frage = String(w?.frage || "").slice(0, 500);
      const antwort = String(w?.antwort || "").slice(0, 500);
      if (frage && antwort) {
        contents.push({ role: "user", parts: [{ text: frage }] });
        contents.push({ role: "model", parts: [{ text: antwort }] });
      }
    }
    contents.push({ role: "user", parts: [{ text: satz }] });

    const modell = env.MODELL || "gemini-flash-lite-latest";
    const anfrage = {
      systemInstruction: {
        parts: [{ text: anweisung }],
      },
      contents,
      generationConfig: { maxOutputTokens: 200, temperature: 0.4 },
    };

    try {
      const antwort = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${modell}:generateContent`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": env.GEMINI_KEY,
          },
          body: JSON.stringify(anfrage),
        }
      );

      if (antwort.status === 429) {
        return json({ antwort: "Ich brauche eine kleine Pause. Frag mich bitte gleich nochmal." });
      }
      if (!antwort.ok) {
        const details = await antwort.text();
        return json({ antwort: "Da hat etwas nicht geklappt. Frag mich bitte gleich nochmal.", fehler: details.slice(0, 300) });
      }

      const daten = await antwort.json();
      const teile = daten?.candidates?.[0]?.content?.parts || [];
      let textAntwort = teile.map((t) => t.text || "").join(" ").trim();
      // Sicherheitshalber Formatierungszeichen entfernen, damit nichts Seltsames vorgelesen wird
      textAntwort = textAntwort.replace(/[*#_`>]/g, "").replace(/\s+/g, " ").trim();

      return json({ antwort: textAntwort || "Darauf weiß ich gerade keine Antwort." });
    } catch (e) {
      return json({ antwort: "Ich erreiche mein Gehirn gerade nicht. Frag mich bitte gleich nochmal." });
    }
  },
};

function json(daten, status = 200) {
  return new Response(JSON.stringify(daten), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

function text(inhalt) {
  return new Response(inhalt, { headers: { "Content-Type": "text/plain; charset=utf-8" } });
}
