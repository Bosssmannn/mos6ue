# Protokoll: UE-04 – Schwachstellen in WebViews ausnutzen

## Mikolaj Milczek & Jonathan Rusche

## App-Informationen

- **App:** Hitchhacker Guide v3
- **Package:** `space.hitchhacker.guide`
- **Werkzeug:** JADX-GUI zur Dekompilierung

---

## Schritt 1: APK dekompilieren

Die Datei `hitchhacker_v3.apk` wurde in JADX-GUI geöffnet. Nach dem Laden erscheint die Package-Struktur unter `Source code > space > hitchhacker > guide`.

Dort befinden sich mehrere Klassen. Die für den WebView relevante Klasse wurde durch Suche nach `WebView` im Code identifiziert.

**Ergebnis:**
- Klasse mit WebView-Implementierung: `space.hitchhacker.guide.ui.guide.GuideFragment`
- JavaScript-Interface-Klasse: `JInputs`

---

## Schritt 2: WebView-Konfiguration analysieren

In der WebView-Klasse wurde die Methode gesucht, in der der WebView konfiguriert wird (typisch: `onCreate` oder eine eigene Setup-Methode).

Folgende sicherheitsrelevante Einstellungen wurden im Quellcode gefunden:

```java
// Zeile [50]: JavaScript aktiviert
webView.getSettings().setJavaScriptEnabled(true);

// Zeile [51]: JavaScript Interface registriert
webView.addJavascriptInterface(new Interface(), "Wormhole");

// Zeile [52]: Dateizugriff erlaubt
webView.getSettings().setAllowFileAccess(true);

// Zeile [53]: Universeller Dateizugriff erlaubt
webView.setWebViewClient(new WebViewClient(){...});
```

---

## Schritt 3: SharedPreferences im WebView laden

### Vorgehensweise

1. Zuerst den Dateinamen der SharedPreferences ermitteln. Dazu im Quellcode nach `getSharedPreferences` gesucht:

```java
// Fundstelle in JADX:
SharedPreferences prefs = getSharedPreferences("[PREF_NAME]", MODE_PRIVATE);
```

2. Die vollständige URL zum Laden zusammengesetzt:

```
file:///data/data/space.hitchhacker.guide/shared_prefs/[PREF_NAME].xml
```

3. Diese URL im WebView der App eingegeben. Dazu [BESCHREIBUNG WIE – z.B. "in das URL-Eingabefeld auf der Guide-Seite eingetragen" oder "über ein Intent an die App geschickt"].

### Ergebnis

Das Laden der SharedPreferences-Datei **funktioniert**, weil:

- `setAllowFileAccess(true)` ist gesetzt, daher darf der WebView `file://`-URLs laden
- Die Datei liegt unter `/data/data/space.hitchhacker.guide/`, also im eigenen App-Verzeichnis
- Der WebView-Prozess läuft mit den Berechtigungen der App und hat daher Lese-/Schreibzugriff auf das eigene Datenverzeichnis
- Es wird keine zusätzliche Android-Permission benötigt

---

## Schritt 4: Datei aus /sdcard/Download laden

### Vorgehensweise

1. Eine Testdatei auf dem Emulator erstellt:

```bash
adb shell "echo '<h1>Test</h1>' > /sdcard/Download/test.html"
```

2. Versucht, diese im WebView zu laden:

```
file:///sdcard/Download/test.html
```

### Ergebnis

Das Laden **funktioniert nicht**, weil:

- `/sdcard/Download/` liegt außerhalb des App-Verzeichnisses
- Die App benötigt dafür die Permission `READ_EXTERNAL_STORAGE` im AndroidManifest
- Im Manifest [IST / IST NICHT] diese Permission deklariert: `[AUS JADX PRÜFEN: Resources > AndroidManifest.xml]`
- Ab Android 10 (API 29) blockiert Scoped Storage den Zugriff auf fremde Verzeichnisse zusätzlich
- `setAllowFileAccess(true)` erlaubt zwar `file://`-URLs, aber der WebView kann nur Dateien lesen, auf die der App-Prozess auf Betriebssystem-Ebene Zugriff hat

> Falls die Permission vorhanden ist und der Emulator Android < 10 nutzt, funktioniert es möglicherweise doch. Das muss am Emulator getestet werden.

---

## Schritt 5: JavaScript Interface analysieren

### Analyse der Klasse JInputs

Die Klasse `JInputs` wurde in JADX geöffnet (unter `space.hitchhacker.guide`).

