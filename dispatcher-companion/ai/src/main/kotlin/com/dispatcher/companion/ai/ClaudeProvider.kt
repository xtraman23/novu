package com.dispatcher.companion.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.stream.Collectors

/**
 * Claude provider over the official Anthropic Java SDK. Used for tier-2
 * extraction refinement, negotiation replies, and call summaries when online.
 */
class ClaudeProvider(
    apiKey: String,
    // Model.of: the bundled SDK enum predates this id, but the API accepts it.
    private val model: Model = Model.of("claude-opus-4-8"),
    private val maxTokens: Long = 2_048,
    client: AnthropicClient? = null,
) : LlmProvider {

    override val name = "claude"
    override val worksOffline = false

    private val client: AnthropicClient =
        client ?: AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    override suspend fun complete(system: String, user: String): String =
        withContext(Dispatchers.IO) {
            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                .addUserMessage(user)
                .build()
            val response = client.messages().create(params)
            response.content().stream()
                .flatMap { it.text().stream() }
                .map { it.text() }
                .collect(Collectors.joining())
        }
}
