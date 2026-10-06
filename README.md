# MeiLists

Android-App für Einkaufs- und Aufgabenlisten mit Kotlin und Jetpack Compose.
Aktueller Stand: **0.2.10**, `versionCode 15`; Paket `de.haberland.meilists`.

## Funktionen

- Kategorien mit mehreren Listen, Farben und eigenen Anzeigeeinstellungen.
- Zuletzt geöffnete Kategorie und Liste werden auf dem Gerät wiederhergestellt.
- Listenreihenfolge pro Gerät und Kategorie über das Listenmenü anpassbar.
- Katalogprodukte und Bereiche alphabetisch nach deutscher Sortierung.
- Lokale Speicherung in Room; optional Firebase-Kategorien mit Google-Anmeldung und Live-Synchronisation.
- Geteilten Kategorien per Einladungscode beitreten.
- Einträge hinzufügen, bearbeiten, abhaken, verschieben und löschen.
- Einträge optional alle 1–3650 Tage ab dem Abhaken wieder öffnen, ohne Duplikate oder Benachrichtigungen. Wiederholung beim Anlegen/Bearbeiten einstellbar; „Erledigte löschen“ behält wiederkehrende Einträge.
- Fällige Wiederholungen werden beim Öffnen und während der Nutzung als offen angezeigt. Intervall und Fälligkeit werden bei Firebase-Listen geteilt. Ein Tag entspricht 24 Stunden. Bei geändertem Intervall eines noch erledigten Eintrags beginnt die Wartezeit beim Speichern neu.
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

GitHub Actions führt Unit-Tests und den Debug-Build aus. Die Datenbank-Migrationstests laufen zusätzlich in CI auf einem Android-35-Emulator. Weitere Geräte-/UI-Tests lokal mit angeschlossenem Gerät:

```bash
bash gradlew :app:connectedDebugAndroidTest
```

Die Datenbank ist auf Version 14. Datenbankversionen und App-Versionen sind unabhängig. Für alle in Git belegten älteren Schemata gibt es jetzt den Pfad 2 → 3 → 10 → 11 → 12 → 13 → 14. Die Tests prüfen Kategorien, Freigaben, Listen, abgehakte/offene Einträge, Zeitstempel, Bereiche, Katalogprodukte und deaktiviertes Autolernen. Alte Listen ohne Zeitstempel erhalten 0; neue Felder erhalten fachlich passende Standardwerte.

Der automatische destruktive Fallback ist entfernt. Unbekannte Versionen und Downgrades werden beim Öffnen abgewiesen, ohne den Datenbestand zu löschen. Das ist kein automatischer Reparaturmodus: Bei einem solchen Fehler die App-Daten nicht löschen und die App nicht deinstallieren; eine passende Migration muss ergänzt werden. Bereits durch frühere App-Versionen gelöschte Daten lassen sich dadurch nicht wiederherstellen.

Schemaquellen der Migrationsprüfung: `5f449d1` (DB 2), `145c87b` (DB 3), `75f0149` (DB 10), `f1b9158` (DB 11), `c38d931`/`94769dc` (beide Varianten DB 12).

## Technische nächste Schritte

Das große `MainViewModel` schrittweise in Authentifizierungs-, Synchronisations- und Listen-Repositories aufteilen. Vorher das Verhalten bei Abmeldung, Kontowechsel, entzogenen Freigaben und Netzwerkfehlern absichern. Fehler beim Listen-/Einträge-Sync und beim lokalen Verarbeiten von Listener-Daten werden angezeigt. Kontowechsel beendet alte Listener und ausstehende Synchronisationsjobs; verspätete Antworten werden verworfen. Cloud-Kategorien und ihre Listen/Einträge werden nur für das angemeldete Mitglied angezeigt; lokale Kategorien bleiben ohne Anmeldung verfügbar. Der Cache wird beim Kontowechsel nicht gelöscht. JVM-Tests prüfen diese Abbruch-, Sichtbarkeits- und Fehlerfälle. Neue Datenbankversionen benötigen überprüfte Migrationen und aktualisierte Schema-Dateien.

## Datenschutz

Siehe [PRIVACY_POLICY.md](PRIVACY_POLICY.md).
