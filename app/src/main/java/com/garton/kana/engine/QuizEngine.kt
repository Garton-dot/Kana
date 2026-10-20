package com.garton.kana.engine

import com.garton.kana.data.Course
import com.garton.kana.model.ItemKind
import com.garton.kana.model.ProgressState
import com.garton.kana.model.StudyItem
import com.garton.kana.model.StudyUnit
import kotlin.random.Random

enum class SessionMode { LESSON, REVIEW, LIGHTNING }

enum class QuestionType { KANA_TO_ROMAJI, ROMAJI_TO_KANA, WORD_TO_MEANING, MEANING_TO_WORD, KANJI_TO_READING }

data class Question(
    val item: StudyItem,
    val type: QuestionType,
    val prompt: String,
    val hint: String?,
    val options: List<String>,
    val correctIndex: Int,
    val isRetry: Boolean = false,
) {
    val promptIsJapanese: Boolean get() = type != QuestionType.ROMAJI_TO_KANA && type != QuestionType.MEANING_TO_WORD
    val optionsAreJapanese: Boolean get() = !promptIsJapanese || type == QuestionType.KANJI_TO_READING
    /** Saying the word aloud is a hint, except when its reading is the question. */
    val speakOnShow: Boolean get() = promptIsJapanese && type != QuestionType.KANJI_TO_READING
}

sealed interface Step {
    data class Teach(val item: StudyItem) : Step
    data class Ask(val question: Question) : Step
}

class QuizEngine(private val random: Random = Random.Default) {

    fun answerOf(item: StudyItem, type: QuestionType): String = when (type) {
        QuestionType.KANA_TO_ROMAJI -> item.romaji
        QuestionType.ROMAJI_TO_KANA -> item.japanese
        QuestionType.WORD_TO_MEANING -> item.meaning
        QuestionType.MEANING_TO_WORD -> if (item.kind == ItemKind.WORD) item.written else item.japanese
        QuestionType.KANJI_TO_READING -> item.japanese
    }

    fun promptOf(item: StudyItem, type: QuestionType): String = when (type) {
        QuestionType.KANA_TO_ROMAJI -> item.japanese
        QuestionType.WORD_TO_MEANING -> if (item.kind == ItemKind.WORD) item.written else item.japanese
        QuestionType.ROMAJI_TO_KANA -> item.romaji
        QuestionType.MEANING_TO_WORD -> item.meaning
        QuestionType.KANJI_TO_READING -> item.written
    }

    fun types(item: StudyItem): List<QuestionType> = when (item.kind) {
        ItemKind.KANA -> listOf(QuestionType.KANA_TO_ROMAJI, QuestionType.ROMAJI_TO_KANA)
        ItemKind.VOCAB -> listOf(QuestionType.WORD_TO_MEANING, QuestionType.MEANING_TO_WORD)
        ItemKind.WORD -> if (item.kanji != null) {
            listOf(QuestionType.WORD_TO_MEANING, QuestionType.MEANING_TO_WORD, QuestionType.KANJI_TO_READING)
        } else {
            listOf(QuestionType.WORD_TO_MEANING, QuestionType.MEANING_TO_WORD)
        }
    }

    fun question(item: StudyItem, pool: List<StudyItem>, type: QuestionType = types(item).random(random)): Question {
        val answer = answerOf(item, type)
        val prompt = promptOf(item, type)
        // A distractor must not also be a right answer (じ and ぢ are both "ji").
        fun valid(other: StudyItem) = other.kind == item.kind && other.id != item.id &&
            answerOf(other, type) != answer && promptOf(other, type) != prompt
        val sameUnitFirst = pool.filter(::valid).shuffled(random).sortedBy { if (it.unitId == item.unitId) 0 else 1 }
        val fallback = Course.items.filter(::valid).shuffled(random)
        val distractors = (sameUnitFirst + fallback).map { answerOf(it, type) }.distinct().take(3)
        val options = (distractors + answer).shuffled(random)
        val hint = if (type == QuestionType.WORD_TO_MEANING && item.kind == ItemKind.VOCAB) item.kanji else null
        return Question(item, type, prompt, hint, options, options.indexOf(answer))
    }

    /** New items are taught in pairs then quizzed, followed by weighted practice over the unit. */
    fun lesson(unit: StudyUnit, state: ProgressState, pool: List<StudyItem>, maxNew: Int = 5, asks: Int = 12): List<Step> {
        val steps = mutableListOf<Step>()
        val newItems = unit.items.filter { (state.items[it.id]?.seen ?: 0) == 0 }.take(maxNew)
        for (chunk in newItems.chunked(2)) {
            chunk.forEach { steps += Step.Teach(it) }
            chunk.shuffled(random).forEach { steps += Step.Ask(question(it, pool, types(it).first())) }
        }
        val practice = unit.items.filter { (state.items[it.id]?.seen ?: 0) > 0 } + newItems
        var previous: StudyItem? = newItems.lastOrNull()
        while (steps.count { it is Step.Ask } < asks && practice.isNotEmpty()) {
            val item = weightedPick(practice.filter { it != previous }.ifEmpty { practice }, state)
            steps += Step.Ask(question(item, pool))
            previous = item
        }
        return steps
    }

    fun review(items: List<StudyItem>, poolFor: (StudyItem) -> List<StudyItem>): List<Step> =
        items.shuffled(random).map { Step.Ask(question(it, poolFor(it))) }

    fun lightningBatch(items: List<StudyItem>, poolFor: (StudyItem) -> List<StudyItem>, size: Int = 20): List<Step> =
        List(size) { items.random(random) }.map { Step.Ask(question(it, poolFor(it))) }

    /** Weaker items (low SRS box) come up more often. */
    private fun weightedPick(items: List<StudyItem>, state: ProgressState): StudyItem {
        val weights = items.map { Srs.MAX_BOX + 1 - (state.items[it.id]?.box ?: 0) }
        var roll = random.nextInt(weights.sum())
        for ((i, w) in weights.withIndex()) {
            if (roll < w) return items[i]
            roll -= w
        }
        return items.last()
    }
}
