package com.garton.kana.engine

import com.garton.kana.data.Course
import com.garton.kana.model.ProgressState
import com.garton.kana.model.Track
import kotlin.math.ceil

/** How fast new deck words are being learned, and where that pace lands you. */
data class Pace(
    /** New deck words per day, averaged over the last [days] days (up to [WINDOW]). */
    val perDay: Double,
    val days: Int,
    val learned: Int,
    val total: Int,
    /** Days until every deck word has been met at this pace; null when there's no pace yet. */
    val daysToFinish: Int?,
    /** Days until [MILESTONE] words (about JLPT N5); null once passed or without a pace. */
    val daysToMilestone: Int?,
) {
    companion object {
        const val WINDOW = 14
        const val MIN_DAYS = 3
        const val MILESTONE = 800
        /** How long the per-day log is kept. */
        const val KEEP_DAYS = 60

        /** Null until there are [MIN_DAYS] days of history: a pace from one session would mislead. */
        fun of(state: ProgressState, today: Long): Pace? {
            val total = Course.items(Track.DECK).size
            if (total == 0) return null
            val learned = Course.items(Track.DECK).count { (state.items[it.id]?.seen ?: 0) > 0 }
            val first = state.newWordsByDay.keys.minOrNull() ?: return null
            val days = minOf(WINDOW.toLong(), today - first + 1).toInt()
            if (days < MIN_DAYS) return null
            val recent = state.newWordsByDay.filterKeys { it > today - days }.values.sum()
            val perDay = recent.toDouble() / days
            fun daysFor(n: Int) = if (perDay <= 0.0 || n <= 0) null else ceil(n / perDay).toInt()
            return Pace(
                perDay = perDay,
                days = days,
                learned = learned,
                total = total,
                daysToFinish = daysFor(total - learned),
                daysToMilestone = if (learned >= MILESTONE || total < MILESTONE) null else daysFor(MILESTONE - learned),
            )
        }

        /** Logs deck words met for the first time in a finished session. */
        fun record(before: ProgressState, answers: List<Pair<String, Boolean>>, today: Long, after: ProgressState): ProgressState {
            val fresh = answers.map { it.first }.distinct().count { it.startsWith("d:") && (before.items[it]?.seen ?: 0) == 0 }
            if (fresh == 0) return after
            val log = after.newWordsByDay.filterKeys { it > today - KEEP_DAYS }.toMutableMap()
            log[today] = (log[today] ?: 0) + fresh
            return after.copy(newWordsByDay = log)
        }
    }
}
