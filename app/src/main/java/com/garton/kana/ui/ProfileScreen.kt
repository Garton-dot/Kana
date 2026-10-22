package com.garton.kana.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garton.kana.data.Course
import com.garton.kana.engine.Achievement
import com.garton.kana.engine.Gamify
import com.garton.kana.engine.Progression
import com.garton.kana.model.ItemKind
import com.garton.kana.model.Track

private val GOALS = listOf(50 to "Casual", 100 to "Regular", 200 to "Serious", 300 to "Insane")

@Composable
fun ProfileScreen(vm: KanaViewModel, padding: PaddingValues, onImport: () -> Unit) {
    val state = vm.progress
    vm.deckVersion // re-read Course when a deck is imported
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDeckReset by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Profile", style = MaterialTheme.typography.headlineMedium) }
        item {
            val stats = listOf(
                Triple("⭐", "Level", "${Gamify.level(state.totalXp)}"),
                Triple("⚡", "Total XP", "${state.totalXp}"),
                Triple("🔥", "Best streak", "${state.bestStreak}"),
                Triple("☄️", "Best combo", "${state.bestCombo}"),
                Triple("⏱️", "Lightning best", "${state.lightningBest}"),
                Triple("🎌", "Sessions", "${state.sessions}"),
                Triple("あ", "Kana mastered", "${Progression.masteredCount(state, Course.items.filter { it.kind == ItemKind.KANA })}/208"),
                Triple("📚", "Words mastered", "${Progression.masteredCount(state, Course.items(Track.VOCAB))}/${Course.items(Track.VOCAB).size}"),
            ) + if (Course.deckName != null) {
                listOf(
                    Triple("🀄", "Anki words mastered", "${Progression.masteredCount(state, Course.items(Track.DECK))}/${Course.items(Track.DECK).size}"),
                    Triple("🃏", "Anki words studied", "${Course.items(Track.DECK).count { (state.items[it.id]?.seen ?: 0) > 0 }}"),
                )
            } else {
                emptyList()
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (emoji, label, value) ->
                            Column(
                                Modifier.weight(1f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(14.dp),
                            ) {
                                Text("$emoji $value", fontWeight = FontWeight.Black, fontSize = 20.sp)
                                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Text("Daily goal", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GOALS.forEach { (xp, name) ->
                    val active = state.dailyGoal == xp
                    Column(
                        Modifier
                            .weight(1f)
                            .background(if (active) KanaColors.Sakura else MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                            .clickable { vm.setDailyGoal(xp) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(name, fontWeight = FontWeight.Bold, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface)
                        Text("$xp XP", fontSize = 12.sp, color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Streak reminders", fontWeight = FontWeight.Bold)
                    Text("A nudge at 7pm if you haven't practiced", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.remindersOn, onCheckedChange = { vm.setReminders(it) })
            }
        }

        item { Text("Anki deck", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
        item {
            val name = Course.deckName
            if (name == null) {
                ImportCard(vm.importing, onImport)
            } else {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("🀄 $name", fontWeight = FontWeight.Bold)
                        Text(
                            "${Course.items(Track.DECK).size} words",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = onImport, enabled = !vm.importing) { Text(if (vm.importing) "Importing…" else "Re-import") }
                        TextButton(onClick = { confirmDeckReset = true }) { Text("Reset progress") }
                        TextButton(onClick = { confirmRemove = true }) { Text("Remove", color = KanaColors.Wrong) }
                    }
                }
            }
        }

        item {
            Text(
                "Achievements · ${state.achievements.size}/${Achievement.entries.size}",
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(Achievement.entries) { a ->
            val earned = a.name in state.achievements
            Row(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (earned) 1f else 0.45f)
                    .background(
                        if (earned) KanaColors.Gold.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(16.dp),
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (earned) a.emoji else "🔒", fontSize = 28.sp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(a.title, fontWeight = FontWeight.Bold)
                    Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            TextButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Reset all progress", color = KanaColors.Wrong)
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove the Anki deck?") },
            text = { Text("Its words leave the app. Your progress on them is kept, so importing the deck again brings it all back.") },
            confirmButton = {
                TextButton(onClick = { vm.removeDeck(); confirmRemove = false }) { Text("Remove", color = KanaColors.Wrong) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }

    if (confirmDeckReset) {
        AlertDialog(
            onDismissRequest = { confirmDeckReset = false },
            title = { Text("Start the Anki deck over?") },
            text = { Text("Every word in the deck goes back to new, and sets lock again from Set 1. Kana, vocabulary, XP and your own mnemonics stay.") },
            confirmButton = {
                TextButton(onClick = { vm.resetDeckProgress(); confirmDeckReset = false }) { Text("Start over", color = KanaColors.Wrong) }
            },
            dismissButton = { TextButton(onClick = { confirmDeckReset = false }) { Text("Cancel") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset everything?") },
            text = { Text("XP, streak, stars and every learned kana will be wiped. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.resetProgress(); confirmReset = false }) { Text("Reset", color = KanaColors.Wrong) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
