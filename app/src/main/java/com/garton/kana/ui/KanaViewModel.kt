package com.garton.kana.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garton.kana.audio.Speaker
import com.garton.kana.data.Course
import com.garton.kana.engine.AnkiFormatException
import com.garton.kana.engine.AnkiImport
import com.garton.kana.engine.Feedback
import com.garton.kana.engine.KanaPool
import com.garton.kana.engine.QuestionType
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
import com.garton.kana.storage.AnkiReader
import com.garton.kana.storage.DeckRepository
import com.garton.kana.storage.ProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

enum class Tab { LEARN, CHART, PROFILE }

sealed interface Screen {
    data object Home : Screen
    data object Quiz : Screen
    data class Results(
        val title: String,
        val summary: SessionSummary,
        val rewards: Rewards,
        val unit: StudyUnit?,
        /** Kana this session put into My kana (check misses, reading slips). */
        val tagged: List<StudyItem> = emptyList(),
    ) : Screen
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
    /** Kana just added to My kana because of the last answer. */
    val slips: List<StudyItem> = emptyList(),
)

class KanaViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProgressRepository(app)
    private val decks = DeckRepository(app).also { Course.install(it.load()) }
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

    /** Bumped whenever the imported deck changes, so screens re-read [Course]. */
    var deckVersion by mutableIntStateOf(0)
        private set
    var importing by mutableStateOf(false)
        private set
    /** Outcome of the last Anki import, shown once in a dialog. */
    var importMessage by mutableStateOf<Pair<String, String>?>(null)

    private var session: QuizSession? = null
    private var title = ""
    private var feedback: Feedback? = null
    private var endsAt: Long? = null
    private var stepNumber = 0
    private var slips: List<StudyItem> = emptyList()
    private val taggedThisSession = mutableListOf<String>()

    init {
        Reminders.schedule(app, progress.remindersOn)
        // Learners chose to relearn their Anki deck from scratch: drop progress imported before that choice existed.
        if (!progress.deckProgressReset) updateProgress(AnkiImport.clearDeckProgress(progress).copy(deckProgressReset = true))
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

    /** Every basic hiragana once; whatever you miss lands in My kana. */
    fun startCheck() {
        val steps = KanaPool.checkSteps(engine, Random.Default)
        val all = KanaPool.basicHiragana()
        begin(QuizSession(SessionMode.CHECK, null, steps, retry = { engine.question(it, all) }), "Hiragana check")
    }

    fun startPool() {
        val steps = KanaPool.practiceSteps(engine, progress, Random.Default)
        if (steps.isEmpty()) return
        begin(QuizSession(SessionMode.REVIEW, null, steps, retry = { engine.question(it, Course.items(Course.unit(it.unitId)!!.track)) }), "My kana")
    }

    fun toggleTag(id: String) {
        updateProgress(if (id in progress.taggedKana) KanaPool.untag(progress, id) else KanaPool.tag(progress, listOf(id)))
    }

    fun setShowRomaji(on: Boolean) = updateProgress(progress.copy(showRomaji = on))

    private fun begin(s: QuizSession, title: String, keepDeadline: Boolean = false) {
        session = s
        this.title = title
        feedback = null
        slips = emptyList()
        taggedThisSession.clear()
        stepNumber = 0
        if (!keepDeadline) endsAt = null
        screen = Screen.Quiz
        publish()
    }

    private fun publish() {
        val s = session ?: return
        quiz = QuizUi(s.mode, title, s.current, s.progress, s.combo, s.xp, s.firstTryCorrect, feedback, endsAt, stepNumber, slips)
    }

    // ---- In-quiz actions ----

    fun answer(choice: Int) {
        val s = session ?: return
        val question = (s.current as? Step.Ask)?.question ?: return
        if (feedback != null) return
        val fb = s.answer(choice)
        feedback = fb
        // A near miss on a reading points at the kana that got mixed up: put them in My kana.
        slips = if (!fb.correct && question.type == QuestionType.KANJI_TO_READING) {
            KanaPool.slips(question.options[question.correctIndex], question.options[choice])
        } else {
            emptyList()
        }
        if (slips.isNotEmpty()) {
            updateProgress(KanaPool.tag(progress, slips.map { it.id }))
            taggedThisSession += slips.map { it.id }
        }
        publish()
    }

    fun next() {
        val s = session ?: return
        if (s.current is Step.Ask && feedback == null) return
        feedback = null
        slips = emptyList()
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
        var summary = s.summary()
        var before = progress
        if (s.mode == SessionMode.CHECK) {
            val (checked, missed) = KanaPool.applyCheck(progress, summary.answers, now())
            before = checked
            taggedThisSession += missed
            summary = summary.copy(answers = emptyList()) // already scheduled by the check
        }
        val (state, rewards) = Progression.completeSession(before, summary, now(), today())
        updateProgress(KanaPool.graduate(state))
        session = null
        quiz = null
        val tagged = taggedThisSession.distinct().mapNotNull { Course.item(it) }
        screen = Screen.Results(title, summary, rewards, s.unitId?.let { Course.unit(it) }, tagged)
    }

    fun closeResults() {
        screen = Screen.Home
    }

    // ---- Anki import ----

    /** Set when the learner picks how to import, read when the file comes back. */
    var keepAnkiHistory = false

    fun importAnki(uri: Uri) {
        val keepHistory = keepAnkiHistory
        if (importing) return
        importing = true
        viewModelScope.launch {
            try {
                val collection = withContext(Dispatchers.IO) { AnkiReader.read(getApplication(), uri) }
                val deck = AnkiImport.deck(collection)
                withContext(Dispatchers.IO) { decks.save(deck) }
                Course.install(deck)
                deckVersion++
                track = Track.DECK
                tab = Tab.LEARN
                if (keepHistory) {
                    val (state, report) = AnkiImport.merge(progress, collection)
                    updateProgress(state)
                    importMessage = "Imported ${deck.name}" to buildString {
                        append("${deck.words.size} words added as the Anki track.")
                        if (report.studied > 0) append(" Your Anki history carried over: ${report.studied} studied, ${report.mastered} mastered.")
                    }
                } else {
                    importMessage = "Imported ${deck.name}" to "${deck.words.size} words added as the Anki track. Every word starts fresh from Set 1."
                }
            } catch (e: AnkiFormatException) {
                importMessage = "Couldn't import that deck" to (e.message ?: "")
            } catch (e: Exception) {
                importMessage = "Couldn't import that deck" to "The file couldn't be read as an Anki deck (.apkg)."
            } finally {
                importing = false
            }
        }
    }

    fun resetDeckProgress() = updateProgress(AnkiImport.clearDeckProgress(progress))

    /** Saves the learner's own story for [kanji]; a blank one brings back the built-in mnemonic. */
    fun setMnemonic(kanji: String, story: String) {
        val text = story.trim()
        updateProgress(progress.copy(mnemonics = if (text.isEmpty()) progress.mnemonics - kanji else progress.mnemonics + (kanji to text)))
    }

    fun removeDeck() {
        decks.clear()
        Course.install(null)
        deckVersion++
        if (track == Track.DECK) track = Track.HIRAGANA
    }

    // ---- Settings ----

    fun setDailyGoal(goal: Int) = updateProgress(progress.copy(dailyGoal = goal))

    fun setReminders(on: Boolean) {
        updateProgress(progress.copy(remindersOn = on))
        Reminders.schedule(getApplication<Application>(), on)
    }

    fun resetProgress() = updateProgress(
        ProgressState(dailyGoal = progress.dailyGoal, remindersOn = progress.remindersOn, mnemonics = progress.mnemonics, deckProgressReset = true, showRomaji = progress.showRomaji),
    )

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
