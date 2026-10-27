package com.glennwalker.bear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glennwalker.bear.core.Age
import com.glennwalker.bear.core.BearSim
import com.glennwalker.bear.core.SceneRaster
import com.glennwalker.bear.core.Tuning

/** Holds the one running simulation. [frame] changes whenever the scene needs redrawing. */
class BearViewModel(val keys: KeyStore, healthSource: HealthSource? = null) : ViewModel() {
    val sim = BearSim()
    val raster = SceneRaster()
    val chat = Chat(sim, keys, viewModelScope) { frame++ }
    val health = Health(sim, healthSource, viewModelScope) { frame++ }

    var frame by mutableIntStateOf(0)
        private set

    private var lastFrameMs = 0L
    private var pendingMs = 0L

    /** Advance by real elapsed time, in fixed [Tuning.TICK_MS] steps. Call once per display frame. */
    fun onFrame(nowMs: Long) {
        if (lastFrameMs == 0L) lastFrameMs = nowMs
        // After a long gap (app paused), don't fast-forward the animation.
        pendingMs = (pendingMs + nowMs - lastFrameMs).coerceAtMost(500)
        lastFrameMs = nowMs
        var stepped = false
        while (pendingMs >= Tuning.TICK_MS) {
            sim.step()
            pendingMs -= Tuning.TICK_MS
            stepped = true
        }
        if (stepped) frame++
    }

    var age by mutableStateOf(sim.age)
        private set

    fun toggleAge() {
        sim.toggleAge()
        age = sim.age
        frame++
    }

    fun act(action: BearSim.() -> Unit) {
        sim.action()
        frame++
    }
}
