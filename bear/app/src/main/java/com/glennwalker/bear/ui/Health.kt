package com.glennwalker.bear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.glennwalker.bear.core.BearSim
import com.glennwalker.bear.core.HealthLink
import com.glennwalker.bear.core.HealthReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where sleep and steps come from (Health Connect on the phone). */
interface HealthSource {
    val available: Boolean
    fun granted(): Boolean
    fun requestAccess()
    suspend fun read(): HealthReading?

    /** Set by the app; called when access changes or the app comes back to the front. */
    var onChanged: (() -> Unit)?
}

/** Reads your sleep and steps and passes them to the bear, with a line to show what happened. */
class Health(private val sim: BearSim, private val source: HealthSource?, private val scope: CoroutineScope, private val onChange: () -> Unit) {
    private val link = HealthLink()

    enum class Status { UNAVAILABLE, NOT_CONNECTED, CONNECTED }

    var status by mutableStateOf(Status.UNAVAILABLE)
        private set
    var reading by mutableStateOf("")
        private set
    var boost by mutableStateOf("")
        private set

    init {
        source?.onChanged = { sync() }
        sync()
    }

    fun connect() = source?.requestAccess()

    fun sync() {
        val src = source
        status = when {
            src == null || !src.available -> Status.UNAVAILABLE
            !src.granted() -> Status.NOT_CONNECTED
            else -> Status.CONNECTED
        }
        if (status != Status.CONNECTED || src == null) return
        scope.launch {
            val r = runCatching { src.read() }.getOrNull() ?: return@launch
            val added = link.apply(sim, r)
            reading = HealthLink.describe(r)
            if (added.any) boost = HealthLink.describe(added)
            onChange()
        }
    }
}
