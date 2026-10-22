package com.garton.kana.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garton.kana.engine.Feedback
import com.garton.kana.engine.Gamify
import com.garton.kana.engine.Question
import com.garton.kana.engine.QuestionType
import com.garton.kana.engine.SessionMode
import com.garton.kana.engine.Step
import com.garton.kana.model.ItemKind
import com.garton.kana.model.StudyItem
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun QuizScreen(vm: KanaViewModel, quiz: QuizUi) {
    BackHandler { vm.quit() }
    val haptics = LocalHapticFeedback.current
    val step = quiz.step
    val feedback = quiz.feedback

    // Read Japanese aloud as soon as it appears.
    LaunchedEffect(quiz.stepNumber) {
        when (step) {
            is Step.Teach -> vm.speak(step.item.japanese)
            is Step.Ask -> if (step.question.speakOnShow && quiz.mode != SessionMode.LIGHTNING) vm.speak(step.question.item.japanese)
            null -> Unit
        }
    }

    // Feedback: haptics, audio for the right answer, and auto-advance on success.
    LaunchedEffect(quiz.stepNumber, feedback) {
        if (feedback == null || step !is Step.Ask) return@LaunchedEffect
        if (feedback.correct) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (quiz.mode != SessionMode.LIGHTNING) vm.speak(step.question.item.japanese)
            delay(if (quiz.mode == SessionMode.LIGHTNING) 250 else 750)
            vm.next()
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (quiz.mode != SessionMode.LIGHTNING) vm.speak(step.question.item.japanese)
            if (quiz.mode == SessionMode.LIGHTNING) {
                delay(700)
                vm.next()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            TopBar(vm, quiz)
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = quiz.stepNumber to step,
                transitionSpec = {
                    (slideInHorizontally { it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 2 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "step",
            ) { (_, shown) ->
                when (shown) {
                    is Step.Teach -> TeachCard(vm, shown.item, onSpeak = { vm.speak(shown.item.japanese) })
                    is Step.Ask -> AskCard(shown.question, if (shown == step) feedback else null, onSpeak = { vm.speak(shown.question.item.japanese) }) { vm.answer(it) }
                    null -> Box(Modifier.fillMaxSize())
                }
            }
            if (step is Step.Teach) {
                BigButton("Got it!", KanaColors.Sakura) { vm.next() }
                Spacer(Modifier.height(16.dp))
            }
        }
        AnimatedVisibility(
            visible = feedback != null && step is Step.Ask,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (feedback != null && step is Step.Ask) FeedbackBanner(vm, feedback, step.question, quiz.mode) { vm.next() }
        }
    }
}

@Composable
private fun TopBar(vm: KanaViewModel, quiz: QuizUi) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { vm.quit() }) { Text("✕", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val endsAt = quiz.endsAt
        if (endsAt != null) {
            var left by remember { mutableLongStateOf(endsAt - vm.now()) }
            LaunchedEffect(endsAt) {
                while (true) {
                    left = endsAt - vm.now()
                    if (left <= 0) {
                        vm.timeUp()
                        break
                    }
                    delay(100)
                }
            }
            val fraction = left.toFloat() / KanaViewModel.LIGHTNING_MS
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(50)),
                color = if (fraction < 0.2f) KanaColors.Wrong else KanaColors.Flame,
                trackColor = KanaColors.Flame.copy(alpha = 0.15f),
            )
            Spacer(Modifier.width(10.dp))
            Pill("⏱ ${(left / 1000).coerceAtLeast(0)}s", KanaColors.Flame)
            Spacer(Modifier.width(6.dp))
            Pill("✓ ${quiz.score}", KanaColors.Correct)
        } else {
            val animated by animateFloatAsState(quiz.progress, label = "progress")
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(50)),
                color = KanaColors.Correct,
                trackColor = KanaColors.Correct.copy(alpha = 0.15f),
            )
            Spacer(Modifier.width(10.dp))
            Pill("⚡ ${quiz.xp}", KanaColors.Gold)
        }
    }
    ComboBadge(quiz.combo)
}

@Composable
private fun ComboBadge(combo: Int) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(combo) {
        if (combo >= 3) {
            pop.snapTo(1.35f)
            pop.animateTo(1f, tween(250))
        }
    }
    Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = combo >= 3, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
            val mult = Gamify.multiplier(combo)
            val label = if (mult > 1f) "🔥 $combo combo · ${formatMult(mult)} XP" else "🔥 $combo in a row"
            Pill(label, if (combo >= 10) KanaColors.Wrong else KanaColors.Flame, Modifier.scale(pop.value))
        }
    }
}

private fun formatMult(m: Float) = if (m % 1f == 0f) "${m.toInt()}×" else "$m×"

