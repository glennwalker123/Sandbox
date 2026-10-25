package com.glennwalker.bear.ui.scene

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glennwalker.bear.core.Activity
import com.glennwalker.bear.core.Age
import com.glennwalker.bear.core.CareMenu
import com.glennwalker.bear.core.Floor
import com.glennwalker.bear.core.SceneRaster
import com.glennwalker.bear.ui.BearViewModel
import com.glennwalker.bear.ui.matrix.DotLayout
import com.glennwalker.bear.ui.matrix.DotMatrix
import com.glennwalker.bear.ui.theme.Led
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

@Composable
fun SceneScreen(vm: BearViewModel, onOpenCharacterSheet: () -> Unit) {
    LaunchedEffect(vm) {
        while (true) withFrameMillis { vm.onFrame(it) }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf(false) }
    val chat = vm.chat
    vm.frame // The activity can end from the close box, inside the scene.
    val activity = vm.sim.activity
    LaunchedEffect(activity) { if (activity == Activity.TALK) chat.show() }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SceneCanvas(vm)
                }
                Spacer(Modifier.height(CareDock.HEIGHT))
            }
            if (menuOpen) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Led.Background.copy(alpha = 0.6f))
                        .pointerInput(Unit) { detectTapGestures { menuOpen = false } },
                )
            }
            // The bottom area follows the activity; the close box in the title bar ends it.
            when (activity) {
                Activity.TALK -> ChatBar(chat, modifier = Modifier.align(Alignment.BottomCenter).height(CareDock.HEIGHT))
                Activity.FETCH -> Text(
                    CareMenu.fetchStatus(vm.sim),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Led.Dim),
                    modifier = Modifier.align(Alignment.BottomCenter).height(CareDock.HEIGHT).padding(top = 14.dp),
                )
                Activity.NONE -> CareDock(
                    vm = vm,
                    open = menuOpen,
                    onOpenChange = { menuOpen = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        DebugLine(vm)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("DEBUG", style = MaterialTheme.typography.labelSmall, color = Led.Dim)
            SmallButton("Make it worry") { vm.act { makeWorry() } }
            SmallButton(if (vm.age == Age.CUB) "Show adult" else "Show cub") { vm.toggleAge() }
            SmallButton("Character sheet", onOpenCharacterSheet)
            SmallButton("Claude key") { editingKey = true }
        }
    }
    if (editingKey) KeyDialog(vm, onClose = { editingKey = false })
}

/** Enter the Claude API key the bear talks with. Stored on this phone only. */
@Composable
private fun KeyDialog(vm: BearViewModel, onClose: () -> Unit) {
    var key by remember { mutableStateOf(vm.keys.claudeKey.orEmpty()) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Led.Off,
        title = { Text("Claude API key", color = Led.Lit) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Bear uses Claude to talk. The key stays on this phone and is only sent to Anthropic when you chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Led.Dim,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    singleLine = true,
                    placeholder = { Text("sk-ant-…") },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.keys.claudeKey = key.ifEmpty { null }; onClose() }) { Text("Save", color = Led.Lit) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", color = Led.Dim) } },
    )
}

@Composable
private fun SceneCanvas(vm: BearViewModel) {
    BoxWithConstraints(Modifier.aspectRatio(Floor.WIDTH.toFloat() / Floor.HEIGHT)) {
        SceneDots(vm)
        // The bear's words sit just under the HUD.
        vm.frame
        if (vm.sim.activity == Activity.TALK) SpeechPanel(vm.chat, Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.16f))
    }
}

@Composable
private fun SceneDots(vm: BearViewModel) {
    DotMatrix(
        cols = Floor.WIDTH,
        rows = Floor.HEIGHT,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .pointerInput(vm) {
                awaitEachGesture {
                    val layout = DotLayout(size.width.toFloat(), size.height.toFloat(), Floor.WIDTH, Floor.HEIGHT)
                    val down = awaitFirstDown()
                    vm.act { pointerDown(layout.gridX(down.position.x), layout.gridY(down.position.y), down.uptimeMillis) }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val x = layout.gridX(change.position.x)
                            val y = layout.gridY(change.position.y)
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
    ) { lit, dim ->
        vm.frame // Redraw whenever the simulation moves.
        val dots = vm.raster.render(vm.sim)
        for (y in 0 until Floor.HEIGHT) for (x in 0 until Floor.WIDTH) {
            val v = dots[y * Floor.WIDTH + x].toInt()
            if (v == SceneRaster.LIT) lit.add(x, y) else if (SceneRaster.isDim(v, x, y)) dim.add(x, y)
        }
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
private fun SmallButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Led.Dim)
    }
}
