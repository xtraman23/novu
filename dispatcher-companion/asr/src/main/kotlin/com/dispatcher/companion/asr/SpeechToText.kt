package com.dispatcher.companion.asr

import com.dispatcher.companion.model.PcmChunk
import kotlinx.coroutines.flow.Flow

sealed class AsrEvent {
    /** Low-latency hypothesis for the live transcript line; may be revised. */
    data class Partial(val text: String, val tMs: Long) : AsrEvent()

    /** Committed utterance text. */
    data class Final(
        val text: String,
        val tStartMs: Long,
        val tEndMs: Long,
        val confidence: Double,
    ) : AsrEvent()
}

enum class LatencyClass { STREAMING_SUB_2S, NEAR_REALTIME, BATCH }

/**
 * Provider abstraction (FR-302). Implementations registered at runtime:
 * cloud streaming (primary online), Vosk small-en / whisper.cpp tiny-en
 * (offline). Selection is by connectivity + user setting.
 */
interface SpeechToText {
    val name: String
    val latencyClass: LatencyClass
    val worksOffline: Boolean

    fun stream(pcm: Flow<PcmChunk>): Flow<AsrEvent>
}
