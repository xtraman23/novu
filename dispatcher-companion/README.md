# RingCentral Dispatcher Companion (Android)

Real-time AI copilot for truck dispatchers on RingCentral calls: live
transcription, automatic PDWCR extraction (Pickup / Delivery / Weight /
Commodity / Rate), negotiation suggestions, broker memory, and automatic call
summaries. Optimized for the Xiaomi Redmi 14C (HyperOS).

## Module map

| Module | Kind | Tests |
|---|---|---|
| `core:model` | pure Kotlin domain + PDWCR merge rules | 7 |
| `core:db` | SQLCipher-encrypted SQLite (validated Phase 3 schema) | 1 (+`docs/validate_schema.py`, 7 checks) |
| `audio` | capture-method ladder, silence watchdog, mic capture | 9 |
| `asr` | VAD, diarization, transcript assembly, ASR abstraction | 10 |
| `engine:extraction` | tier-1 regex/gazetteer PDWCR extractor | 13 |
| `engine:negotiation` | cue detection + counter/floor/ceiling/accept% | 8 |
| `engine:calculator` | lane miles/RPM/fuel/profit + ZIP/city lookup | 8 |
| `ai` | LLM provider abstraction (Claude official SDK), prompts, parsers | 12 |
| `broker` | broker rollups, negotiation-style classification | 7 |
| `app` | Compose UI (Concept B), services, overlay, wizard, export | 8 |

**83 distinct unit tests, all green** (101 executions across debug/release variants).

## Build

Requirements: JDK 17+, Android SDK (platform 35, build-tools 35), Gradle 8.14+.

```bash
cd dispatcher-companion
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle test :audio:testDebugUnitTest :core:db:testDebugUnitTest :app:testDebugUnitTest  # full suite
gradle :app:assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleRelease    # → app-release-unsigned.apk (sign before distribution)
```

Sign the release APK:

```bash
keytool -genkeypair -keystore release.jks -alias dispatcher -keyalg RSA -validity 9125
$ANDROID_HOME/build-tools/35.0.0/apksigner sign --ks release.jks \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

## Deploy to the Redmi 14C

1. `adb install app-debug.apk` (or sideload via file share).
2. Open the app → **Setup** tab → grant each item; the **Autostart** and
   **Battery: No restrictions** steps deep-link into HyperOS Security Center.
3. Grant **Notification access** so RingCentral calls auto-start dispatch mode.
4. During calls, use **speakerphone** — that is how both call sides reach the
   transcriber (Android blocks direct VoIP-audio capture for third-party apps;
   see `docs/PHASE-1-REQUIREMENTS.md` §3.2).
5. Optional: add a Claude API key in code (`ClaudeProvider`) to enable tier-2
   LLM extraction/summaries; everything else works fully offline.

## Legal note

Call-recording/transcription consent law varies by state. The app defaults to
transcript-only mode (no audio retention) and the operator is responsible for
obtaining any required consent.

## Docs

`docs/PHASE-1-REQUIREMENTS.md` · `PHASE-2-ARCHITECTURE.md` ·
`PHASE-3-DATABASE.md` (+ `validate_schema.py`) · `PHASE-4-UI-SPEC.md` ·
`docs/ui-concepts/` (5 concepts, Concept B approved) ·
`PHASE-10-12-REPORT.md` (testing, optimization, release).
