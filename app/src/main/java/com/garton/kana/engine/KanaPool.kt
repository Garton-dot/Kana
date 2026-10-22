package com.garton.kana.engine

import com.garton.kana.data.Course
import com.garton.kana.model.ItemProgress
import com.garton.kana.model.ProgressState
import com.garton.kana.model.StudyItem
import com.garton.kana.model.Track
import kotlin.random.Random

/**
 * "My kana": the kana a learner flagged (or tripped on) while reading words, studied on their own
 * instead of marching through every row of the chart.
 */
object KanaPool {
    const val CHECK_SIZE = 46

    /** Kana that are easy to mix up; reading questions use them to set traps, and misses reveal the confusion. */
    private val LOOKALIKES: List<String> = listOf(
        "ぬめ", "わねれ", "はほけ", "るろ", "さちき", "いり", "こに", "あおめ", "うつら", "くへ", "しつ", "まも", "そろ", "たな",
        "ばぱ", "びぴ", "ぶぷ", "べぺ", "ぼぽ",
        "シツ", "ソン", "クタケ", "ウワフ", "コユ", "チテ", "ノメ", "ヌス", "ラヲ", "マム", "セサ", "ルレ",
    )
    private val confusable: Map<Char, List<Char>> = buildMap {
        for (group in LOOKALIKES) for (c in group) put(c, (getOrDefault(c, emptyList()) + group.toList().filter { it != c }).distinct())
    }

    /** Splits kana text into the course's kana items (combos like きゃ count as one); っ, ー and the like are skipped. */
    fun itemsIn(text: String): List<StudyItem> {
        val out = mutableListOf<StudyItem>()
        var i = 0
        while (i < text.length) {
            val pair = if (i + 1 < text.length) text.substring(i, i + 2) else null
            val two = pair?.let(::itemFor)
            if (two != null) {
                out += two
                i += 2
            } else {
                itemFor(text.substring(i, i + 1))?.let { out += it }
                i++
            }
        }
        return out
    }

    private fun itemFor(kana: String): StudyItem? = Course.item("h:$kana") ?: Course.item("k:$kana")

    private fun units(text: String): List<String> = itemsIn(text).map { it.japanese }

    /** Same kana shape family: ぬ/め, or combos like きゃ/さゃ built on lookalikes. */
    private fun looksAlike(a: String, b: String): Boolean =
        a.length == b.length && a.drop(1) == b.drop(1) && confusable[a[0]]?.contains(b[0]) == true

    /**
     * After a missed "How do you read this?", the kana behind the slip. Only lookalike swaps count
     * (ぬる read as める): picking a different word (いる for いう) is about the word, not about reading kana.
     */
    fun slips(correct: String, chosen: String): List<StudyItem> {
        val a = units(correct)
        val b = units(chosen)
        if (a.isEmpty() || a.size != b.size) return emptyList()
        val diff = a.indices.filter { a[it] != b[it] }
        if (diff.isEmpty() || diff.size > 2 || diff.any { !looksAlike(a[it], b[it]) }) return emptyList()
        return diff.flatMap { listOfNotNull(itemFor(a[it]), itemFor(b[it])) }.distinct()
    }

    /** [reading] with one kana swapped for a lookalike, or null when it has nothing to swap. */
    fun lookalikeOf(reading: String, random: Random): String? {
        val spots = reading.indices.filter { confusable.containsKey(reading[it]) }
        if (spots.isEmpty()) return null
        val i = spots.random(random)
        val swap = confusable.getValue(reading[i]).random(random)
        return reading.substring(0, i) + swap + reading.substring(i + 1)
    }

    fun tag(state: ProgressState, ids: Collection<String>): ProgressState =
        if (ids.all { it in state.taggedKana }) state else state.copy(taggedKana = state.taggedKana + ids)

    fun untag(state: ProgressState, id: String): ProgressState = state.copy(taggedKana = state.taggedKana - id)

    /** Kana you now know well leave the pool on their own. */
    fun graduate(state: ProgressState): ProgressState {
        val known = state.taggedKana.filter { (state.items[it]?.box ?: 0) >= Srs.MASTERED_BOX }
        return if (known.isEmpty()) state else state.copy(taggedKana = state.taggedKana - known.toSet())
    }

    fun poolItems(state: ProgressState): List<StudyItem> = state.taggedKana.mapNotNull { Course.item(it) }

    /** The 46 basic hiragana, vowels through ん. */
    fun basicHiragana(): List<StudyItem> = Course.units(Track.HIRAGANA).take(10).flatMap { it.items }

    /** The check: every basic hiragana once, kana → sound, in random order. */
    fun checkSteps(engine: QuizEngine, random: Random): List<Step> {
        val all = basicHiragana()
        return all.shuffled(random).map { Step.Ask(engine.question(it, all, QuestionType.KANA_TO_ROMAJI)) }
    }

    /** Practice for the pool: each tagged kana once, then extra rounds on the weakest. */
    fun practiceSteps(engine: QuizEngine, state: ProgressState, random: Random, asks: Int = 12): List<Step> {
        val pool = poolItems(state)
        if (pool.isEmpty()) return emptyList()
        val distractors = { it: StudyItem -> Course.items(Course.unit(it.unitId)!!.track) }
        val order = pool.shuffled(random).toMutableList()
        while (order.size < asks) order += pool.minBy { (state.items[it.id]?.box ?: 0) + random.nextInt(3) }
        return order.map { Step.Ask(engine.question(it, distractors(it))) }
    }

    /**
     * Applies a finished check: kana you read correctly count as known (box 3, due in a few days),
     * misses go through the normal schedule and into the pool.
     */
    fun applyCheck(state: ProgressState, answers: List<Pair<String, Boolean>>, now: Long): Pair<ProgressState, List<String>> {
        val items = state.items.toMutableMap()
        val missed = mutableListOf<String>()
        for ((id, ok) in answers) {
            val prev = items[id]
            items[id] = if (ok) {
                val p = Srs.onAnswer(prev, true, now)
                if (p.box >= 3) p else ItemProgress(box = 3, due = now + 3 * 86_400_000L, seen = p.seen, correct = p.correct)
            } else {
                missed += id
                Srs.onAnswer(prev, false, now)
            }
        }
        return tag(state.copy(items = items, lastCheckScore = answers.count { it.second }), missed) to missed
    }
}
