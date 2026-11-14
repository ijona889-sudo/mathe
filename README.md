# Rechenzauber – Mathegenerator für Android

Der Mathegenerator 0.1.44 läuft offline ab Android 8. Die App startet die mitgelieferte HTML-Oberfläche in der Android-WebView. Gespeicherte Arbeitsblätter bleiben im App-Speicher. Der PDF-Button öffnet die Android-Dateiauswahl und „Drucken“ öffnet den Systemdruckdialog.

## APK installieren

Öffne die signierte Release-APK auf dem Android-Gerät. Android kann beim ersten Öffnen verlangen, dass du der verwendeten App (etwa der Dateien-App) die Installation aus dieser Quelle erlaubst.

## Projekt lokal bauen

Voraussetzungen sind JDK 17, Android SDK Platform 36 und Build Tools 36.0.0. Setze `ANDROID_HOME` auf den SDK-Ordner oder öffne das Projekt in Android Studio.

Unter Windows in PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintRelease
```

Unter Linux oder macOS:

```sh
bash gradlew :app:assembleDebug :app:lintRelease
```

Die Test-APK liegt anschließend unter `app/build/outputs/apk/debug/app-debug.apk`.

## Auf GitHub verwenden

Entpacke das ZIP und pushe den Inhalt des Ordners `Rechenzauber-Android` als Repository. Der enthaltene Workflow baut bei Pushes und Pull Requests eine Test-APK. Du findest sie in **Actions → Android APK → Artifacts**. Die separat mitgelieferte Release-APK kannst du direkt als Datei an einen GitHub-Release hängen.

Für automatische Release-Builds richte unter **Settings → Secrets and variables → Actions** diese Repository-Secrets ein:

| Secret | Wert |
| --- | --- |
| `RECHENZAUBER_KEYSTORE_BASE64` | Base64-kodierte PKCS#12-Schlüsseldatei |
| `RECHENZAUBER_KEYSTORE_PASSWORD` | Passwort der Schlüsseldatei |
| `RECHENZAUBER_KEY_ALIAS` | Alias des Schlüssels (`rechenzauber` für die mitgelieferte APK) |
| `RECHENZAUBER_KEY_PASSWORD` | Schlüsselpasswort, bei der mitgelieferten APK identisch mit dem Passwort der Schlüsseldatei |

Wenn du den Release `v0.1.44` veröffentlichst, hängt der Workflow APK und SHA-256-Prüfsumme an. Für künftige Releases erhöhe `versionCode` und passe `versionName` und den Tag in demselben Commit an. Bewahre die private Schlüsseldatei und ihr Passwort sicher auf. Android-Updates erfordern dieselbe Signatur wie die bereits installierte APK.

Für einen lokal signierten Release setze dieselben Passwort- und Aliaswerte als Umgebungsvariablen. Setze statt `RECHENZAUBER_KEYSTORE_BASE64` die Variable `RECHENZAUBER_KEYSTORE` auf den absoluten Pfad zur Schlüsseldatei und baue mit `:app:assembleRelease`. Die signierte APK liegt dann unter `app/build/outputs/apk/release/app-release.apk`.
