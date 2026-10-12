package com.garton.kana.engine

import com.garton.kana.model.StudyItem

data class Feedback(val correct: Boolean, val chosen: Int, val correctIndex: Int, val xpGained: Int, val combo: Int)

/** Runs one sequence of steps: tracks combo and XP, and re-queues missed questions until answered. */
class QuizSession(
    val mode: SessionMode,
    val unitId: String?,
    initial: List<Step>,
    private val retry: (StudyItem) -> Question,
    private val refill: (() -> List<Step>)? = null,
) {
    private val queue = ArrayDeque(initial)

    var current: Step? = queue.removeFirstOrNull()
        private set
    var combo = 0
        private set
    var maxCombo = 0
        private set
    var xp = 0
        private set
    var firstTryCorrect = 0
        private set
    var questions = 0
        private set
    private var completed = 0
    private val answers = mutableListOf<Pair<String, Boolean>>()

    val isFinished: Boolean get() = current == null

    val progress: Float
        get() {
            val total = completed + queue.size + if (current != null) 1 else 0
            return if (total == 0) 1f else completed.toFloat() / total
        }

    fun answer(choice: Int): Feedback {
        val question = (current as Step.Ask).question
        val correct = choice == question.correctIndex
        var gained = 0
        if (!question.isRetry) {
            questions++
            answers += question.item.id to correct
        }
        if (correct) {
            combo++
            maxCombo = maxOf(maxCombo, combo)
            gained = Gamify.xpFor(combo)
            xp += gained
            if (!question.isRetry) firstTryCorrect++
        } else {
            combo = 0
            if (mode != SessionMode.LIGHTNING) queue.addLast(Step.Ask(retry(question.item).copy(isRetry = true)))
        }
        return Feedback(correct, choice, question.correctIndex, gained, combo)
    }

    fun advance() {
        completed++
        if (queue.isEmpty()) refill?.invoke()?.let { queue.addAll(it) }
        current = queue.removeFirstOrNull()
    }

    /** Ends the session early (Lightning time-out). */
    fun stop() {
        queue.clear()
        current = null
    }

    fun summary() = SessionSummary(mode, unitId, firstTryCorrect, questions, xp, maxCombo, answers.toList())
}
