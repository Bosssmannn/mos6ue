# UE-04: Schwachstellen in WebViews ausnutzen

## Team
- Jonathan Rusche
- Mikolaj Milczek

---

## Teil 1: Laden von lokalen Dateien im WebView (2P)

### Name der Klasse

Die Klasse, die den Quellcode für den WebView beinhaltet, ist:

```
space.hitchhacker.guide.ui.guide.GuideFragment
```

### Schwachstellen im Quellcode

In der Methode `onViewCreated` der Klasse `GuideFragment` befinden sich folgende sicherheitskritische Stellen:

```java
webView.getSettings().setJavaScriptEnabled(true);
```
-> Erlaubt die Ausführung von JavaScript im WebView.

```java
webView.getSettings().setAllowFileAccess(true);
```
-> Erlaubt dem WebView, lokale Dateien über das `file://`-Protokoll zu laden.

```java
webView.addJavascriptInterface(new Interface(), "Wormhole");
```
-> Exponiert ein Java-Objekt namens `Wormhole` an JavaScript – aufrufbar von jeder geladenen Seite.

### Vorgehensweise: SharedPreferences-Datei laden

Die App lädt beim Start bereits selbst eine SharedPreferences-Datei (`webView.loadUrl(sharedPrefsUrl)`). Um die Datei manuell zu laden, wird folgende URL im WebView aufgerufen:

```
file:///data/data/space.hitchhacker.guide/shared_prefs/shared_prefs.xml
```

**Warum funktioniert das:**
`setAllowFileAccess(true)` ist gesetzt, daher darf der WebView `file://`-URLs laden. Die SharedPreferences-Datei liegt im eigenen App-Verzeichnis `/data/data/space.hitchhacker.guide/`. Der WebView-Prozess läuft mit den Berechtigungen der App und hat automatisch Lese- und Schreibzugriff auf das eigene Datenverzeichnis. Es wird keine zusätzliche Android-Permission benötigt.

### Vorgehensweise: Datei aus /sdcard/Download laden

Eine Testdatei wird per ADB im Downloads-Verzeichnis abgelegt:

```bash
adb shell "echo '<h1>Test</h1>' > /sdcard/Download/test.html"
```

Anschließend wird versucht, diese URL im WebView zu laden:

```
file:///sdcard/Download/test.html
```

**Warum funktioniert das nicht:**
Das Verzeichnis `/sdcard/Download/` liegt außerhalb des App-Verzeichnisses. Um darauf zuzugreifen, benötigt die App die Permission `READ_EXTERNAL_STORAGE` im AndroidManifest. Selbst wenn `setAllowFileAccess(true)` gesetzt ist, kann der WebView nur Dateien lesen, auf die der App-Prozess auf Betriebssystem-Ebene Zugriff hat. Ab Android 10 (API 29) blockiert Scoped Storage den Zugriff auf fremde Verzeichnisse zusätzlich. Die WebView-Einstellung hebt also keine Dateisystem-Berechtigungen des Betriebssystems auf.

---

## Teil 2: Zugriff auf JavaScript Interface (3P)

### Was muss wo eingegeben werden?

Die App enthält eine Suchfunktion auf der Guide-Seite. Die Methode `searchTerm` baut den Suchbegriff direkt und unvalidiert in die URL ein:

```java
guideWebView.loadUrl("https://hitchhacker.space/?search=" + term);
```

Der Suchbegriff wird also ohne Filterung oder Encoding an die URL angehängt. Um auf das JavaScript Interface `Wormhole` zuzugreifen, wird im Suchfeld der Guide-Seite folgender Wert eingegeben:

```
javascript:alert(window.Wormhole.showSecretMessage())
```

Alternativ, falls `javascript:`-URLs gefiltert werden, kann über die externe Webseite `hitchhacker.space` JavaScript injiziert werden, da der Suchparameter unescaped in die Seite eingebaut wird:

```
<script>alert(window.Wormhole.showSecretMessage())</script>
```

Die Methode `showSecretMessage()` ist in der Klasse `space.hitchhacker.guide.comm.Interface` definiert und mit `@JavascriptInterface` annotiert. Sie gibt den String `"Would it save you a lot of time if I just gave up and went mad now?"` zurück.

### Warum funktioniert der Zugriff? (Schwachstelle)

Der Zugriff funktioniert, weil drei Schwachstellen zusammenwirken:

**Schwachstelle 1 – Unvalidierter User-Input in der URL (Klasse `GuideFragment`, Methode `searchTerm`):**
Der Suchbegriff des Benutzers wird direkt per String-Konkatenation in die `loadUrl`-Methode eingebaut (`"https://hitchhacker.space/?search=" + term`). Es gibt kein URL-Encoding, keine Eingabevalidierung und keine Filterung von Sonderzeichen oder `javascript:`-URLs. Dadurch kann ein Angreifer beliebigen JavaScript-Code einschleusen.

**Schwachstelle 2 – JavaScript Interface global exponiert (Klasse `GuideFragment`, Methode `onViewCreated`):**
Durch `webView.addJavascriptInterface(new Interface(), "Wormhole")` wird das Java-Objekt `Interface` unter dem Namen `Wormhole` an den WebView gebunden. Dieses Interface ist nicht auf eine bestimmte URL oder Origin beschränkt – es ist von jeder Seite aufrufbar, die im WebView geladen wird, egal ob es die eigentliche App-Seite, eine externe Seite oder injizierter Code ist.

**Schwachstelle 3 – JavaScript aktiviert (Klasse `GuideFragment`, Methode `onViewCreated`):**
Durch `setJavaScriptEnabled(true)` kann der eingeschleuste JavaScript-Code tatsächlich ausgeführt werden und auf `window.Wormhole` zugreifen.

Die Kombination dieser drei Schwachstellen ermöglicht es, über das Suchfeld beliebigen JavaScript-Code auszuführen, der dann vollen Zugriff auf alle `@JavascriptInterface`-Methoden des `Wormhole`-Objekts hat – in diesem Fall `showSecretMessage()`, definiert in der Klasse `space.hitchhacker.guide.comm.Interface`.
