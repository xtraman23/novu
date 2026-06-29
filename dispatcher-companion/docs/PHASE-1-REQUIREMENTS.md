# RingCentral Dispatcher Companion — Phase 1: Requirements Analysis

**Product:** RingCentral Dispatcher Companion (Android)
**Primary user:** Truck dispatchers / freight brokers negotiating loads over RingCentral
**Primary device:** Xiaomi Redmi 14C (HyperOS / Android 14, MediaTek Helio G81-Ultra, 4–8 GB RAM)
**Status:** Phase 1 of 12 — requirements baseline for all later phases

---

## 1. Product Overview

While the dispatcher talks to a freight broker through the RingCentral Android
app, Dispatcher Companion runs as a real-time AI copilot. It captures the call
audio through the best legally and technically available method, transcribes it
live, continuously extracts the PDWCR fields (Pickup, Delivery, Weight,
Commodity, Rate) plus extended freight data, suggests counter-offers, and
produces broker notes and a call summary automatically when the call ends.

## 2. User Stories (Core)

| # | As a dispatcher… | Acceptance criterion |
|---|---|---|
| US-1 | I press **START DISPATCH MODE** once and everything (capture, transcription, extraction, notes) starts | One tap, < 3 s to "listening" state |
| US-2 | I see PDWCR fields fill themselves in while the broker talks | Fields update < 4 s after the info is spoken |
| US-3 | I get a suggested counter-offer when the broker quotes a rate | Counter, floor, ceiling, acceptance % shown |
| US-4 | I see a floating panel over RingCentral so I never leave the call screen | Movable overlay, never blocks call controls |
| US-5 | When the call ends I get a finished summary + notes with zero effort | Summary generated automatically on call end |
| US-6 | I can look up a broker's history before/while negotiating | Searchable broker profiles with rate history |
| US-7 | I can run quick lane math (miles, deadhead, RPM, profit) | Calculator usable mid-call from the overlay |
| US-8 | The app works in a dead zone and syncs later | Offline capture + local store, deferred AI sync |
| US-9 | The app walks me through every Xiaomi permission once | Setup wizard with per-permission status checks |

## 3. Functional Requirements

### 3.1 Call detection & RingCentral integration (FR-100)
- FR-101 Detect RingCentral call start/end via: notification listener (ongoing-call
  notification), accessibility events (RingCentral in foreground, in-call UI),
  and audio-mode changes (`AudioManager.MODE_IN_COMMUNICATION`).
- FR-102 Extract caller name/number from the RingCentral notification/UI when present.
- FR-103 Auto-show the floating copilot panel on call start; auto-generate summary on call end.
- FR-104 Function degrades gracefully if RingCentral updates its UI (detection is
  heuristic, never hard-coupled to view IDs alone).

### 3.2 Audio capture (FR-200) — method ladder
The app probes methods at setup and at every call start, then picks the best:

| Priority | Method | Android reality on a non-rooted Redmi 14C |
|---|---|---|
| 1 | AudioPlaybackCapture of RingCentral output | **Will not work for voice**: VoIP audio uses `USAGE_VOICE_COMMUNICATION`, which Android excludes from playback capture for third-party apps. Probed anyway (OEM/firmware variations exist). |
| 2 | Direct VoIP capture (`VOICE_CALL`/`VOICE_DOWNLINK` sources) | Requires the system-only `CAPTURE_AUDIO_OUTPUT` permission. Probed; expected unavailable. |
| 3 | Accessibility-assisted capture | Some OEM audio paths allow recorder apps with an AccessibilityService to reach call audio (the "Cube ACR technique"). Device/firmware dependent; probed at runtime. |
| 4 | MediaProjection-assisted capture | Same `USAGE_VOICE_COMMUNICATION` exclusion applies; useful only for media/notification audio. Probed. |
| 5 | **Microphone fallback (expected primary)** | `VOICE_RECOGNITION` / `UNPROCESSED` mic source. With **speakerphone on**, both sides are captured acoustically. With earpiece, only the dispatcher side is strong; broker side is partial/none. Always available. |

- FR-201 Automatic probe ranks methods and selects the best working one per call.
- FR-202 Capture status surfaced to the user at all times:
  **Excellent** (both sides, clean) / **Good** (both sides via speakerphone) /
  **Limited** (dispatcher side only) / **Fallback** (degraded mic).
- FR-203 The app recommends speakerphone when that is the only path to two-sided audio.
- FR-204 Capture runs in a `mediaProjection`/`microphone`-type **foreground service**
  with persistent notification; survives screen-off and app-switch.

> **Honest engineering constraint (drives architecture):** on stock Android
> 10+, third-party apps cannot capture VoIP call audio directly. The product
> must be excellent in microphone/speakerphone mode, and treat methods 1–4 as
> opportunistic upgrades, not as the baseline.

### 3.3 Live transcription (FR-300)
- FR-301 Streaming speech-to-text, end-to-end latency target **< 2 s** (cloud) /
  best-effort (< 5 s) on-device.
- FR-302 Provider abstraction: cloud streaming ASR (primary when online),
  on-device Vosk / whisper.cpp tiny-en (offline fallback). Hot-swappable.
- FR-303 Speaker separation: channel/heuristic diarization — dispatcher vs broker —
  labeled `Broker:` / `Dispatcher:` in the transcript; "Unknown" allowed.
- FR-304 Rolling transcript persisted incrementally (crash-safe).

