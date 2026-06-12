# RingCentral Dispatcher Companion — Phase 2: System Architecture

**Selected UI concept:** B — Freight Dispatcher Pro (navy `#1B2A4A` / orange `#F97316`)
**Toolchain (validated in CI container):** JDK 21 · Gradle 8.14 · AGP 8.10 · Kotlin 2.0 · minSdk 29 · target/compileSdk 35

## 1. Module Map (Gradle multi-module)

```
dispatcher-companion/app/                      Android application (Compose UI, services, DI graph)
├── core/model                # Pure Kotlin. Domain types: LoadDetails, PdwcrField,
│                             # TranscriptSegment, BrokerProfile, CallSummary, NegotiationAdvice
├── core/db                   # Android lib. Room entities/DAOs, SQLCipher, FTS, backup
├── engine/extraction         # Pure Kotlin. Regex/gazetteer PDWCR extractor (offline tier-1)
├── engine/negotiation        # Pure Kotlin. Cue detection, counter/floor/ceiling heuristics
├── engine/calculator         # Pure Kotlin. Miles/RPM/fuel/profit + ZIP/city geo lookups
├── audio/                    # Android lib. Capture-method ladder, probe, foreground service
├── asr/                      # Android lib. SpeechToText interface; Vosk/cloud impls; diarization
├── ai/                       # Kotlin lib. LlmProvider interface; Claude/OpenAI impls; prompt + JSON schema
└── broker/                   # Android lib. Broker memory repository, search, scoring
```

Dependency rule (enforced by Gradle): `app → everything`; engines depend only on
`core/model`; `core/db` depends on `core/model`; **no module depends on `app`**.
Pure-Kotlin modules run their test suites on the JVM in < 5 s — this is what
makes the per-module test gates cheap.

## 2. Runtime Topology

```
┌────────────────────────────── Android process ──────────────────────────────┐
│                                                                              │
│  CallDetectionController                                                     │
│   ├─ RcNotificationListener (NotificationListenerService)                    │
│   ├─ RcAccessibilityService (AccessibilityService; foreground pkg + UI text) │
│   └─ AudioModeWatcher (AudioManager MODE_IN_COMMUNICATION)                   │
│            │  callStarted(callMeta) / callEnded()                            │
│            ▼                                                                 │
│  DispatchSessionService (foregroundServiceType=microphone, START_STICKY)     │
│   ├─ CaptureMethodLadder ─ probes M1..M5 → ActiveCapture (AudioRecord)       │
│   ├─ AsrPipeline: PCM 16k mono ─► SpeechToText (cloud-stream | vosk-local)   │
│   │       └─► TranscriptSegment(speaker, text, t0, t1) → Room (incremental)  │
│   ├─ ExtractionPipeline: tier-1 regex (every segment, <10 ms)                │
│   │                      tier-2 LLM refine (debounced 6 s, online only)      │
│   │       └─► PdwcrState (StateFlow) — monotonic merge, manual-edit sticky   │
│   ├─ NegotiationPipeline: cue detector + LLM advice (debounced on rate talk) │
│   └─ SummaryGenerator: on callEnded() → CallSummary + notes (4 styles)       │
│                                                                              │
│  OverlayController (WindowManager TYPE_APPLICATION_OVERLAY + ComposeView)    │
│   └─ renders PdwcrState / advice / last transcript line; drag, snap, expand  │
│                                                                              │
│  UI (single-activity Compose): Home ▸ LiveDashboard ▸ Summary ▸ BrokerMemory │
│                                ▸ Calculator ▸ History ▸ SetupWizard          │
│  WorkManager: deferred LLM jobs (offline queue), DB backup, broker rollups   │
└──────────────────────────────────────────────────────────────────────────────┘
```

All pipelines communicate via Kotlin `StateFlow`/`SharedFlow` owned by a
`DispatchSession` object scoped to the foreground service; UI and overlay are
pure observers, so process death never loses more than the debounce window
(segments are persisted as they arrive).

## 3. Audio Capture Ladder (Phase 5 contract)

