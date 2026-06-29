# Dispatcher Companion — Windows 10 PC version

Two-sided call transcription on a PC, reliably, with a **headset** (mic in /
headphones out). It reuses the same freight engines as the Android app.

## Why this is more reliable than on the phone

On a PC the two voices are already separated by Windows:

- **Your voice** → into the headset **microphone** → `WasapiCapture`.
- **The broker's voice** → out to your **headphones** → `WasapiLoopbackCapture`
  (loopback records whatever is played to an output device, headphones included).

Two separate streams ⇒ **perfect speaker separation, no diarization guessing**.
Mic = `DISPATCHER`, loopback = `BROKER`. Android blocks call-audio capture for
third-party apps; Windows does not — so this is both simpler and more accurate.

## Pieces

| Piece | What it is | Build/run |
|---|---|---|
| `pc-capture/` (C#/.NET 8 + NAudio) | Captures mic + loopback to two 16 kHz WAVs | `dotnet build -c Release` |
| whisper.cpp | Transcribes each WAV to text (offline) | prebuilt binary + `base.en`/`small.en` model |
| `dispatcher-desktop` (this repo, `:desktop`) | The freight "brains": PDWCR + negotiation + transcript/info files. **Pure-JVM reuse of the Android engines.** | `gradle :desktop:installDist` |

## Quick proof (record both sides)

```powershell
cd pc-capture
dotnet build -c Release
.\bin\Release\net8.0-windows\DispatcherCapture.exe --list   # find your headset
.\bin\Release\net8.0-windows\DispatcherCapture.exe          # records broker.wav + dispatcher.wav
```

Make a short RingCentral test call, then stop with ENTER. Open `broker.wav` —
the broker's voice is there; `dispatcher.wav` has yours. That single step proves
the whole concept.

## Transcribe + run the engine

1. Download a whisper.cpp Windows build and the `ggml-base.en.bin` (or
   `ggml-small.en.bin`) model.
2. Transcribe each side (it can emit a timestamped transcript):
   ```powershell
   whisper-cli.exe -m ggml-base.en.bin -f broker.wav     -otxt -of broker
   whisper-cli.exe -m ggml-base.en.bin -f dispatcher.wav -otxt -of dispatcher
   ```
3. Label and merge the lines into `BROKER:` / `DISPATCHER:` form (by timestamp),
   then feed them to the engine:
   ```powershell
   # build the engine once
   gradle :desktop:installDist
   # pipe the labelled transcript in
   Get-Content merged.txt | .\desktop\build\install\desktop\bin\desktop.bat .\out
   ```
   The engine prints live PDWCR + counter-offer and writes
   `out\transcript_*.txt` (full conversation) and `out\info_*.txt` (info only).

The **live** version (real-time, no WAV step) streams each capture into
whisper in chunks and pipes the labelled lines straight into the engine — same
data flow, just continuous.

## Per-process loopback (reliable upgrade)

Default-device loopback also captures music/notifications. To capture **only
RingCentral**, use the Windows 10 (2004+) **Application Loopback API**:
`ActivateAudioInterfaceAsync` with `AUDIOCLIENT_ACTIVATION_PARAMS` set to
`PROCESS_LOOPBACK` and RingCentral's PID (include-process-tree mode). Microsoft's
`ApplicationLoopback` Win32 sample is the reference. Fallback on older builds:
point RingCentral's output at a dedicated/virtual output device and loopback
just that device.

## Recommended stack going forward

- **Most reliable audio (recommended):** keep the C#/NAudio capture front-end,
  run `:desktop` as the engine. The freight logic is 100% reused from Android.
- **One-language option:** Compose for Desktop (Kotlin/JVM) using the same
  modules, with a small native capture helper.
- **STT:** whisper.cpp `base.en`/`small.en` (offline, real-time on CPU); Vosk if
  the PC is weak; cloud (Deepgram/Azure) only if you accept internet + consent.

## Engine reuse

`:desktop` depends only on the platform-agnostic modules — `:core:model`,
`:core:report`, `:engine:extraction` (incl. the `BrokerLexicon` and the new
`ConversationContext` for stilted Q&A calls), `:engine:negotiation`,
`:engine:calculator`, `:broker`. Zero Android. That's why the same brains run on
the phone and the PC.
