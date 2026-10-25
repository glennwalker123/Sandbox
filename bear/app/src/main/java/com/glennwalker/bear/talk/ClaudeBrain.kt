package com.glennwalker.bear.talk

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.glennwalker.bear.core.BearTalk

/** One turn as sent to Claude: the person's message (with the bear's state) or the bear's JSON reply. */
class ChatTurn(val fromBear: Boolean, val content: String)

/**
 * The bear's voice, through the Claude API. Blocking: call it off the main thread.
 * The personality and reply format come from [BearTalk], shared with the web preview.
 */
class ClaudeBrain(apiKey: String) {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
    private val json = ObjectMapper()

    class Answer(val reply: BearTalk.Reply, val raw: String)

    fun answer(turns: List<ChatTurn>): Answer {
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(4000L)
            .system(BearTalk.instructions)
            // Chat wants quick, short replies; low effort keeps them snappy.
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
            // If a safety check declines a message, let the API retry on a suitable model.
            .addBeta("server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .apply {
                for (turn in turns) if (turn.fromBear) addAssistantMessage(turn.content) else addUserMessage(turn.content)
            }
            .build()
        val message = client.beta().messages().create(params)
        if (message.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
            return Answer(BearTalk.reply("Let's talk about something else.", null, null), "")
        }
        val text = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
        return Answer(parse(text), text)
    }

    /** Reads the JSON object out of the reply, tolerating a stray sentence around it. */
    private fun parse(text: String): BearTalk.Reply {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        val node: JsonNode? = if (start >= 0 && end > start) runCatching { json.readTree(text.substring(start, end + 1)) }.getOrNull() else null
        if (node == null) return BearTalk.reply(text.trim().take(200), null, null)
        val suggestions = node.get("suggestions")?.takeIf { it.isArray }?.map { it.asText() }
        return BearTalk.reply(node.get("say")?.asText(), suggestions, node.get("react")?.asText())
    }

    companion object {
        const val MODEL = "claude-opus-5-5"
    }
}
