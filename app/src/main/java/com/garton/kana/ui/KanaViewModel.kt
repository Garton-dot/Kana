package com.garton.kana.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.garton.kana.audio.Speaker
import com.garton.kana.data.Course
import com.garton.kana.engine.Feedback
import com.garton.kana.engine.Progression
import com.garton.kana.engine.QuizEngine
import com.garton.kana.engine.QuizSession
import com.garton.kana.engine.Rewards
import com.garton.kana.engine.SessionMode
import com.garton.kana.engine.SessionSummary
import com.garton.kana.engine.Step
import com.garton.kana.model.ItemKind
import com.garton.kana.model.ProgressState
import com.garton.kana.model.StudyItem
import com.garton.kana.model.StudyUnit
import com.garton.kana.model.Track
import com.garton.kana.reminder.Reminders
import com.garton.kana.storage.ProgressRepository
import java.time.LocalDate

enum class Tab { LEARN, CHART, PROFILE }

sealed interface Screen {
    data object Home : Screen
    data object Quiz : Screen
    data class Results(val title: String, val summary: SessionSummary, val rewards: Rewards, val unit: StudyUnit?) : Screen
}

/** An immutable snapshot of the running quiz for the UI. */
data class QuizUi(
    val mode: SessionMode,
    val title: String,
    val step: Step?,
    val progress: Float,
    val combo: Int,
    val xp: Int,
    val score: Int,
    val feedback: Feedback?,
    val endsAt: Long?,
    val stepNumber: Int,
)

class KanaViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProgressRepository(app)
    private val engine = QuizEngine()
    private val speaker = Speaker(app)

    var progress by mutableStateOf(repo.load())
        private set
    var tab by mutableStateOf(Tab.LEARN)
    var track by mutableStateOf(Track.HIRAGANA)
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var quiz by mutableStateOf<QuizUi?>(null)
        private set

    private var session: QuizSession? = null
    private var title = ""
    private var feedback: Feedback? = null
    private var endsAt: Long? = null
    private var stepNumber = 0

    init {
        Reminders.schedule(app, progress.remindersOn)
    }

    fun now() = System.currentTimeMillis()
    fun today() = LocalDate.now().toEpochDay()

    fun speak(text: String) = speaker.speak(text)

    private fun poolFor(item: StudyItem): List<StudyItem> =
        Progression.unlockedItems(progress, Course.unit(item.unitId)!!.track)

    // ---- Starting sessions ----

    fun startLesson(unit: StudyUnit) {
        val pool = Progression.unlockedItems(progress, unit.track)
        val steps = engine.lesson(unit, progress, pool)
        begin(QuizSession(SessionMode.LESSON, unit.id, steps, retry = { engine.question(it, pool) }), "${unit.track.native} · ${unit.title}")
    }

    fun reviewCount(): Int = Progression.dueItems(progress, now()).size

    fun canReview(): Boolean = Progression.seenItems(progress).isNotEmpty()

    fun startReview() {
        val due = Progression.dueItems(progress, now()).take(15)
        val items = due.ifEmpty {
            Progression.seenItems(progress).sortedWith(compareBy({ progress.items[it.id]?.box ?: 0 }, { progress.items[it.id]?.due ?: 0 })).take(12)
        }
        if (items.isEmpty()) return
        val steps = engine.review(items, ::poolFor)
        begin(QuizSession(SessionMode.REVIEW, null, steps, retry = { engine.question(it, poolFor(it)) }), if (due.isEmpty()) "Weak spots" else "Review")
    }

    fun startLightning() {
        val seenKana = Progression.seenItems(progress, ItemKind.KANA)
        val items = if (seenKana.size >= 8) seenKana else Progression.unlockedItems(progress, Track.HIRAGANA)
        val session = QuizSession(
            SessionMode.LIGHTNING, null, engine.lightningBatch(items, ::poolFor),
            retry = { engine.question(it, poolFor(it)) },
            refill = { engine.lightningBatch(items, ::poolFor) },
        )
        endsAt = now() + LIGHTNING_MS
        begin(session, "Lightning round", keepDeadline = true)
    }

    private fun begin(s: QuizSession, title: String, keepDeadline: Boolean = false) {
        session = s
        this.title = title
        feedback = null
        stepNumber = 0
        if (!keepDeadline) endsAt = null
        screen = Screen.Quiz
        publish()
    }

    private fun publish() {
        val s = session ?: return
        quiz = QuizUi(s.mode, title, s.current, s.progress, s.combo, s.xp, s.firstTryCorrect, feedback, endsAt, stepNumber)
    }

    // ---- In-quiz actions ----

    fun answer(choice: Int) {
        val s = session ?: return
        if (feedback != null || s.current !is Step.Ask) return
        feedback = s.answer(choice)
        publish()
    }

    fun next() {
        val s = session ?: return
        if (s.current is Step.Ask && feedback == null) return
        feedback = null
        s.advance()
        stepNumber++
        if (s.isFinished) finish() else publish()
    }

    fun timeUp() {
        val s = session ?: return
        if (s.mode != SessionMode.LIGHTNING) return
        s.stop()
        finish()
    }

    fun quit() {
        session = null
        quiz = null
        screen = Screen.Home
    }

    private fun finish() {
        val s = session ?: return
        val summary = s.summary()
        val (state, rewards) = Progression.completeSession(progress, summary, now(), today())
        updateProgress(state)
        session = null
        quiz = null
        screen = Screen.Results(title, summary, rewards, s.unitId?.let { Course.unit(it) })
    }

    fun closeResults() {
        screen = Screen.Home
    }

    // ---- Settings ----

    fun setDailyGoal(goal: Int) = updateProgress(progress.copy(dailyGoal = goal))

    fun setReminders(on: Boolean) {
        updateProgress(progress.copy(remindersOn = on))
        Reminders.schedule(getApplication<Application>(), on)
    }

    fun resetProgress() = updateProgress(ProgressState(dailyGoal = progress.dailyGoal, remindersOn = progress.remindersOn))

    private fun updateProgress(state: ProgressState) {
        progress = state
        repo.save(state)
    }

    override fun onCleared() {
        speaker.shutdown()
    }

    companion object {
        const val LIGHTNING_MS = 60_000L
    }
}
