# Lagebild – seriöse Nachrichten, Warnungen und Verkehr für Berlin

Android-App (Kotlin, Jetpack Compose, Material 3), die deutsche und internationale Qualitätsquellen bündelt,
fremdsprachige Überschriften automatisch ins Deutsche übersetzt und Gefahrenmeldungen sowie Verkehrsstörungen
für Berlin anzeigt. Updates kommen direkt über GitHub Releases – ohne Play Store.

---

## Inhalt

1. [Was die App kann](#was-die-app-kann)
2. [Quellen und Nutzungsbedingungen](#quellen-und-nutzungsbedingungen)
3. [Einmalige Einrichtung](#einmalige-einrichtung)
4. [Erste Installation auf dem Handy](#erste-installation-auf-dem-handy)
5. [Ablauf für spätere Updates](#ablauf-für-spätere-updates)
6. [Keystore sicher aufbewahren](#keystore-sicher-aufbewahren)
7. [Quellen ändern ohne App-Update](#quellen-ändern-ohne-app-update)
8. [Lokal bauen und testen](#lokal-bauen-und-testen)
9. [Aufbau des Projekts](#aufbau-des-projekts)
10. [Fehlerbehebung](#fehlerbehebung)

---

## Was die App kann

- **Start:** Top-Meldungen aller Quellen, darunter Chips für Politik, International, Deutschland, Berlin,
  Wissenschaft, Wirtschaft, Sport und Technologie. Meldungen zum selben Thema aus verschiedenen Quellen
  werden zusammengefasst („+2 weitere Quellen“).
- **Berlin:** Warnungen (NINA/BBK und Deutscher Wetterdienst, farbig nach Warnstufe), Störungen auf deinen
  Lieblingslinien (BVG/S-Bahn/VBB) und Nachrichten von rbb24 und taz Berlin.
- **Suche, Lesezeichen, Teilen**, Pull-to-Refresh, Offline-Modus über den lokalen Cache.
- **Übersetzung** von Überschrift und Teaser (Guardian, BBC, Al Jazeera, NPR) mit Google ML Kit
  direkt auf dem Gerät – optional DeepL. Ein Button zeigt jederzeit das Original.
- Artikel öffnen sich **immer beim Original-Anbieter** (Custom Tab). Die App kopiert keine Artikeltexte.
- **Benachrichtigungen** (eigene Kanäle): Breaking News, Gefahrenmeldungen, Verkehr, App-Updates.
  Ruhezeiten einstellbar; Warnungen der Stufen „Unwetter“ und „extrem“ kommen immer durch.
- **In-App-Updates** mit Changelog, Fortschrittsanzeige und SHA-256-Prüfung.

## Quellen und Nutzungsbedingungen

Alle Feeds wurden am 25.11.2026 abgerufen und mit dem Parser der App getestet.

**Nur frei lesbare Quellen:** Die App enthält nur Anbieter, deren Artikel ohne Abo lesbar sind. Geprüft wurde das
über die Kennzeichnung `isAccessibleForFree`, die Nachrichtenseiten für Suchmaschinen in jeden Artikel schreiben.
Entfernt wurden deshalb Spiegel (11 von 12 Stichproben hinter Bezahlschranke), SZ (8/12), Handelsblatt (8/12),
Spektrum (6/12), FAZ und Tagesspiegel (je 2/12, im Feed nicht erkennbar), Zeit (Z+), FT, Economist und Nature
(Fachartikel kostenpflichtig). heise wurde wegen heise+ nicht aufgenommen, Golem.de wegen Werbung („Anzeige:“) im Feed.

| Quelle | Zugang | Status | Hinweis |
|---|---|---|---|
| tagesschau | offizielle API (`tagesschau.de/api2u`) | ✅ | Liefert ein Eilmeldungs-Kennzeichen. Laut Doku nur für private, nicht-kommerzielle Nutzung. |
| Deutschlandfunk | RSS | ✅ | |
| ZDFheute | RSS | ✅ | ein Gesamt-Feed |
| rbb24 | RSS | ✅ | Berlin-Nachrichten, ohne Bilder im Feed |
| Deutsche Welle | RSS | ✅ | |
| n-tv | RSS | ✅ | Nachrichtensender, komplett frei |
| taz | RSS | ✅ | frei lesbar (freiwilliges Bezahlmodell), auch Berlin-Ressort |
| The Guardian | RSS | ✅ | frei lesbar (Spendenmodell), kein API-Key nötig |
| BBC News | RSS | ✅ | |
| Al Jazeera English | RSS | ✅ | |
| NPR | RSS | ✅ | US-Nachrichten |
| Quarks (WDR) | RSS | ✅ | |
| scinexx | RSS | ✅ | Wissenschaftsmagazin |
| netzpolitik.org | RSS | ✅ | Technik und Digitalpolitik |
| **Reuters** | – | ❌ weggelassen | Reuters hat seine öffentlichen RSS-Feeds 2020 abgeschaltet. Es gibt nur kostenpflichtige Lizenzen. |
| **AP** | – | ❌ weggelassen | AP bietet keinen offiziellen offenen Feed bzw. keine offene API an, nur kostenpflichtige Lizenzen. Inoffizielle Feeds oder Scraping nutze ich bewusst nicht. |
| NINA (BBK) | offene API `warnung.bund.de/api31` | ✅ | Regionalschlüssel Berlin `110000000000` |
| Deutscher Wetterdienst | offene Warn-JSON (`dwd.de/.../warnings.json`) | ✅ | Warnzellen Berlin `111…` |
| BVG/S-Bahn/VBB | `v6.vbb.transport.rest` | ✅ | Freier Community-Dienst mit VBB-Daten (Fair Use, ca. 100 Anfragen/Min.). Eine offizielle offene „Alle Störungen“-API gibt es nicht. Deshalb fragt die App die Abfahrten deiner Haltestellen ab und wertet Störungshinweise, Ausfälle und Verspätungen ab 10 Minuten aus. |
| VIZ Berlin (Straßenverkehr) | `api.viz.berlin.de` | ❌ weggelassen | Die Datei ist erreichbar, wurde aber **seit Juli 2025 nicht mehr aktualisiert**. Veraltete Sperrungen wären irreführend. Kommt eine aktuelle offene Quelle, lässt sie sich nachrüsten. |

## Einmalige Einrichtung

Du brauchst: einen GitHub-Account, Git und Android Studio (oder die Kommandozeilen-Tools, die bereits auf
deinem PC installiert sind – siehe [Lokal bauen](#lokal-bauen-und-testen)).

### 1. Repository anlegen

1. Lege auf github.com ein **öffentliches** Repository an, z. B. `lagebild`.
   *Öffentlich ist nötig, damit die App ohne Zugangsdaten Updates und die Quellenliste laden kann.
   Deine API-Keys und der Keystore landen trotzdem nie im Repository.*
2. Trage in [`gradle.properties`](gradle.properties) deinen Repository-Namen ein:
   ```properties
   GITHUB_REPO=dein-github-name/lagebild
   ```
3. Projekt hochladen (PowerShell im Ordner `Lagebild`):
   ```powershell
   git init
   ```
   ```powershell
   git add .
   ```
   ```powershell
   git commit -m "Erste Version"
   ```
   ```powershell
   git branch -M main
   ```
   ```powershell
   git remote add origin https://github.com/dein-github-name/lagebild.git
   ```
   ```powershell
   git push -u origin main
   ```
   Die [`.gitignore`](.gitignore) verhindert, dass `local.properties`, `keystore.properties` und `*.jks` hochgeladen werden.

### 2. API-Keys eintragen

**Guardian:** braucht keinen Key mehr – die App nutzt die öffentlichen RSS-Feeds.

**DeepL (optional):** Auf <https://www.deepl.com/pro-api> „DeepL API Free“ wählen (500.000 Zeichen/Monat gratis).
Ohne DeepL übersetzt die App mit Google ML Kit.

Für lokale Builds trägst du die Keys in `local.properties` ein (Vorlage: [`local.properties.example`](local.properties.example)):
```properties
DEEPL_API_KEY=dein-deepl-key:fx
```
Für die GitHub Action legst du sie als **Secrets** an (siehe Schritt 4).

### 3. Release-Keystore anlegen (einmalig!)

Der Keystore ist dein digitaler Signaturschlüssel. **Alle Versionen müssen damit signiert sein**, sonst
verweigert Android das Update. Dann müsstest du die App deinstallieren und deine Lesezeichen gingen verloren.

PowerShell (`keytool` gehört zum JDK):
```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\Lagebild-Schluessel"
```
```powershell
& "C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot\bin\keytool.exe" -genkeypair -v -keystore "$env:USERPROFILE\Lagebild-Schluessel\lagebild-release.jks" -alias lagebild -keyalg RSA -keysize 4096 -validity 36500
```
`keytool` fragt nach einem Passwort (gut merken bzw. im Passwortmanager speichern) und nach Namen/Ort.
Die Angaben dort sind frei wählbar.

### 4. GitHub-Secrets anlegen

Im Repository: **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Inhalt |
|---|---|
| `KEYSTORE_BASE64` | Keystore als Base64-Text (Befehl unten) |
| `SIGNING_STORE_PASSWORD` | Passwort des Keystores |
| `SIGNING_KEY_ALIAS` | `lagebild` |
| `SIGNING_KEY_PASSWORD` | Passwort des Schlüssels (meist dasselbe) |
| `DEEPL_API_KEY` | optional, dein DeepL-Key |

Keystore als Base64 in die Zwischenablage kopieren:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\Lagebild-Schluessel\lagebild-release.jks")) | Set-Clipboard
```
Danach den Inhalt der Zwischenablage als Wert für `KEYSTORE_BASE64` einfügen.

## Erste Installation auf dem Handy

1. Ersten Release auslösen (Version steht schon auf `1.0.0`):
   ```powershell
   git tag v1.0.0
   ```
   ```powershell
   git push origin v1.0.0
   ```
2. Unter **Actions** im Repository siehst du den Build (ca. 5–8 Minuten). Danach liegt unter **Releases**
   `lagebild-1.0.0.apk` bereit.
3. Öffne die Release-Seite **auf dem Handy** im Browser und lade die APK herunter.
4. Tippe auf die Datei. Android fragt, ob der Browser „unbekannte Apps installieren“ darf. Erlaube es einmalig
   und installiere.
5. Beim ersten Start fragt die App nach der Erlaubnis für Benachrichtigungen (Android 13+).
6. Unter **Einstellungen → Berlin · Verkehr** deine Haltestellen und Lieblingslinien eintragen.

Ab jetzt brauchst du nie wieder eine APK per Hand.

## Ablauf für spätere Updates

```
Feature mit Claude Code einbauen → Version erhöhen → Tag pushen → in der App auf „Update“ tippen
```

1. **Feature einbauen**, z. B. mit Claude Code. Danach lokal testen:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
2. **Version erhöhen** in [`gradle.properties`](gradle.properties). Beide Werte müssen steigen:
   ```properties
   VERSION_CODE=2
   VERSION_NAME=1.1.0
   ```
   Die Änderungsnotizen für den Update-Dialog in [`CHANGELOG_RELEASE.md`](CHANGELOG_RELEASE.md) schreiben.
3. **Committen, taggen, pushen.** Der Tag muss genau `v` + `VERSION_NAME` sein, sonst bricht die Action ab:
   ```powershell
   git add .
   ```
   ```powershell
   git commit -m "Version 1.1.0"
   ```
   ```powershell
   git push
   ```
   ```powershell
   git tag v1.1.0
   ```
   ```powershell
   git push origin v1.1.0
   ```
4. Die GitHub Action testet, baut, signiert und veröffentlicht die APK samt `update.json`
   (enthält `versionCode` und SHA-256-Prüfsumme).
5. **In der App:** Beim nächsten Start erscheint der Update-Hinweis. Alternativ
   **Einstellungen → Nach Updates suchen**. Zusätzlich prüft die App alle 12 Stunden im Hintergrund und meldet
   sich per Benachrichtigung. Tippe auf **Herunterladen & installieren**.
6. **Beim allerersten In-App-Update** erklärt die App, wie du „Unbekannte Apps installieren“ für Lagebild
   erlaubst: Einstellungen öffnen → Schalter aktivieren → zurück. Danach startet die Installation automatisch.

Deine Daten (Lesezeichen, Einstellungen, Cache) bleiben bei Updates erhalten. Ändert sich das
Datenbankschema, braucht es eine Room-Migration (siehe Kommentar in `AppDatabase.kt`). Claude Code weiß das,
wenn du es darauf hinweist.

## Keystore sicher aufbewahren

- **Nie ins Repository.** `.gitignore` schließt `*.jks` und `keystore.properties` aus.
- **Mindestens zwei Sicherungen** an getrennten Orten, z. B. USB-Stick im Schrank **und** ein verschlüsselter
  Cloud-Speicher oder das Datei-Anhang-Feld deines Passwortmanagers (Bitwarden, 1Password, KeePassXC).
- **Passwörter getrennt** vom Keystore im Passwortmanager speichern.
- Auf GitHub liegt der Keystore nur als verschlüsseltes Secret. Dort kannst du ihn **nicht** wieder herunterladen.
  Die GitHub-Kopie ersetzt also keine eigene Sicherung.
- Geht der Keystore verloren: neuen anlegen, Secrets ersetzen, App einmal deinstallieren und neu installieren.
  Dabei gehen die lokalen Daten verloren.

## Quellen ändern ohne App-Update

Die Quellenliste liegt in [`config/sources.json`](config/sources.json). Die App lädt sie bei jedem Start von
`raw.githubusercontent.com/<GITHUB_REPO>/main/config/sources.json`. Offline nutzt sie die zuletzt geladene Kopie,
beim allerersten Start die eingebaute Kopie.

Eine Quelle hinzufügen:
```json
{
  "id": "beispiel", "name": "Beispiel-Zeitung", "type": "rss", "language": "de",
  "group": "Zeitungen", "color": "#123456", "homepage": "https://example.de",
  "feeds": [
    { "url": "https://example.de/rss", "top": true },
    { "url": "https://example.de/politik/rss", "category": "POLITIK" }
  ]
}
```
- `type`: `rss` (auch Atom), `tagesschau` oder `guardian`
- `language`: alles außer `de` wird übersetzt
- `category`: `POLITIK`, `INTERNATIONAL`, `NATIONAL`, `BERLIN`, `WISSENSCHAFT`, `WIRTSCHAFT`, `SPORT`, `TECHNOLOGIE`
- `top: true`: Feed liefert die wichtigsten Meldungen (Startseite)
- optional: `"enabledByDefault": false`, `"paywall": true`

Nach dem Bearbeiten alle Feeds gegen das Internet prüfen:
```powershell
$env:LIVE_FEEDS = "1"; .\gradlew.bat testDebugUnitTest --tests "*LiveFeedsCheck*"; Remove-Item Env:LIVE_FEEDS
```
Das Ergebnis steht in `app/build/live-feeds.txt` und `app/build/live-apis.txt`.
Danach committen und pushen. Die App übernimmt die Änderung beim nächsten Start.

## Lokal bauen und testen

Auf deinem PC sind bereits installiert: JDK 17 (Temurin) unter `C:\Program Files\Eclipse Adoptium\` und das
Android SDK unter `%LOCALAPPDATA%\Android\Sdk`. Vor dem Bauen in PowerShell einmal JAVA_HOME setzen:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot"
```
Unit-Tests:
```powershell
.\gradlew.bat testDebugUnitTest
```
Debug-APK zum Ausprobieren (eigene App-ID `de.lagebild.app.debug`, kann parallel installiert sein):
```powershell
.\gradlew.bat assembleDebug
```
Signierte Release-APK lokal (braucht `keystore.properties`, Vorlage: [`keystore.properties.example`](keystore.properties.example)):
```powershell
.\gradlew.bat assembleRelease
```
Mit Android Studio: Ordner `Lagebild` öffnen, Gradle-Sync abwarten, oben „app“ wählen und ▶ drücken.

## Aufbau des Projekts

MVVM + Clean Architecture, Hilt für Dependency Injection.

```
app/src/main/java/de/lagebild/app/
├── domain/            Modelle, Themen-Gruppierung (StoryGrouper), Auswahl der Top-Meldungen
├── data/
│   ├── remote/        HTTP, Retrofit-APIs (NINA, VBB, GitHub), Parser (RSS/Atom, tagesschau, Guardian, DWD)
│   ├── local/         Room: Artikel-Cache, Lesezeichen, Gelesen, Übersetzungen, Warnungen
│   ├── sources/       Laden der sources.json (GitHub → Cache → eingebaut)
│   ├── translation/   ML Kit, DeepL, Übersetzungs-Cache
│   ├── settings/      Einstellungen (DataStore)
│   └── repository/    News-, Warn- und Verkehrs-Repository
├── notifications/     Kanäle, Ruhezeiten
├── work/              WorkManager: Hintergrund-Abgleich, Update-Prüfung
├── update/            Update-Prüfung, Download, SHA-256, Installation
└── ui/                Compose-Oberfläche (Start, Berlin, Suche, Lesezeichen, Einstellungen, Update-Dialog)
```

Wichtige Entscheidungen, kurz erklärt:
- **Eigener RSS-Parser (SAX)** statt Bibliothek: klein, schnell, funktioniert in Unit-Tests ohne Emulator und
  ist gegen XXE-Angriffe abgesichert.
- **Themen-Gruppierung lokal** über Wortähnlichkeit der (übersetzten) Überschriften. Kein Server nötig.
  Wie streng gruppiert wird, stellt `MIN_SIMILARITY` in `StoryGrouper.kt` ein.
- **„Breaking News“**: tagesschau liefert ein offizielles Eilmeldungs-Flag. Zusätzlich meldet die App ein Thema,
  wenn mindestens drei Quellen gleichzeitig darüber berichten (abschaltbar).
- **Übersetzungs-Cache**: Derselbe Text wird nie zweimal übersetzt. Das spart DeepL-Kontingent und Akku.
- **Update-Sicherheit**: HTTPS-Download von GitHub, SHA-256-Vergleich mit `update.json`, und Android prüft die
  Signatur. Eine fremde APK kann deine App daher nicht ersetzen.
- **Nur ARM-Prozessoren** in der Release-APK (ca. 30 MB statt 70 MB). Handys sind praktisch immer ARM.

## Fehlerbehebung

| Problem | Lösung |
|---|---|
| „GITHUB_REPO ist noch nicht eingetragen“ | `gradle.properties` anpassen, neue Version bauen |
| „Noch kein Release gefunden“ | Tag gepusht? Ist die Action unter „Actions“ grün? |
| Action: „Tag passt nicht zu VERSION_NAME“ | Tag `v1.2.0` ↔ `VERSION_NAME=1.2.0` angleichen |
| Action: „Secret KEYSTORE_BASE64 fehlt“ | Schritt 4 der Einrichtung |
| Android: „App nicht installiert“ beim Update | APK mit anderem Keystore signiert, oder `VERSION_CODE` nicht erhöht |
| Keine Übersetzung | Beim ersten Mal lädt ML Kit ein Sprachmodell (~30 MB, standardmäßig nur im WLAN) |
| Keine Benachrichtigungen | Android-Einstellungen → Apps → Lagebild → Benachrichtigungen; bei Xiaomi/Huawei/Samsung zusätzlich den Akku-Optimierer für Lagebild ausschalten |
