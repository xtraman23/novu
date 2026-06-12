# Phases 10–12: Testing, Optimization, Release

## Phase 10 — Testing

Full suite: **83 distinct unit tests, 0 failures** (101 executions counting
debug+release variants of Android modules). Coverage by category:

- **Unit**: every engine (merge rules, ladder, VAD/diarizer/assembler,
  extraction, negotiation, calculator, broker rollups, prompts/parsers,
  summary/export, RC-notification parsing, wizard parsing).
- **Integration**: extraction over multi-turn conversations; ASR pipeline
  end-to-end on synthetic PCM; schema integrity (DDL → inserts → cascade →
  FTS) on real SQLite, both via `docs/validate_schema.py` (7 checks) and the
  `core:db` JVM test.
- **Stress/perf**: 1 hour of synthetic audio through VAD+assembler < 2 s CPU;
  tier-1 extraction budget < 10 ms/segment (measured ~µs).
- **Recovery**: idempotent segment replay, assembler `resumeFrom`, watchdog
  failover, LLM garbage-output degradation.
- **Bugs found & fixed by tests during development**: case-sensitive pickup
  cue, `53'` word-boundary, "Yeah" as city, FTS numeric tokenization,
  `ComponentName` in JVM tests, em-dash trimming, negotiation column overlap.
- **On-device (requires hardware, not runnable in CI container)**: permission
  flows, HyperOS battery/autostart behavior, SpeechRecognizer latency, battery
  drain (<8 %/hr target). Checklist lives in this file for the first device pass.

## Phase 11 — Optimization

- `ndk.abiFilters = [arm64-v8a, armeabi-v7a]`: debug APK 61 MB → 53 MB; the
  Redmi 14C is arm64.
- Release: R8 minify + resource shrink → **22 MB**; keep rules for SQLCipher
  (JNI) and dontwarn for desktop-JVM reflection types referenced by the
  Anthropic SDK's unused tool-runner path.
- Runtime: tier-1 extraction is regex over single segments (no re-scan of the
  whole transcript); UI/overlay are StateFlow observers (no polling); DB writes
  are incremental per final segment; foreground service is `IMPORTANCE_LOW`
  notification; overlay uses plain views (no Compose runtime in the window).

## Phase 12 — Release

- `gradle :app:assembleRelease` → `app-release-unsigned.apk` (sign per README).
- Versioning: 0.1.0 (1). minSdk 29, target/compile 35.
- Known limitations for v0.1 (tracked for v0.2):
  1. ASR uses the platform `SpeechRecognizer` (speakerphone capture); Vosk
     offline model and cloud streaming ASR plug into the existing
     `SpeechToText` interface.
  2. Tier-2 LLM refinement requires an API key wired into `ClaudeProvider`
     (settings-screen entry planned); all features degrade gracefully offline.
  3. Bundled gazetteer covers 66 major freight markets; the full 42k-row ZIP
     dataset drops into `assets/zip_geo.csv` with no code change.
  4. WorkManager offline→online LLM re-queue and nightly `VACUUM INTO` backup
     jobs are specified (Phase 2/3) but not yet wired.
