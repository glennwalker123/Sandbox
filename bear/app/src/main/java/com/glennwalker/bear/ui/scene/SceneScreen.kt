package com.glennwalker.bear.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.glennwalker.bear.core.Age
import com.glennwalker.bear.ui.BearViewModel
import com.glennwalker.bear.ui.theme.Led
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

@Composable
fun SceneScreen(vm: BearViewModel, onOpenCharacterSheet: () -> Unit) {
    LaunchedEffect(vm) {
        while (true) withFrameMillis { vm.onFrame(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            SceneCanvas(vm)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LedButton("Feed") { vm.act { feed() } }
            LedButton("Make it worry") { vm.act { makeWorry() } }
            LedButton(if (vm.age == Age.CUB) "Show adult" else "Show cub") { vm.toggleAge() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DebugLine(vm)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenCharacterSheet) { Text("Character sheet", color = Led.Dim) }
        }
    }
}

@Composable
private fun SceneCanvas(vm: BearViewModel) {
    Canvas(
        Modifier
            .aspectRatio(120f / 200f)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Led.Off, RoundedCornerShape(20.dp))
            .pointerInput(vm) {
                awaitEachGesture {
                    val frame = SceneFrame(size.width.toFloat(), size.height.toFloat())
                    val down = awaitFirstDown()
                    vm.act { pointerDown(frame.sceneX(down.position.x), frame.sceneY(down.position.y), down.uptimeMillis) }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val x = frame.sceneX(change.position.x)
                            val y = frame.sceneY(change.position.y)
                            if (!change.pressed) {
                                vm.act { pointerUp(x, y, change.uptimeMillis) }
                                break
                            }
                            vm.act { pointerMove(x, y, change.uptimeMillis) }
                            change.consume()
                        }
                    } catch (e: CancellationException) {
                        vm.act { pointerCancel() }
                        throw e
                    }
                }
            },
    ) {
        vm.frame // Redraw whenever the simulation moves.
        drawScene(vm.sim, SceneFrame(size.width, size.height))
    }
}

@Composable
private fun DebugLine(vm: BearViewModel) {
    vm.frame
    val s = vm.sim
    Text(
        "${s.mode.name.lowercase()} · ${s.look().mood.name.lowercase()} · " +
            "♥${s.happiness.roundToInt()} ⚡${s.energy.roundToInt()} ✚${s.health.roundToInt()}",
        style = MaterialTheme.typography.labelSmall,
        color = Led.Dim,
    )
}

@Composable
fun LedButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
        Text(label, color = Led.Lit)
    }
}
