package com.glennwalker.bear.ui.scene

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glennwalker.bear.core.CareMenu
import com.glennwalker.bear.core.Sprites
import com.glennwalker.bear.core.Tint
import com.glennwalker.bear.ui.BearViewModel
import com.glennwalker.bear.ui.matrix.DotLayout
import com.glennwalker.bear.ui.matrix.Dots
import com.glennwalker.bear.ui.theme.Led
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Colours for the raised button and the menu options. */
private object Care {
    val Face = Color(0xFF23271F)
    val FaceOpen = Color(0xFF2B3026)
    val FaceEdge = Color(0xFF2E3329)
    val Wall = Color(0xFF121410)
    val Line = Color(0xFF2A2E24)
    val Chip = Color(0xFF171A13)
}

object CareDock {
    /** Space the dock takes under the LED screen. */
    val HEIGHT = 112.dp
    private val button = 84.dp
    private val option = 60.dp
    private val radius = 128.dp
    private val hitRadius = 46.dp
    private val raised = 8.dp

    /** How long to hold before the menu opens and the finger can slide onto an option. */
    private const val HOLD_MS = 280L

    /** Where each option sits, relative to the button centre: an arc across the top, left to right. */
    private fun optionOffset(i: Int): Pair<Float, Float> {
        val angle = (165.0 - i * 30.0) * PI / 180
        return cos(angle).toFloat() to -sin(angle).toFloat()
    }

    private val overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    @Composable
    operator fun invoke(
        vm: BearViewModel,
        open: Boolean,
        onOpenChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val haptics = LocalHapticFeedback.current
        var pressed by remember { mutableStateOf(false) }
        var holding by remember { mutableStateOf(false) }
        var hot by remember { mutableStateOf<CareMenu.Item?>(null) }
        if (open) vm.frame // Keep labels current (the ball can come back while the menu is open).

        fun choose(item: CareMenu.Item) {
            if (!CareMenu.enabled(item, vm.sim)) return
            vm.act { CareMenu.choose(item, this) }
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            hot = null
            onOpenChange(false)
        }

        // Button centre sits 10 dp + half the button below the top of the dock.
        val centerFromBottom = HEIGHT - 10.dp - button / 2
        Box(modifier.fillMaxWidth().height(HEIGHT + radius + option + 80.dp), contentAlignment = Alignment.BottomCenter) {
            // Label above the arc.
            val labelAlpha by animateFloatAsState(if (open) 1f else 0f, tween(140), label = "label")
            val title = hot?.let { CareMenu.label(it, vm.sim) } ?: "Care"
            val hint = hot?.let { CareMenu.hint(it, vm.sim) } ?: if (holding) "Slide to an option and let go" else "Choose an option"
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(centerFromBottom + radius + option / 2 + 22.dp))
                    .width(240.dp)
                    .graphicsLayer { alpha = labelAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Led.Lit, textAlign = TextAlign.Center)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Led.Dim, textAlign = TextAlign.Center)
            }

            // Options.
            CareMenu.items.forEachIndexed { i, item ->
                val progress by animateFloatAsState(
                    if (open) 1f else 0f,
                    tween(durationMillis = 260, delayMillis = if (open) i * 22 else 0, easing = overshoot),
                    label = "option$i",
                )
                val enabled = CareMenu.enabled(item, vm.sim)
                val isHot = item == hot
                val (ux, uy) = optionOffset(i)
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -(centerFromBottom - option / 2))
                        .size(option)
                        .graphicsLayer {
                            translationX = ux * radius.toPx() * progress
                            translationY = uy * radius.toPx() * progress
                            val scale = (0.3f + 0.7f * progress) * if (isHot) 1.16f else 1f
                            scaleX = scale; scaleY = scale
                            alpha = progress.coerceIn(0f, 1f) * if (enabled) 1f else 0.35f
                        }
                        .clip(CircleShape)
                        .background(if (isHot) Care.Face else Care.Chip)
                        .border(1.dp, if (isHot) Led.Lit else Care.Line, CircleShape)
                        .clickable(enabled = open && enabled && !holding) { choose(item) },
                    contentAlignment = Alignment.Center,
                ) {
                    SpriteIcon(CareMenu.icon(item), Modifier.size(32.dp), dimTint = CareMenu.iconTint(item))
                }
            }

            // The raised button: a face on a wall that sinks when pressed.
            val faceLift by animateDpAsState(if (pressed) 1.dp else raised, tween(if (pressed) 40 else 70), label = "press")
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(centerFromBottom - button / 2))
                    .size(button)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            pressed = true
                            holding = false
                            val center = Offset(size.width / 2f, size.height / 2f)
                            // true: let go quickly (a tap). false: the touch was cancelled. null: still held.
                            val released = withTimeoutOrNull(HOLD_MS) { awaitRelease(down.id) }
                            if (released != null) {
                                pressed = false
                                // A quick tap opens the menu and leaves it open (or closes it).
                                if (released) onOpenChange(!open)
                                return@awaitEachGesture
                            }
                            // Held: open, then follow the finger onto an option.
                            holding = true
                            if (!open) onOpenChange(true)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val reach = radius.toPx()
                            val hit = hitRadius.toPx()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                val p = change.position - center
                                var nearest: CareMenu.Item? = null
                                var best = hit
                                CareMenu.items.forEachIndexed { i, item ->
                                    val (ox, oy) = optionOffset(i)
                                    val d = hypot(p.x - ox * reach, p.y - oy * reach)
                                    if (d < best) { best = d; nearest = item }
                                }
                                if (nearest != hot) {
                                    hot = nearest
                                    if (nearest != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            pressed = false
                            holding = false
                            val chosen = hot
                            if (chosen != null && CareMenu.enabled(chosen, vm.sim)) choose(chosen) else hot = null
                        }
                    },
            ) {
                // Wall: the side of the button, visible below the raised face.
                Box(
                    Modifier
                        .matchParentSize()
                        .shadow(14.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Care.Wall)
                        .border(1.dp, Care.Line, CircleShape),
                )
                Box(
                    Modifier
                        .size(button)
                        .offset(y = -faceLift)
                        .clip(CircleShape)
                        .background(if (open) Care.FaceOpen else Care.Face)
                        .border(1.dp, Care.FaceEdge, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SpriteIcon(Sprites.PAW, Modifier.size(36.dp))
                }
            }
        }
    }
}

/** Waits for pointer [id] to lift (true) or disappear (false). */
private suspend fun AwaitPointerEventScope.awaitRelease(id: PointerId): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return false
        if (!change.pressed) return true
    }
}

/** A small dot-matrix icon: lit and dim dots only, on whatever is behind it. */
@Composable
fun SpriteIcon(sprite: List<String>, modifier: Modifier = Modifier, litTint: Tint = Tint.NONE, dimTint: Tint = Tint.NONE) {
    Canvas(modifier) {
        val layout = DotLayout(size.width, size.height, sprite[0].length, sprite.size)
        val dots = Dots(layout)
        sprite.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch == '#') dots.lit(x, y, litTint) else if (ch == '+') dots.dim(x, y, dimTint)
            }
        }
        dots.draw(this)
    }
}
