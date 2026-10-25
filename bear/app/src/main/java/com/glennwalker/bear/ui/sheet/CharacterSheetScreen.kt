package com.glennwalker.bear.ui.sheet

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glennwalker.bear.core.Age
import com.glennwalker.bear.core.BearGrid
import com.glennwalker.bear.core.CharacterSheet
import com.glennwalker.bear.core.BearRaster
import com.glennwalker.bear.core.Tuning
import com.glennwalker.bear.ui.matrix.DotMatrix
import com.glennwalker.bear.ui.theme.Led

private val SIZES = CharacterSheet.sizes

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
                    items(CharacterSheet.entries, key = { "${age.name}-${it.label}" }) { entry ->
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
private fun BearCell(entry: CharacterSheet.Entry, age: Age, n: Int, unit: Dp, tick: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DotMatrix(n, n, Modifier.size(unit * n)) { lit, dim ->
            val bear = BearRaster.build(entry.look(age, tick), n)
            val lift = CharacterSheet.lift(entry, tick, n)
            for (y in 0 until n) for (x in 0 until n) {
                when (bear[x, y]) {
                    BearGrid.FUR -> lit.add(x, y - lift)
                    BearGrid.INNER_EAR -> dim.add(x, y - lift)
                }
            }
        }
        Text("N = $n", style = MaterialTheme.typography.labelSmall, color = Led.Dim)
    }
}
