# MeiLists

Android-App für Einkaufs- und Aufgabenlisten mit Kotlin und Jetpack Compose.
Aktueller Stand: **0.2.6**, `versionCode 11`; Paket `de.haberland.meilists`.

## Funktionen

- Kategorien mit mehreren Listen, Farben und eigenen Anzeigeeinstellungen.
- Lokale Speicherung in Room; optional Firebase-Kategorien mit Google-Anmeldung und Live-Synchronisation.
- Geteilten Kategorien per Einladungscode beitreten.
- Einträge hinzufügen, bearbeiten, abhaken, verschieben und löschen.
- Produkt- und Bereichskatalog, optionale Lernfunktion und Sortierung nach Bereichen.
- Google-Play-In-App-Updates und Firebase Crashlytics.

MeiHome kann auf dieselben Firebase-Listen zugreifen. Rein lokale Kategorien sind dort nicht verfügbar.

## Entwicklung und Build

Benötigt werden JDK 17, Android SDK 37 und ein Gerät/Emulator ab Android 8 (API 26).
Gradle wird über den mitgelieferten Wrapper ausgeführt:

```bash
git clone https://github.com/pehab/MeiLists.git
cd MeiLists
bash gradlew :app:testDebugUnitTest :app:assembleDebug
bash gradlew :app:lintDebug
```

Android Studio kann das Projekt direkt öffnen. Den SDK-Pfad gegebenenfalls in der nicht versionierten `local.properties` als `sdk.dir` setzen.

## Firebase und Anmeldung

`app/google-services.json` muss zum Paket und zum gewünschten Firebase-Projekt passen. Für ein eigenes Backend Google-Anmeldung und Firestore konfigurieren; die passenden Zugriffsregeln müssen im Backend vorhanden sein. Dieses Repository enthält keine deploybaren Firestore-Regeln.

Für Google-Anmeldung müssen die Zertifikat-Fingerprints der tatsächlich installierten Builds in Firebase registriert sein. Lokale Debug-, Upload- und Google-Play-App-Signing-Zertifikate können unterschiedlich sein. Nach Änderungen die Firebase-Konfiguration erneut herunterladen.

Optional kann eine konstante Signatur in `local.properties` konfiguriert werden:

```properties
meilists.signing.storeFile=/absoluter/pfad/meilists.jks
meilists.signing.storePassword=...
meilists.signing.keyAlias=...
meilists.signing.keyPassword=...
```

Ohne vollständige Konfiguration verwendet Debug die normale Android-Debug-Signatur. Keystores und Passwörter gehören nicht ins Repository.

## Struktur und Prüfungen

- `model/`: Room-Datenbank, DAOs, Entities und Datenmodelle.
- `domain/`: ausgelagerte Anzeige- und Sortierlogik.
- `ui/`: Compose-Screens, Komponenten und Dialoge.
- `MainViewModel.kt`: UI-Zustand, Authentifizierung, Synchronisation und Listenoperationen.
- `app/src/test/`: JVM-Tests für Modelle, Anzeigelogik und Schema-Prüfungen.
- `app/src/androidTest/`: Geräte-/Emulatortests für Migrationen, DAOs und UI.

GitHub Actions führt Unit-Tests und den Debug-Build aus. Geräte-/Emulatortests werden dort derzeit nicht ausgeführt; lokal mit angeschlossenem Gerät:

```bash
bash gradlew :app:connectedDebugAndroidTest
```

Die Datenbank hat Version 13, mit expliziten Migrationen 11 → 12 → 13. Für andere nicht unterstützte Versionspfade ist ein destruktiver Fallback konfiguriert: lokale Daten können dabei verloren gehen.

## Technische nächste Schritte

Das große `MainViewModel` schrittweise in Authentifizierungs-, Synchronisations- und Listen-Repositories aufteilen. Vorher das Verhalten bei Abmeldung, Kontowechsel, entzogenen Freigaben und Netzwerkfehlern absichern. Listener-Fehler werden teilweise noch still ignoriert. Neue Datenbankversionen benötigen überprüfte Migrationen und aktualisierte Schema-Dateien.

## Datenschutz

Siehe [PRIVACY_POLICY.md](PRIVACY_POLICY.md).