```java
public class JInputs {

    @JavascriptInterface
    public [RÜCKGABETYP] [METHODENNAME]([PARAMETER]) {
        [METHODENINHALT]
    }

    // weitere Methoden mit @JavascriptInterface:
    // [AUS JADX ÜBERNEHMEN]
}
```

Das Interface wurde im WebView unter folgendem Namen registriert:

```java
webView.addJavascriptInterface(new JInputs(), "[INTERFACE_NAME]");
```

Damit sind alle `@JavascriptInterface`-Methoden aus JavaScript heraus über `window.[INTERFACE_NAME].[METHODENNAME]()` aufrufbar.

---

## Schritt 6: Über die Guide-Seite auf das Interface zugreifen

### Analyse der Guide-Seite

In JADX wurde untersucht, wie die Guide-Seite Inhalte in den WebView lädt:

- Die Guide-Seite lädt [BESCHREIBUNG – z.B. "eine externe URL", "eine lokale HTML-Datei", "User-Input wird in die URL eingebaut"]
- Es gibt [ein Eingabefeld / einen URL-Parameter / eine Suchfunktion], über das eigener Inhalt eingeschleust werden kann
- Die Methode `shouldOverrideUrlLoading()` im WebViewClient [FEHLT / IST VORHANDEN ABER FILTERT NICHT]

### Was muss wo eingegeben werden?

**Variante A – JavaScript-URL im Eingabefeld:**

Falls die App ein Eingabefeld hat, in dem URLs eingegeben werden können:

```
javascript:alert(window.[INTERFACE_NAME].[METHODENNAME]())
```

**Variante B – HTML mit JavaScript erstellen und über file:// laden:**

Falls die App URLs lädt, kann eine eigene HTML-Datei im App-Verzeichnis platziert werden:

```html
<html>
<body>
<script>
    document.write(window.[INTERFACE_NAME].[METHODENNAME]());
</script>
</body>
</html>
```

Diese Datei dann über `file://` laden.

**Variante C – Injection über die Guide-URL:**

Falls die Guide-Seite eine externe URL per HTTP (nicht HTTPS) lädt, kann per Man-in-the-Middle-Angriff JavaScript in die Antwort injiziert werden.

> Die konkrete Variante hängt davon ab, wie die Guide-Seite aufgebaut ist. In JADX prüfen, welche URL geladen wird und ob User-Input einfließt.

### Warum funktioniert der Zugriff?

Der Zugriff auf das JavaScript Interface funktioniert, weil:

1. **Das Interface ist global exponiert:** `addJavascriptInterface()` macht das Java-Objekt für jede Seite verfügbar, die im WebView geladen wird – nicht nur für eine bestimmte URL
2. **JavaScript ist aktiviert:** Durch `setJavaScriptEnabled(true)` kann JS-Code ausgeführt werden, der das Interface anspricht
3. **Keine URL-Filterung:** Die App prüft nicht, welche Seiten im WebView geladen werden. Es fehlt eine Validierung in `shouldOverrideUrlLoading()`, die nur vertrauenswürdige URLs zulässt
4. **Fehlende Origin-Prüfung:** Das JavaScript Interface unterscheidet nicht zwischen vertrauenswürdigen und nicht-vertrauenswürdigen Quellen

Die Schwachstelle liegt also in der Kombination aus exponiertem Interface, aktiviertem JavaScript und fehlender URL-/Origin-Validierung.

---

## Zusammenfassung

| Aufgabe | Ergebnis | Grund |
|---|---|---|
| SharedPreferences laden | Funktioniert | `setAllowFileAccess(true)` + Datei liegt im eigenen App-Verzeichnis |
| Datei aus /sdcard/Download laden | Funktioniert nicht | Fehlende Storage-Permission / Scoped Storage |
| JS-Interface über Guide-Seite | Funktioniert | Interface global exponiert + keine URL-Validierung |

---

## Platzhalter-Übersicht (aus JADX ergänzen)

| Was | Wo in JADX nachschauen |
|---|---|
| `[KLASSENNAME]` | Klasse mit `WebView` unter `space.hitchhacker.guide` |
| `[INTERFACE_NAME]` | Zweiter Parameter bei `addJavascriptInterface(...)` |
| `[METHODENNAME]` | Methoden mit `@JavascriptInterface` in Klasse `JInputs` |
| `[PREF_NAME]` | Parameter bei `getSharedPreferences("...", ...)` |
| `[NR]` (Zeilennummern) | Zeilennummern in JADX links neben dem Code |
| Storage-Permission | `Resources > AndroidManifest.xml` → nach `READ_EXTERNAL_STORAGE` suchen |
| Guide-URL | Im Code nach `loadUrl` oder `loadData` suchen |