@Composable
private fun TeachCard(vm: KanaViewModel, item: StudyItem, onSpeak: () -> Unit) {
    // Centered when it fits; scrolls when a long word, its sentence and its kanji stories don't.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Pill("✨ NEW", KanaColors.Sakura)
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, KanaColors.Sakura.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                    .clickable(onClick = onSpeak)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val word = item.kind == ItemKind.WORD
                    FittedText(if (word) item.written else item.japanese, if (item.kind == ItemKind.KANA) 120.sp else 72.sp)
                    // Vocab shows its kanji under the kana; deck words show their reading under the kanji.
                    (if (word) item.kanji?.let { item.japanese } else item.kanji)?.let {
                        Text(it, fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.romaji.isNotEmpty()) {
                        Text(item.romaji, fontSize = 30.sp, fontWeight = FontWeight.Black, color = KanaColors.Sakura, textAlign = TextAlign.Center)
                    }
                    if (item.meaning.isNotEmpty()) {
                        Text(item.meaning, fontSize = 22.sp, textAlign = TextAlign.Center)
                    }
                    item.sentence?.let {
                        Spacer(Modifier.height(14.dp))
                        Text(it, fontSize = 19.sp, textAlign = TextAlign.Center)
                    }
                    item.sentenceMeaning?.let {
                        Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("🔊 Tap the card to hear it", color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.kanji?.let {
                Spacer(Modifier.height(14.dp))
                MnemonicPanel(vm, it, editable = true)
            }
        }
    }
}

/** One line of large text, shrunk until it fits the width, so long words never wrap onto themselves. */
@Composable
private fun FittedText(text: String, maxSize: TextUnit, fontWeight: FontWeight = FontWeight.Bold) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val fit = with(LocalDensity.current) { maxWidth.toSp() / (text.length * 1.1f) }
        val size = if (fit < maxSize) fit else maxSize
        Text(
            text,
            fontSize = size,
            lineHeight = size * 1.25f,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun AskCard(question: Question, feedback: Feedback?, onSpeak: () -> Unit, onAnswer: (Int) -> Unit) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(feedback) {
        if (feedback != null && !feedback.correct) {
            for (x in listOf(-24f, 24f, -16f, 16f, -8f, 8f, 0f)) shake.animateTo(x, tween(45))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            instruction(question.type) + if (question.isRetry) "  ·  try again" else "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .offset { IntOffset(shake.value.roundToInt(), 0) }
                .clickable(enabled = question.speakOnShow, onClick = onSpeak),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (question.type) {
                    QuestionType.KANA_TO_ROMAJI -> FittedText(question.prompt, 120.sp)
                    QuestionType.WORD_TO_MEANING, QuestionType.KANJI_TO_READING -> FittedText(question.prompt, 72.sp)
                    QuestionType.ROMAJI_TO_KANA -> FittedText(question.prompt, 64.sp)
                    QuestionType.MEANING_TO_WORD -> Text(
                        question.prompt,
                        fontSize = 34.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                question.hint?.let { Text(it, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (question.speakOnShow) Text("🔊", fontSize = 20.sp)
            }
        }
        val japaneseOptions = question.optionsAreJapanese
        val wide = question.options.any { it.length > if (japaneseOptions) 4 else 7 }
        if (wide) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                question.options.forEachIndexed { i, option ->
                    OptionButton(option, i, question, feedback, japaneseOptions, Modifier.fillMaxWidth(), onAnswer)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (col in 0..1) {
                            val i = row * 2 + col
                            OptionButton(question.options[i], i, question, feedback, japaneseOptions, Modifier.weight(1f), onAnswer)
                        }
                    }
                }
            }
        }
        // Room for the feedback banner so it never hides the options.
        Spacer(Modifier.height(210.dp))
    }
}

private fun instruction(type: QuestionType) = when (type) {
    QuestionType.KANA_TO_ROMAJI -> "What sound is this?"
    QuestionType.ROMAJI_TO_KANA -> "Pick the kana"
    QuestionType.WORD_TO_MEANING -> "What does this mean?"
    QuestionType.MEANING_TO_WORD -> "How do you say this?"
    QuestionType.KANJI_TO_READING -> "How do you read this?"
}

@Composable
private fun OptionButton(
    text: String, index: Int, question: Question, feedback: Feedback?, japanese: Boolean, modifier: Modifier, onAnswer: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, edge) = when {
        feedback == null -> scheme.surface to scheme.onSurfaceVariant.copy(alpha = 0.25f)
        index == question.correctIndex -> KanaColors.Correct.copy(alpha = 0.2f) to KanaColors.Correct
        index == feedback.chosen -> KanaColors.Wrong.copy(alpha = 0.2f) to KanaColors.Wrong
        else -> scheme.surface.copy(alpha = 0.5f) to scheme.onSurfaceVariant.copy(alpha = 0.1f)
    }
    Box(
        modifier
            .heightIn(min = if (japanese) 76.dp else 60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(2.dp, edge, RoundedCornerShape(18.dp))
            .clickable(enabled = feedback == null) { onAnswer(index) }
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = if (japanese) (if (text.length <= 2) 36.sp else 26.sp) else 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeedbackBanner(vm: KanaViewModel, feedback: Feedback, question: Question, mode: SessionMode, onContinue: () -> Unit) {
    val color = if (feedback.correct) KanaColors.Correct else KanaColors.Wrong
    val item = question.item
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .background(color.copy(alpha = 0.14f))
            .padding(20.dp),
    ) {
        if (feedback.correct) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Gamify.cheer(feedback.combo + item.id.hashCode()), color = color, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Spacer(Modifier.weight(1f))
                Text("+${feedback.xpGained} XP", color = KanaColors.Gold, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Text(describe(item), color = color)
        } else {
            Text("Not quite!", color = color, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text(
                describe(item),
                color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
            if (mode != SessionMode.LIGHTNING) {
                item.kanji?.let {
                    Spacer(Modifier.height(8.dp))
                    MnemonicPanel(vm, it, editable = false, compact = true, maxKanji = 2)
                }
                Text("It'll come back at the end — you've got this.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                BigButton("Continue", color, onClick = onContinue)
            }
        }
    }
}

/** "食べる (たべる) = taberu · to eat", leaving out whatever the item doesn't have. */
private fun describe(item: StudyItem): String {
    val head = if (item.kind == ItemKind.WORD) item.written + (item.kanji?.let { " (${item.japanese})" } ?: "")
    else item.japanese + (item.kanji?.let { " ($it)" } ?: "")
    return head + listOf(item.romaji, item.meaning).filter { it.isNotEmpty() }.joinToString(" · ").let { if (it.isEmpty()) "" else " = $it" }
}

@Composable
fun BigButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}
