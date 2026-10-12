package com.garton.kana.engine

import com.garton.kana.model.ItemProgress

/** Leitner-style spaced repetition: each correct answer promotes an item to a box with a longer interval. */
object Srs {
    const val MAX_BOX = 5
    const val MASTERED_BOX = 4

    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60 * MINUTE

    /** Wait before an item in box N is due again. */
    private val INTERVALS = longArrayOf(0L, 10 * MINUTE, DAY, 3 * DAY, 7 * DAY, 21 * DAY)

    fun onAnswer(previous: ItemProgress?, correct: Boolean, now: Long): ItemProgress {
        val p = previous ?: ItemProgress()
        val box = if (correct) minOf(p.box + 1, MAX_BOX) else maxOf(p.box - 2, 0)
        return ItemProgress(
            box = box,
            due = now + INTERVALS[box],
            seen = p.seen + 1,
            correct = p.correct + if (correct) 1 else 0,
        )
    }

    fun isDue(p: ItemProgress?, now: Long): Boolean = p != null && p.seen > 0 && p.due <= now
}
