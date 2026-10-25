package com.glennwalker.bear.ui.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glennwalker.bear.core.Age
import com.glennwalker.bear.core.BearLook
import com.glennwalker.bear.core.BearSim
import com.glennwalker.bear.core.Mood
import com.glennwalker.bear.core.Sigh
import com.glennwalker.bear.core.Tuning
import com.glennwalker.bear.ui.bear.drawBear
import com.glennwalker.bear.ui.theme.Led
import kotlin.math.roundToInt

/** One row of the sheet: a mood or pose, animated by the shared tick. */
private class Entry(val label: String, val look: (Age, Int) -> BearLook, val lift: (Int) -> Int = { 0 })

private val ENTRIES = listOf(
    Entry("Calm", { age, t -> BearLook(age, Mood.CALM, tick = t) }),
    Entry("Happy", { age, t -> BearLook(age, Mood.HAPPY, tick = t) }),
    Entry("Worried", { age, t -> BearLook(age, Mood.WORRIED, tick = t) }),
    Entry("Sleepy", { age, t -> BearLook(age, Mood.SLEEPY, tick = t) }),
    Entry("Relieved", { age, t -> BearLook(age, Mood.RELIEVED, tick = t) }),
    Entry("Hop", { age, t -> BearLook(age, Mood.HAPPY, tick = t) }, lift = { t -> BearSim.HOP.getOrElse(t % 20) { 0 } }),
    Entry("Walking", { age, t -> BearLook(age, Mood.CALM, step = if ((t / 3) % 2 == 0) 1 else 2, look = 1, tick = t) }),
    Entry("Walking away", { age, t -> BearLook(age, Mood.CALM, step = if ((t / 3) % 2 == 0) 1 else 2, back = true, tick = t) }),
    Entry("Carrying the ball", { age, t -> BearLook(age, Mood.CALM, carry = true, tick = t) }),
    Entry("Sigh", { age, t -> (t % (Tuning.SIGH_TICKS + 20)).let { s -> if (s < Tuning.SIGH_TICKS) Sigh.at(age, s, t) else BearLook(age, Mood.CALM, tick = t) } }),
)

/** The three sizes the bear is judged at: up close, the Glyph reference size, far away. */
private val SIZES = intArrayOf(52, 25, 13)

@Composable
fun CharacterSheetScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameMillis { now ->
            if (last == 0L) last = now
            if (now - last >= Tuning.TICK_MS) { tick++; last = now }
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = Led.Dim) }
            Text("Character sheet", style = MaterialTheme.typography.titleMedium, color = Led.Lit)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Cell size so the three bears (52 + 25 + 13 cells) plus gaps fit the width.
            val unit = (maxWidth - 32.dp - 24.dp) / (SIZES.sum() + 4)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (age in Age.entries) {
                    item(key = age) {
                        Text(
                            if (age == Age.CUB) "Cub" else "Adult",
                            style = MaterialTheme.typography.titleSmall,
                            color = Led.Lit,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(ENTRIES, key = { "${age.name}-${it.label}" }) { entry ->
                        Column {
                            Text(entry.label, style = MaterialTheme.typography.labelMedium, color = Led.Dim)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                                for (n in SIZES) BearCell(entry, age, n, unit, tick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BearCell(entry: Entry, age: Age, n: Int, unit: Dp, tick: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(unit * n)) {
            val px = size.width / n
            val lift = (entry.lift(tick) * n / 25f).roundToInt()
            drawBear(entry.look(age, tick), n, Offset(0f, -lift * px), px, Led.Lit, Led.Dim, Led.Off)
        }
        Text("N = $n", style = MaterialTheme.typography.labelSmall, color = Led.Dim)
    }
}
