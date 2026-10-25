package com.glennwalker.bear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.glennwalker.bear.core.BearSim
import com.glennwalker.bear.core.BearTalk
import com.glennwalker.bear.core.hear
import com.glennwalker.bear.talk.ChatTurn
import com.glennwalker.bear.talk.ClaudeBrain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the Claude API key lives. Kept on the phone only. */
interface KeyStore {
    var claudeKey: String?
}

/** The conversation with the bear: what's on screen, and the turns sent to Claude. */
class Chat(private val sim: BearSim, private val keys: KeyStore, private val scope: CoroutineScope, private val onChange: () -> Unit) {
    var open by mutableStateOf(false)
        private set
    var bearLine by mutableStateOf("")
        private set
    /** True when [bearLine] is a note from the app rather than the bear speaking. */
    var isNote by mutableStateOf(false)
        private set
    var youSaid by mutableStateOf("")
        private set
    var suggestions by mutableStateOf(BearTalk.OPENERS)
        private set
    var thinking by mutableStateOf(false)
        private set

    private val turns = mutableListOf<ChatTurn>()
    private var brain: ClaudeBrain? = null
    private var brainKey: String? = null

    fun show() {
        open = true
        youSaid = ""
        say(BearTalk.greeting(sim))
    }

    fun hide() {
        open = false
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || thinking) return
        youSaid = text
        val key = keys.claudeKey?.trim().orEmpty()
        if (key.isEmpty()) {
            note("Bear needs a Claude API key to talk. Add one under Debug → Claude key.")
            return
        }
        if (brain == null || brainKey != key) {
            brain = ClaudeBrain(key)
            brainKey = key
        }
        val ask = turns + ChatTurn(fromBear = false, content = BearTalk.userTurn(sim, text))
        thinking = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { brain!!.answer(ask) } }
            thinking = false
            result.onSuccess { answer ->
                turns.clear()
                turns += ask
                if (answer.raw.isNotEmpty()) turns += ChatTurn(fromBear = true, content = answer.raw)
                // Keep it short, and always starting with the person.
                while (turns.size > BearTalk.MAX_TURNS || turns.firstOrNull()?.fromBear == true) turns.removeAt(0)
                sim.hear(answer.reply.reaction)
                suggestions = answer.reply.suggestions
                say(answer.reply.say)
                onChange()
            }.onFailure { e ->
                note(failureNote(e))
            }
        }
    }

    private fun say(text: String) {
        isNote = false
        bearLine = text
    }

    private fun note(text: String) {
        isNote = true
        bearLine = text
    }

    private fun failureNote(e: Throwable): String {
        val name = e.javaClass.simpleName
        return when {
            name.contains("Unauthorized") || name.contains("PermissionDenied") -> "That Claude key didn't work. Check it under Debug → Claude key."
            name.contains("RateLimit") -> "That's a lot of talking for now. Try again in a little while."
            name.contains("Io") || name.contains("Timeout") -> "Bear couldn't reach Claude. Check your connection and try again."
            else -> "Bear couldn't hear you just then. Try again."
        }
    }
}
