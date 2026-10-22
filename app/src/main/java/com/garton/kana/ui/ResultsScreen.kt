package com.garton.kana.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garton.kana.engine.Progression
import com.garton.kana.engine.SessionMode

@Composable
fun ResultsScreen(vm: KanaViewModel, results: Screen.Results) {
    BackHandler { vm.closeResults() }
    val summary = results.summary
    val rewards = results.rewards
    val lightning = summary.mode == SessionMode.LIGHTNING
    val accuracy = if (summary.questions == 0) 0 else summary.firstTryCorrect * 100 / summary.questions
    val celebrate = lightning || (rewards.stars ?: 0) > 0 || accuracy >= 80

    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { target = rewards.xp + rewards.goalBonus }
    val xpShown by animateIntAsState(target, tween(1200), label = "xp")

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                when {
                    lightning -> "⚡"
                    rewards.stars == 3 -> "🏆"
                    accuracy >= 80 -> "🎉"
                    else -> "💪"
                },
                fontSize = 72.sp,
            )
            Text(
                when {
                    summary.mode == SessionMode.CHECK -> if (summary.firstTryCorrect == summary.questions) "All 46. Nailed it!" else "Gaps found"
                    lightning && rewards.newLightningBest -> "New record!"
                    lightning -> "Time's up!"
                    rewards.stars == 3 -> "Perfect!"
                    accuracy >= 80 -> "Great work!"
                    else -> "Practice makes perfect"
                },
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(results.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            if (rewards.stars != null) {
                StarRow(rewards.stars, size = 56, animate = true)
            } else if (results.unit != null && summary.mode == SessionMode.LESSON) {
                val seen = Progression.seenCount(vm.progress, results.unit)
                Text(
                    "Learned $seen / ${results.unit.items.size} — finish the set to earn stars ⭐",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("+$xpShown XP", fontSize = 44.sp, fontWeight = FontWeight.Black, color = KanaColors.Gold)
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (lightning) StatTile("Score", "${summary.firstTryCorrect}", KanaColors.Flame, Modifier.weight(1f))
                else StatTile("Accuracy", "$accuracy%", KanaColors.Correct, Modifier.weight(1f))
                StatTile("Best combo", "${summary.maxCombo}×", KanaColors.Sakura, Modifier.weight(1f))
                StatTile("Streak", "🔥${rewards.streak}", KanaColors.Flame, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (rewards.streakIncreased) RewardLine("🔥", "Streak extended to ${rewards.streak} day${if (rewards.streak == 1) "" else "s"}!", KanaColors.Flame)
                if (rewards.goalBonus > 0) RewardLine("🎁", "Daily goal hit! Chest: +${rewards.goalBonus} XP", KanaColors.Gold)
                rewards.leveledUpTo?.let { RewardLine("⬆️", "Level up! You're now level $it", KanaColors.Indigo) }
                rewards.unlockedUnit?.let { RewardLine("🔓", "Unlocked: ${it.track.native} · ${it.title}", KanaColors.Correct) }
                if (rewards.earnedFreeze) RewardLine("❄️", "Streak freeze earned — protects one missed day", KanaColors.Ice)
                if (lightning && rewards.newLightningBest) RewardLine("⚡", "New Lightning best: ${summary.firstTryCorrect}", KanaColors.Flame)
                rewards.newAchievements.forEach { RewardLine(it.emoji, "Achievement: ${it.title}", KanaColors.Sakura) }
                if (results.tagged.isNotEmpty()) {
                    RewardLine("📌", "Added to My kana: ${results.tagged.joinToString(" ") { it.japanese }}", KanaColors.Indigo)
                }
            }
            Spacer(Modifier.height(28.dp))

            val nextUnit = rewards.unlockedUnit
            when {
                nextUnit != null -> BigButton("Next: ${nextUnit.title} ▶", KanaColors.Sakura) {
                    vm.closeResults()
                    vm.startLesson(nextUnit)
                }
                results.unit != null -> BigButton("Keep going ▶", KanaColors.Sakura) {
                    vm.closeResults()
                    vm.startLesson(results.unit)
                }
                vm.progress.taggedKana.isNotEmpty() && (summary.mode == SessionMode.CHECK || results.tagged.isNotEmpty()) ->
                    BigButton("Drill My kana 📌", KanaColors.Indigo) {
                        vm.closeResults()
                        vm.startPool()
                    }
                lightning -> BigButton("Play again ⚡", KanaColors.Flame) {
                    vm.closeResults()
                    vm.startLightning()
                }
                else -> BigButton("Continue", KanaColors.Sakura) { vm.closeResults() }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { vm.closeResults() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                Text("Home", fontWeight = FontWeight.Bold)
            }
        }
        if (celebrate) Confetti(Modifier.fillMaxSize())
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(18.dp)).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 22.sp, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RewardLine(emoji: String, text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.padding(6.dp))
        Text(text, fontWeight = FontWeight.Bold, color = color)
    }
}