### 3.4 PDWCR + advanced freight extraction (FR-400)
- FR-401 Continuous extraction of **P**ickup, **D**elivery, **W**eight, **C**ommodity, **R**ate
  with confidence scores; fields update as better info appears, never flicker backwards.
- FR-402 Extended fields: pickup/delivery ZIP, equipment type, length, broker
  name/company, MC number, appointment times, special requirements, detention,
  layover, free-form notes.
- FR-403 Two-tier extraction: instant on-device regex/gazetteer pass (cities,
  ZIPs, weights, rates, MC#) + LLM refinement pass when online.
- FR-404 Every field is manually editable; manual edits are sticky (AI never overwrites).

### 3.5 Negotiation copilot (FR-500)
- FR-501 Detect urgency, flexibility, pressure cues, negotiation opportunities.
- FR-502 Output: suggested response, counter-offer, walk-away rate, likely broker
  floor/ceiling, acceptance probability.
- FR-503 Inputs: live transcript, lane rate context, broker history (Phase 9), and
  dispatcher-configured cost basis (RPM floor, fuel cost).

### 3.6 Freight calculator & lane search (FR-600)
- FR-601 Loaded miles, deadhead, RPM, revenue, fuel estimate, profit, profit/mile,
  profitability score.
- FR-602 City↔City, ZIP↔ZIP, ZIP↔City lookups from a bundled offline ZIP/city
  centroid database (instant, no network).

### 3.7 Broker memory (FR-700)
- FR-701 Broker profiles: name, company, MC, rate history, lane history, average
  rate, negotiation style, reliability, success rate, notes. Full-text searchable.

### 3.8 Summaries, notes, export (FR-800)
- FR-801 Auto call summary: broker, company, lane, rate, PDWCR, key details,
  outcome, follow-ups, next steps.
- FR-802 Note styles: short, detailed, bullets, CRM-style; one-tap regenerate.
- FR-803 Export: TXT, CSV, PDF, clipboard, email, Android share intent.

### 3.9 Offline mode & storage (FR-900)
- FR-901 Capture, transcription (on-device engine), regex extraction, calculator,
  and broker DB all work offline; LLM analysis queued and synced when online.
- FR-902 Local encrypted SQLite (Room + SQLCipher), automatic local backup,
  fast indexed search.

### 3.10 Xiaomi/HyperOS setup wizard (FR-1000)
Guided, auto-detecting checklist for: microphone, notifications,
notification-listener access, accessibility service, display-over-other-apps
(overlay), **Autostart** (MIUI/HyperOS-specific), battery saver "No restrictions",
background pop-up permission, and foreground-service awareness. Each step shows
live granted/denied status and deep-links to the exact HyperOS settings screen.

## 4. Non-Functional Requirements

| Area | Requirement |
|---|---|
| Latency | Transcript < 2 s (cloud); PDWCR field update < 4 s after utterance |
| Battery | < 8%/hr during an active call on Redmi 14C; near-zero when idle |
| Memory | Steady-state service < 180 MB (device may have only 4 GB RAM) |
| Resilience | Foreground service auto-restart; incremental persistence; crash-safe transcripts |
| Security | SQLCipher at rest; API keys in Android Keystore; no audio retained unless user opts in |
| Privacy/Legal | First-run consent notice; per-call recording-consent reminder; configurable "two-party-consent mode" (transcript-only, no audio retention). Call-recording consent law varies by US state — the user is responsible for compliance; the app surfaces the reminder. |
| Compatibility | minSdk 29 (Android 10, required for capture APIs), target latest; tested on HyperOS |

## 5. Technology Stack (decided)

- **Language/UI:** Kotlin, Jetpack Compose + a lightweight View-based overlay (Compose-in-`WindowManager` for the floating panel)
- **DI:** Hilt · **DB:** Room + SQLCipher + FTS4 · **Async:** Coroutines/Flow
- **Services:** Foreground service (`microphone` type) for capture; `NotificationListenerService` + `AccessibilityService` for RingCentral detection
- **ASR:** pluggable — cloud streaming provider, Vosk (small-en), whisper.cpp (tiny-en) for offline
- **AI:** provider abstraction over Claude API / OpenAI API / local; JSON-schema-constrained extraction prompts
- **Sync/queue:** WorkManager
- **Build:** Gradle (Kotlin DSL), JDK 17; module-per-engine (`:core`, `:audio`, `:asr`, `:extraction`, `:ai`, `:broker`, `:app`)

## 6. Top Risks

| Risk | Severity | Mitigation |
|---|---|---|
| No direct VoIP audio access on stock Android | High | Speakerphone-first UX; method ladder; honest capture-status indicator |
| HyperOS kills background services | High | Autostart + battery-exemption wizard; foreground service; restart watchdog |
| Redmi 14C CPU too weak for local Whisper | Medium | Vosk small / whisper tiny-en only; cloud ASR primary |
| RingCentral UI changes break detection | Medium | Multi-signal detection (notification + audio mode + accessibility) |
| Recording-consent law exposure | Medium | Consent reminders; transcript-only mode; user-facing legal notice |

## 7. Phase Plan & Exit Criteria

Phases 1–12 as specified in the project brief. Each phase ends with its test
suite green before the next begins. **Phase 1 exit criterion:** this document
reviewed + UI concept selected by the product owner (you) from the five
concepts in `docs/ui-concepts/`.
