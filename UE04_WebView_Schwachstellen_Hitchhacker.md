# UE-04: Schwachstellen in WebViews ausnutzen – Hitchhacker-App

## 1. Dekompilierung und Analyse

### 1.1 Vorgehen

Die APK-Datei `hitchhacker_v3.apk` wurde mit **JADX-GUI** dekompiliert. Nach dem Öffnen wurde die Package-Struktur unter `space.hitchhacker.guide` analysiert.

### 1.2 Relevante Klasse

> **TODO:** Ersetze den Platzhalter mit dem tatsächlichen Klassennamen aus JADX.

Die Klasse, die den WebView-Quellcode beinhaltet, befindet sich unter:

```
space.hitchhacker.guide.<WebViewActivity/GuideActivity>
```

*(In JADX unter `Source code > space > hitchhacker > guide` navigieren und nach Klassen suchen, die `WebView` instanziieren.)*

---

## 2. Schwachstellen der WebView-Implementierung

### 2.1 Identifizierte Schwachstellen im Quellcode

Folgende Einstellungen sind typischerweise in der WebView-Klasse zu finden und stellen Schwachstellen dar:

| Zeile (ca.) | Code | Schwachstelle |
|---|---|---|
| — | `webView.getSettings().setJavaScriptEnabled(true)` | JavaScript-Ausführung im WebView aktiviert |
| — | `webView.getSettings().setAllowFileAccess(true)` | Zugriff auf lokale Dateien über `file://`-Schema erlaubt |
| — | `webView.getSettings().setAllowUniversalAccessFromFileURLs(true)` | Cross-Origin-Zugriff von `file://`-URLs auf beliebige Ressourcen |
| — | `webView.addJavascriptInterface(...)` | Java-Objekte werden an JavaScript exponiert |

> **TODO:** Trage die exakten Zeilennummern und den genauen Code aus JADX ein.

### 2.2 Erklärung der Schwachstellen

- **`setJavaScriptEnabled(true)`**: Ermöglicht die Ausführung von beliebigem JavaScript-Code innerhalb des WebViews. In Kombination mit anderen Schwachstellen kann ein Angreifer dadurch Schadcode ausführen.

- **`setAllowFileAccess(true)`**: Erlaubt dem WebView, Dateien über das `file://`-Protokoll zu laden. Damit können lokale Dateien des Geräts im WebView angezeigt werden.

- **`setAllowUniversalAccessFromFileURLs(true)`**: Hebt die Same-Origin-Policy für `file://`-URLs auf. Eine lokal geladene Datei kann so auf beliebige andere lokale Dateien zugreifen – eine massive Sicherheitslücke.

- **`addJavascriptInterface(object, "name")`**: Exponiert ein Java-Objekt mit all seinen `@JavascriptInterface`-annotierten Methoden an JavaScript. Jede im WebView geladene Seite (auch externe/injizierte) kann dieses Interface aufrufen.

---

## 3. Laden von lokalen Dateien im WebView

### 3.1 SharedPreferences-Datei laden

**Ziel:** Die unverschlüsselte SharedPreferences-Datei aus `/data/data/space.hitchhacker.guide/shared_prefs/` im WebView anzeigen.

**Vorgehensweise:**

1. Die SharedPreferences-Dateien sind XML-Dateien im App-eigenen Verzeichnis. Der typische Dateiname wäre z.B. `space.hitchhacker.guide_preferences.xml` oder ein anderer Name (in JADX unter `Resources > shared_prefs` oder im Code nach `getSharedPreferences("name", ...)` suchen).

2. Folgende URL im WebView eingeben/laden:

```
file:///data/data/space.hitchhacker.guide/shared_prefs/<DATEINAME>.xml
```

**Warum funktioniert das?**

Das Laden der SharedPreferences-Datei **funktioniert**, weil:

