package com.garton.kana.engine

import kotlin.math.roundToInt

object Gamify {
    const val BASE_XP = 10
    const val GOAL_BONUS_XP = 20
    const val MAX_FREEZES = 2
    const val CHECK_XP = 2

    fun multiplier(combo: Int): Float = when {
        combo >= 20 -> 3f
        combo >= 10 -> 2f
        combo >= 5 -> 1.5f
        else -> 1f
    }

    fun xpFor(combo: Int): Int = (BASE_XP * multiplier(combo)).roundToInt()

    /** Cumulative XP needed to reach [level] (level 1 starts at 0, level 2 at 100, level 3 at 300…). */
    fun xpForLevel(level: Int): Int = 50 * level * (level - 1)

    fun level(totalXp: Int): Int {
        var level = 1
        while (xpForLevel(level + 1) <= totalXp) level++
        return level
    }

    /** XP gained inside the current level, and XP the level spans. */
    fun levelProgress(totalXp: Int): Pair<Int, Int> {
        val level = level(totalXp)
        val start = xpForLevel(level)
        return (totalXp - start) to (xpForLevel(level + 1) - start)
    }

    fun stars(correct: Int, total: Int): Int {
        if (total == 0) return 0
        val accuracy = correct.toFloat() / total
        return when {
            accuracy >= 1f -> 3
            accuracy >= 0.85f -> 2
            accuracy >= 0.6f -> 1
            else -> 0
        }
    }

    private val CHEERS = listOf("すごい!", "いいね!", "Nice!", "完璧!", "やった!", "Great!", "Sugoi!", "Keep going!")
    fun cheer(seed: Int): String = CHEERS[Math.floorMod(seed, CHEERS.size)]
}
