package com.garton.kana.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garton.kana.data.Course
import com.garton.kana.engine.Progression
import com.garton.kana.engine.Srs
import com.garton.kana.model.ItemKind
import com.garton.kana.model.StudyItem
import com.garton.kana.model.Track

/** Every kana and word, coloured by how well it is known. Tap to hear it. */
@Composable
fun ChartScreen(vm: KanaViewModel, padding: PaddingValues) {
    val state = vm.progress
    val track = vm.track
    vm.deckVersion // re-read Course when a deck is imported
    val list = track == Track.VOCAB || track == Track.DECK
    val units = Course.units(track)
    val columns = if (list) 1 else 15

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Chart", style = MaterialTheme.typography.headlineMedium)
                val mastered = Progression.masteredCount(state, Course.items(track))
                Text(
                    "$mastered / ${Course.items(track).size} mastered · tap any card to hear it",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Track.entries.forEach { t ->
                        Pill(t.native, if (t == track) KanaColors.Sakura else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.clickable { vm.track = t })
                    }
                }
            }
        }
        for (unit in units) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(unit.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
            }
            // Kana cells span 3 of 15 columns (5 per row); combos span 5 (3 per row).
            val cellSpan = when {
                list -> 1
                unit.items.first().japanese.length > 1 -> 5
                else -> 3
            }
            items(unit.items, span = { GridItemSpan(cellSpan) }) { item ->
                val box = state.items[item.id]?.box ?: 0
                val seen = (state.items[item.id]?.seen ?: 0) > 0
                if (list) WordRow(item, box, seen) { vm.speak(item.japanese) }
                else KanaCell(item, box, seen) { vm.speak(item.japanese) }
            }
        }
    }
}

private fun masteryColor(box: Int, seen: Boolean): Color = when {
    !seen -> Color.Gray.copy(alpha = 0.12f)
    box >= Srs.MASTERED_BOX -> KanaColors.Gold.copy(alpha = 0.45f)
    else -> KanaColors.Sakura.copy(alpha = 0.12f + 0.1f * box)
}

@Composable
private fun KanaCell(item: StudyItem, box: Int, seen: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(if (item.japanese.length > 1) 1.4f else 0.85f)
            .clip(RoundedCornerShape(12.dp))
            .background(masteryColor(box, seen))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.japanese, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(item.romaji, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WordRow(item: StudyItem, box: Int, seen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(masteryColor(box, seen))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val (main, second) = if (item.kind == ItemKind.WORD) item.written to item.kanji?.let { item.japanese } else item.japanese to item.kanji
            Text(main + (second?.let { "  $it" } ?: ""), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(item.romaji, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(item.meaning, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(Srs.MAX_BOX) { i ->
                Box(Modifier.size(6.dp).background(if (i < box) KanaColors.Sakura else Color.Gray.copy(alpha = 0.25f), CircleShape))
            }
        }
    }
}
