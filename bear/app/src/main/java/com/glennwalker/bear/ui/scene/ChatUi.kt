package com.glennwalker.bear.ui.scene

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glennwalker.bear.core.Sprites
import com.glennwalker.bear.ui.Chat
import com.glennwalker.bear.ui.theme.Led
import kotlinx.coroutines.delay

private val Panel = Color(0xE60B0C09)
private val Line = Color(0xFF2A2E24)
private val Chip = Color(0xFF171A13)
private val Face = Color(0xFF23271F)
private val Wall = Color(0xFF121410)
private val Mono = FontFamily.Monospace

/** The bear's words, on the LED screen under the HUD, typed out like a scrolling sign. */
@Composable
fun SpeechPanel(chat: Chat, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (chat.youSaid.isNotEmpty()) {
            Text("you · ${chat.youSaid}", style = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = Led.Dim))
        }
        when {
            chat.thinking -> ThinkingDots()
            chat.isNote -> Text(chat.bearLine, style = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Led.Dim, lineHeight = 18.sp))
            else -> TypedLine(chat.bearLine)
        }
    }
}

@Composable
private fun TypedLine(text: String) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        while (shown < text.length) {
            delay(28)
            shown = minOf(text.length, shown + 2)
        }
    }
    Text(
        text.take(shown),
        style = TextStyle(fontFamily = Led.Voice, fontSize = 21.sp, lineHeight = 27.sp, color = Led.Lit, letterSpacing = 0.4.sp),
    )
}

@Composable
private fun ThinkingDots() {
    val pulse = rememberInfiniteTransition(label = "thinking")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val alpha by pulse.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 200), RepeatMode.Reverse),
                label = "dot$i",
            )
            Text("•", style = TextStyle(fontFamily = Led.Voice, fontSize = 21.sp, color = Led.Lit), modifier = Modifier.graphicsLayer { this.alpha = alpha })
        }
    }
}

/** Where you talk: suggested replies, a text field and a raised send button. */
@Composable
fun ChatBar(chat: Chat, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var draft by remember { mutableStateOf("") }
    fun send(text: String) {
        chat.send(text)
        draft = ""
    }
    Column(modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chat.suggestions) { text ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Chip)
                        .border(1.dp, Line, RoundedCornerShape(50))
                        .clickable(enabled = !chat.thinking) { send(text) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .graphicsLayer { alpha = if (chat.thinking) 0.4f else 1f },
                ) {
                    Text(text, style = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Led.Lit))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDone, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Done", style = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Led.Dim))
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(280) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Mono, fontSize = 16.sp, color = Led.Lit),
                cursorBrush = SolidColor(Led.Lit),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(draft) }),
                modifier = Modifier.weight(1f),
                decorationBox = { field ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Chip)
                            .border(1.dp, Line, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        if (draft.isEmpty()) Text("Say something…", style = TextStyle(fontFamily = Mono, fontSize = 16.sp, color = Led.Dim))
                        field()
                    }
                },
            )
            RaisedButton(size = 46.dp, raised = 5.dp, enabled = !chat.thinking, onClick = { send(draft) }) {
                SpriteIcon(Sprites.SEND, Modifier.size(20.dp).graphicsLayer { alpha = if (chat.thinking) 0.4f else 1f })
            }
        }
    }
}

/** A small round button with the same raised, press-down feel as the care button. */
@Composable
fun RaisedButton(size: Dp, raised: Dp, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateDpAsState(if (pressed) 1.dp else raised, tween(if (pressed) 40 else 70), label = "lift")
    Box(Modifier.size(size).clickable(interaction, indication = null, enabled = enabled, onClick = onClick)) {
        Box(Modifier.size(size).shadow(8.dp, CircleShape).clip(CircleShape).background(Wall).border(1.dp, Line, CircleShape))
        Box(
            Modifier.size(size).offset(y = -lift).clip(CircleShape).background(if (enabled) Face else Chip).border(1.dp, Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
