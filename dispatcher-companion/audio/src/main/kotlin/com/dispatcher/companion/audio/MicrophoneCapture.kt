package com.dispatcher.companion.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.dispatcher.companion.model.CaptureMethodId
import com.dispatcher.companion.model.CaptureQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Method 5 — microphone fallback, the expected primary path on a non-rooted
 * Redmi 14C. VOICE_RECOGNITION source: unprocessed enough for ASR, and with
 * speakerphone on it captures both call sides acoustically (quality GOOD);
 * with the earpiece only the dispatcher side is strong (quality LIMITED).
 */
class MicrophoneCapture(private val context: Context) : CaptureMethod {

    override val id = CaptureMethodId.MICROPHONE

    override suspend fun probe(): ProbeResult {
        val granted = context.checkSelfPermission(
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return ProbeResult.Denied
        return ProbeResult.Ok(currentQuality())
    }

    private fun currentQuality(): CaptureQuality {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val speakerOn = am.availableCommunicationDevices.isNotEmpty() &&
            am.communicationDevice?.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        return if (speakerOn) CaptureQuality.GOOD else CaptureQuality.LIMITED
    }

    @SuppressLint("MissingPermission") // probe() gates on RECORD_AUDIO
    override fun start(): ActiveCapture {
        val sampleRate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf * 2, sampleRate / 2), // >= 250 ms
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val flow = MutableSharedFlow<PcmChunk>(extraBufferCapacity = 64)
        record.startRecording()
        scope.launch {
            val buf = ShortArray(sampleRate / 10) // 100 ms frames
            while (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) flow.emit(PcmChunk(buf.copyOf(n), SystemClock.elapsedRealtime()))
            }
        }
        return object : ActiveCapture {
            override val method = id
            override val quality = currentQuality()
            override val chunks: Flow<PcmChunk> = flow
            override fun stop() {
                runCatching { record.stop() }
                record.release()
                scope.cancel()
            }
        }
    }
}