```kotlin
interface CaptureMethod {
    val id: MethodId            // PLAYBACK_CAPTURE, VOIP_DIRECT, ACCESSIBILITY,
                                // MEDIA_PROJECTION, MICROPHONE
    suspend fun probe(): ProbeResult   // UNAVAILABLE | DENIED | SILENT | OK(quality)
    fun start(sink: PcmSink): ActiveCapture
}
```

`CaptureMethodLadder` probes in priority order at setup and at call start; the
first `OK` wins; quality maps to the user-facing **Excellent / Good / Limited /
Fallback** chip. A `SilenceWatchdog` (5 s RMS≈0 while call active) triggers
automatic failover down the ladder mid-call.

## 4. ASR & AI Provider Abstractions

```kotlin
interface SpeechToText {                      // asr/
    fun stream(pcm: Flow<PcmChunk>): Flow<AsrEvent>   // Partial | Final(segment)
    val latencyClass: LatencyClass; val worksOffline: Boolean
}
interface LlmProvider {                       // ai/
    suspend fun complete(req: LlmRequest): LlmResult  // JSON-schema-constrained
}
```

Registered implementations: `VoskSpeechToText` (offline, bundled small-en
model), `CloudStreamSpeechToText` (primary online); `ClaudeProvider`,
`OpenAiProvider`, `NullProvider` (offline queue). Selection is a runtime
strategy: best available given connectivity + user settings. Diarization:
channel-based when capture provides separated streams, else energy/turn-taking
heuristic + LLM speaker repair in tier-2.

## 5. Extraction: Two-Tier Design

- **Tier 1 (engine/extraction, offline, deterministic):** regex + gazetteer over
  a sliding transcript window — city/state pairs, ZIPs, `42k lbs`/`42,000 lbs`,
  `$1,900`/`nineteen hundred`, MC numbers, equipment terms (`dry van`, `reefer`,
  `flatbed`, `53'`), appointment times. Emits `(field, value, confidence, src)`.
- **Tier 2 (ai/, online, debounced):** full-window LLM pass returning the
  PDWCR+extended JSON schema; merged by `PdwcrMerger` with rules:
  manual edit > high-conf tier-1 > tier-2 > low-conf tier-1; values never
  regress to lower confidence; rate history is kept (negotiation needs the path,
  e.g. 1700 → 1900 → 2100).

## 6. Xiaomi/HyperOS Survival Strategy

- Foreground service with `microphone` type + persistent notification.
- Setup wizard deep-links: Autostart (`miui.intent.action.OP_AUTO_START`),
  battery saver "No restrictions", overlay, notification listener,
  accessibility, background pop-up. Each step re-checked on every app start.
- `RestartWatchdog`: `AlarmManager` heartbeat while a call is active; if the
  service is killed mid-call it restarts and resumes capture + appends to the
  same session row.

## 7. Security & Privacy

- SQLCipher key in Android Keystore; API keys in `EncryptedSharedPreferences`.
- Raw audio is **not** persisted by default (transcript-only mode); opt-in WAV
  retention with per-call consent flag stored on the session row.
- Export pipeline strips API metadata; PDF/CSV generated locally.

## 8. Error Handling & Recovery

- Every pipeline stage is a supervised coroutine; failure of ASR/LLM degrades
  (chip drops to Limited/Fallback, tier-1 keeps running) — never crashes the call.
- `CrashShield` (uncaught-handler) flushes in-flight transcript to Room before
  rethrow; sessions reopen in "recovered" state.

## 9. Phase-2 Validation (architecture test gate)

Checked against Phase-1 FRs: every FR-group maps to exactly one module
(FR-100→detection, FR-200→audio, FR-300→asr, FR-400→engine/extraction+ai,
FR-500→engine/negotiation, FR-600→engine/calculator, FR-700→broker,
FR-800→app summary/export, FR-900→core/db+WorkManager, FR-1000→app wizard).
Dependency-direction rule keeps engines JVM-testable (Phase 10 requirement).
Buildability of the toolchain (AGP 8.10 on Gradle 8.14/JDK 21) is verified by
the Phase 5+ CI builds in this repo.
