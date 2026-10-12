package com.garton.kana.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garton.kana.data.Course
import com.garton.kana.engine.Gamify
import com.garton.kana.engine.Progression
import com.garton.kana.model.StudyUnit
import com.garton.kana.model.Track

@Composable
fun HomeScreen(vm: KanaViewModel, padding: PaddingValues) {
    val state = vm.progress
    val today = vm.today()
    val units = Course.units(vm.track)
    val next = Progression.nextUnit(state)

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { StatsHeader(vm) }
        item { DailyGoalCard(Progression.todayXp(state, today), state.dailyGoal, state.goalClaimedDay == today) }
        item { ContinueButton(next) { vm.startLesson(next) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val due = vm.reviewCount()
                ModeCard(
                    emoji = "🔁", title = "Review",
                    subtitle = when {
                        due > 0 -> "$due due now"
                        vm.canReview() -> "Drill weak spots"
                        else -> "Learn something first"
                    },
                    accent = KanaColors.Indigo, enabled = vm.canReview(), badge = due.takeIf { it > 0 },
                    modifier = Modifier.weight(1f),
                ) { vm.startReview() }
                ModeCard(
                    emoji = "⚡", title = "Lightning",
                    subtitle = "60s · best ${state.lightningBest}",
                    accent = KanaColors.Flame, enabled = true, badge = null,
                    modifier = Modifier.weight(1f),
                ) { vm.startLightning() }
            }
        }
        item { TrackSelector(vm.track) { vm.track = it } }
        items(units.size) { index ->
            val unit = units[index]
            UnitNode(
                unit = unit,
                index = index,
                stars = state.unitStars[unit.id] ?: 0,
                seen = Progression.seenCount(state, unit),
                mastered = Progression.masteredCount(state, unit.items),
                unlocked = Progression.isUnlocked(state, unit),
                isNext = unit == next,
            ) { vm.startLesson(unit) }
        }
    }
}

@Composable
private fun StatsHeader(vm: KanaViewModel) {
    val state = vm.progress
    val level = Gamify.level(state.totalXp)
    val (inLevel, span) = Gamify.levelProgress(state.totalXp)
    val streak = Progression.currentStreak(state, vm.today())
    val practicedToday = state.lastPracticeDay == vm.today()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(54.dp)
                .background(Brush.linearGradient(listOf(KanaColors.Sakura, KanaColors.Indigo)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$level", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Level $level", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { inLevel.toFloat() / span },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = KanaColors.Sakura,
                trackColor = KanaColors.Sakura.copy(alpha = 0.15f),
            )
            Text("$inLevel / $span XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill("🔥 $streak", if (practicedToday) KanaColors.Flame else MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.freezes > 0) Pill("❄️ ${state.freezes}", KanaColors.Ice)
        }
    }
}

@Composable
private fun DailyGoalCard(todayXp: Int, goal: Int, claimed: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Ring(todayXp.toFloat() / goal, 64.dp, if (claimed) KanaColors.Correct else KanaColors.Gold) {
                Text(if (claimed) "✓" else "🎯", fontSize = 22.sp, color = KanaColors.Correct)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Daily goal", style = MaterialTheme.typography.titleMedium)
                Text("${minOf(todayXp, goal)} / $goal XP today", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (claimed) "Chest opened! Come back tomorrow 🎉" else "🎁 Hit it for a +${Gamify.GOAL_BONUS_XP} XP chest",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (claimed) KanaColors.Correct else KanaColors.Gold,
                )
            }
        }
    }
}

@Composable
private fun ContinueButton(unit: StudyUnit, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .scale(pulse)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(KanaColors.Sakura, KanaColors.SakuraDeep)))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CONTINUE", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
                Text("${unit.track.native} · ${unit.title}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(unit.subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp)
            }
            Text("▶", color = Color.White, fontSize = 32.sp)
        }
    }
}

@Composable
private fun ModeCard(
    emoji: String, title: String, subtitle: String, accent: Color, enabled: Boolean, badge: Int?,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Card(
        modifier = modifier.alpha(if (enabled) 1f else 0.5f).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
    ) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Column {
                Text(emoji, fontSize = 28.sp)
                Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (badge != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).background(KanaColors.Wrong, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                ) { Text("$badge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun TrackSelector(selected: Track, onSelect: (Track) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        Track.entries.forEach { track ->
            val active = track == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(track) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    track.native,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UnitNode(
    unit: StudyUnit, index: Int, stars: Int, seen: Int, mastered: Int, unlocked: Boolean, isNext: Boolean, onClick: () -> Unit,
) {
    // Zig-zag the path like a board game.
    val offsets = listOf(0.dp, 28.dp, 56.dp, 28.dp)
    val color = when {
        !unlocked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        stars == 3 -> KanaColors.Gold
        stars > 0 -> KanaColors.Correct
        else -> KanaColors.Sakura
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = unlocked, onClick = onClick)
            .padding(start = offsets[index % offsets.size], top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(68.dp)
                .border(if (isNext) 4.dp else 0.dp, if (isNext) KanaColors.Gold else Color.Transparent, CircleShape)
                .padding(if (isNext) 5.dp else 0.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (unlocked) unit.items.first().japanese else "🔒",
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.alpha(if (unlocked) 1f else 0.5f)) {
            Text(unit.title, style = MaterialTheme.typography.titleMedium)
            Text(unit.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarRow(stars, size = 16)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (seen < unit.items.size) "$seen/${unit.items.size} learned" else "$mastered/${unit.items.size} mastered",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