- `setAllowFileAccess(true)` ist gesetzt → der WebView darf `file://`-URLs laden
- Die SharedPreferences-Datei liegt im **eigenen App-Verzeichnis** (`/data/data/space.hitchhacker.guide/`) → die App hat Lese-/Schreibrechte auf dieses Verzeichnis
- Der WebView läuft im Kontext der App und erbt deren Dateisystem-Berechtigungen
- Keine zusätzlichen Permissions sind nötig, da es sich um das private Verzeichnis der App handelt

### 3.2 Datei aus dem Downloads-Verzeichnis laden

**Ziel:** Eine Datei aus `/sdcard/Download/` im WebView laden.

**Vorgehensweise:**

1. Eine Testdatei (z.B. `test.html`) im Emulator unter `/sdcard/Download/` ablegen:

```bash
adb push test.html /sdcard/Download/test.html
```

2. Folgende URL im WebView eingeben/laden:

```
file:///sdcard/Download/test.html
```

**Warum funktioniert das (nicht)?**

Das Laden der Datei aus dem Downloads-Verzeichnis **funktioniert NICHT**, weil:

- `/sdcard/Download/` liegt **außerhalb** des App-Verzeichnisses
- Um auf externe Speicherorte zuzugreifen, benötigt die App die Permission `READ_EXTERNAL_STORAGE` (bzw. ab Android 13: `READ_MEDIA_*`)
- Wenn diese Permission im `AndroidManifest.xml` **nicht deklariert** ist, wird der Zugriff vom Betriebssystem blockiert
- Ab **Android 10 (API 29)** ist zusätzlich **Scoped Storage** aktiv: Apps können standardmäßig nur auf ihren eigenen externen Speicherbereich zugreifen, nicht auf `/sdcard/Download/`
- Selbst wenn `setAllowFileAccess(true)` gesetzt ist, kann der WebView nur Dateien laden, auf die der App-Prozess auf OS-Ebene Zugriff hat

> **TODO:** Prüfe im `AndroidManifest.xml` (in JADX unter `Resources`), ob `READ_EXTERNAL_STORAGE` deklariert ist. Falls ja, funktioniert es möglicherweise auf älteren Android-Versionen. Prüfe auch `targetSdkVersion`.

**Alternativer Fall – Falls es doch funktioniert:**

Wenn die App `READ_EXTERNAL_STORAGE` deklariert UND auf einem Emulator mit Android < 10 läuft ODER `requestLegacyExternalStorage="true"` im Manifest steht, dann könnte der Zugriff funktionieren, weil die App dann Leserechte auf den gesamten externen Speicher hat.

---

## 4. JavaScript Interface – Analyse und Zugriff

### 4.1 Analyse des JavaScript Interface

> **TODO:** Ersetze die Platzhalter mit den tatsächlichen Werten aus JADX.

Im Quellcode findet sich die Klasse **`JInputs`** (aus der JADX-Projektdatei ersichtlich, da sie als expandierter Tree-Knoten gespeichert ist).

Die Registrierung des Interfaces erfolgt typischerweise so:

```java
webView.addJavascriptInterface(new JInputs(), "InterfaceName");
```

Die Klasse `JInputs` enthält Methoden mit der Annotation `@JavascriptInterface`, z.B.:

```java
public class JInputs {
    @JavascriptInterface
    public String getFlag() { ... }

    @JavascriptInterface
    public void showToast(String msg) { ... }
}
```

> **TODO:** Öffne die Klasse `JInputs` in JADX und dokumentiere:
> - Den genauen Interface-Namen (zweiter Parameter von `addJavascriptInterface`)
> - Alle `@JavascriptInterface`-Methoden und ihre Funktionalität

### 4.2 Zugriff über die Guide-Seite

**Was muss wo eingegeben werden?**

Der Zugriff auf das JavaScript Interface über die Guide-Seite der App erfolgt so:

1. **Identifiziere, wie die Guide-Seite Inhalte lädt:** Die Guide-Seite lädt vermutlich eine URL in den WebView. Prüfe in JADX, ob:
   - Eine externe URL per HTTP (nicht HTTPS) geladen wird
   - Ein Eingabefeld für URLs/Suchbegriffe vorhanden ist
   - User-Input in die geladene URL eingebaut wird

