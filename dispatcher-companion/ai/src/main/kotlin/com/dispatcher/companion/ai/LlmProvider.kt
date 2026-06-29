package com.dispatcher.companion.ai

/** Thrown when no provider can serve a request (offline, no key). The caller
 *  queues the job via WorkManager and retries when online (FR-901). */
class LlmUnavailableException(message: String) : Exception(message)

/**
 * Provider abstraction (FR "AI ENGINE"): Claude API, OpenAI API, local models.
 * Implementations are interchangeable at runtime.
 */
interface LlmProvider {
    val name: String
    val worksOffline: Boolean

    /** One-shot completion; returns the model's text output. */
    suspend fun complete(system: String, user: String): String
}

/** Picks the best provider for current conditions; null means "queue for later". */
class ProviderRegistry(private val providers: List<LlmProvider>) {

    fun select(online: Boolean): LlmProvider? =
        providers.firstOrNull { online || it.worksOffline }

    suspend fun complete(online: Boolean, system: String, user: String): String {
        val p = select(online)
            ?: throw LlmUnavailableException("no provider available (online=$online)")
        return p.complete(system, user)
    }
}
