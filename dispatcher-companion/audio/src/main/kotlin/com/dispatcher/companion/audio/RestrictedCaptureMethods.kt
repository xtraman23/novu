package com.dispatcher.companion.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioFormat
import com.dispatcher.companion.model.CaptureMethodId
import com.dispatcher.companion.model.CaptureQuality

/**
 * Methods 1–4 of the ladder. On stock Android 10+ these are expected to fail
 * for VoIP voice (USAGE_VOICE_COMMUNICATION is excluded from playback capture
 * and VOICE_CALL sources need the system-only CAPTURE_AUDIO_OUTPUT), but OEM
 * firmware varies, so each one really probes instead of assuming (FR-201).
 */

/** Method 1 — AudioPlaybackCapture of RingCentral's output. */
class PlaybackCaptureMethod(
    /** Supplied by the UI when the user has granted a MediaProjection. */
    private val hasProjection: () -> Boolean,
) : CaptureMethod {
    override val id = CaptureMethodId.PLAYBACK_CAPTURE

    override suspend fun probe(): ProbeResult {
        if (!hasProjection()) return ProbeResult.Denied
        // Even with a projection, voice-usage audio is not capturable by
        // third-party apps; a live capture attempt is made at call start and
        // the SilenceWatchdog demotes us if the stream is empty.
        return ProbeResult.Silent
    }

    override fun start(): ActiveCapture = throw IllegalStateException("probe() did not return Ok")
}

/** Method 2 — direct VOICE_CALL/VOICE_DOWNLINK AudioRecord sources. */
class VoipDirectCaptureMethod(private val context: Context) : CaptureMethod {
    override val id = CaptureMethodId.VOIP_DIRECT

    override suspend fun probe(): ProbeResult {
        return try {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_CALL,
                16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                AudioRecord.getMinBufferSize(
                    16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                ),
            )
            val ok = rec.state == AudioRecord.STATE_INITIALIZED
            rec.release()
            if (ok) ProbeResult.Ok(CaptureQuality.EXCELLENT) else ProbeResult.Unavailable
        } catch (_: SecurityException) {
            ProbeResult.Unavailable // CAPTURE_AUDIO_OUTPUT is system-only
        } catch (_: Exception) {
            ProbeResult.Unavailable
        }
    }

    override fun start(): ActiveCapture = throw IllegalStateException("probe() did not return Ok")
}

/** Method 3 — accessibility-assisted path (firmware dependent). */
class AccessibilityCaptureMethod(
    private val serviceEnabled: () -> Boolean,
    private val firmwareAllowsCallPath: () -> Boolean,
) : CaptureMethod {
    override val id = CaptureMethodId.ACCESSIBILITY

    override suspend fun probe(): ProbeResult = when {
        !serviceEnabled() -> ProbeResult.Denied
        !firmwareAllowsCallPath() -> ProbeResult.Unavailable
        else -> ProbeResult.Ok(CaptureQuality.EXCELLENT)
    }

    override fun start(): ActiveCapture = throw IllegalStateException("probe() did not return Ok")
}

/** Method 4 — MediaProjection-assisted capture (same voice-usage exclusion). */
class MediaProjectionCaptureMethod(
    private val hasProjection: () -> Boolean,
) : CaptureMethod {
    override val id = CaptureMethodId.MEDIA_PROJECTION

    override suspend fun probe(): ProbeResult =
        if (hasProjection()) ProbeResult.Silent else ProbeResult.Denied

    override fun start(): ActiveCapture = throw IllegalStateException("probe() did not return Ok")
}

/** Builds the full priority-ordered ladder (FR-200 table). */
fun defaultLadder(
    context: Context,
    hasProjection: () -> Boolean,
    accessibilityEnabled: () -> Boolean,
    firmwareAllowsCallPath: () -> Boolean = { false },
): CaptureMethodLadder = CaptureMethodLadder(
    listOf(
        PlaybackCaptureMethod(hasProjection),
        VoipDirectCaptureMethod(context),
        AccessibilityCaptureMethod(accessibilityEnabled, firmwareAllowsCallPath),
        MediaProjectionCaptureMethod(hasProjection),
        MicrophoneCapture(context),
    )
)