2. **JavaScript einschleusen:** Über das Eingabefeld oder die URL-Leiste (falls vorhanden) muss JavaScript-Code injiziert werden, der das exponierte Interface aufruft. Eingabe:

```
javascript:alert(InterfaceName.getFlag())
```

oder als HTML-Injection, falls ein Eingabefeld vorhanden ist:

```html
<script>document.write(InterfaceName.getFlag())</script>
```

> **TODO:** Ersetze `InterfaceName` mit dem tatsächlichen Namen aus `addJavascriptInterface()` und `getFlag()` mit der tatsächlichen Methode.

**Alternative Vorgehensweise über `file://`:**

Falls die App eine URL-Eingabe erlaubt, kann eine lokale HTML-Datei erstellt werden:

1. HTML-Datei erstellen und auf dem Gerät ablegen:

```html
<html>
<body>
<script>
  document.write("Flag: " + InterfaceName.getFlag());
</script>
</body>
</html>
```

2. Diese Datei im App-Verzeichnis ablegen (z.B. via `adb` mit Root-Rechten) und über `file://` laden.

### 4.3 Warum funktioniert der Zugriff? (Schwachstelle)

Der Zugriff auf das JavaScript Interface funktioniert, weil **mehrere Schwachstellen zusammenwirken**:

1. **`addJavascriptInterface` exponiert das Interface global:** Das registrierte Java-Objekt ist in **jedem** Kontext verfügbar, der im WebView geladen wird – nicht nur in der ursprünglich vorgesehenen Seite. Es gibt keine Einschränkung auf bestimmte Origins oder URLs.

2. **`setJavaScriptEnabled(true)` erlaubt JS-Ausführung:** Ohne diese Einstellung könnte das Interface nicht per JavaScript angesprochen werden.

3. **Fehlende URL-Validierung:** Die App prüft nicht (oder unzureichend), welche URLs im WebView geladen werden. Es gibt vermutlich keine oder eine unzureichende Implementierung von `shouldOverrideUrlLoading()` im `WebViewClient`, die das Laden von `javascript:`-URLs oder beliebigen `file://`-URLs verhindern würde.

4. **Kombination der Schwachstellen:** Die Kombination aus aktiviertem JavaScript, exponiertem Interface und fehlendem URL-Filter ermöglicht es, über die Guide-Seite beliebigen JavaScript-Code auszuführen und damit auf das Java-Interface zuzugreifen.

---

## 5. Zusammenfassung der Schwachstellen

| Nr. | Schwachstelle | Auswirkung | Risiko |
|-----|--------------|------------|--------|
| 1 | `setJavaScriptEnabled(true)` | JavaScript-Code kann im WebView ausgeführt werden | Hoch |
| 2 | `setAllowFileAccess(true)` | Lokale Dateien können über `file://` geladen werden | Hoch |
| 3 | `setAllowUniversalAccessFromFileURLs(true)` | Cross-Origin-Zugriff von File-URLs möglich | Kritisch |
| 4 | `addJavascriptInterface(...)` ohne URL-Filterung | Exponiertes Interface ist von jeder geladenen Seite erreichbar | Kritisch |
| 5 | Fehlende `shouldOverrideUrlLoading()`-Prüfung | Beliebige URLs können geladen werden | Mittel |

---

## 6. Verwendete Werkzeuge

- **JADX-GUI** – Dekompilierung der APK und Analyse des Java-Quellcodes
- **Android Emulator** – Ausführung der App und Tests
- **Chrome DevTools** (Remote Debugging) – Debugging des WebViews über `chrome://inspect`
- **ADB** – Dateitransfer und Shell-Zugriff auf den Emulator

---

*Hinweis: Die mit **TODO** markierten Stellen müssen mit den konkreten Werten aus der JADX-Analyse ergänzt werden (Klassennamen, Zeilennummern, Interface-Name, Methodennamen).*
