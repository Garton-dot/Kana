package com.garton.kana.engine

import com.garton.kana.data.Course
import com.garton.kana.model.ProgressState
import com.garton.kana.model.Track

enum class Achievement(val emoji: String, val title: String, val description: String, val earned: (ProgressState) -> Boolean) {
    FIRST_STEPS("👣", "First Steps", "Finish your first session", { it.sessions >= 1 }),
    COMBO_10("🔥", "On Fire", "Hit a 10× combo", { it.bestCombo >= 10 }),
    COMBO_25("☄️", "Unstoppable", "Hit a 25× combo", { it.bestCombo >= 25 }),
    PERFECT("💯", "Perfectionist", "Earn 3 stars on a unit", { s -> s.unitStars.values.any { it >= 3 } }),
    STREAK_3("📅", "Habit Forming", "Reach a 3-day streak", { it.bestStreak >= 3 }),
    STREAK_7("🗓️", "Week Warrior", "Reach a 7-day streak", { it.bestStreak >= 7 }),
    STREAK_30("🏯", "Shogun", "Reach a 30-day streak", { it.bestStreak >= 30 }),
    HIRAGANA_ALL("🌸", "Hiragana Hero", "Clear every hiragana unit", { trackCleared(it, Track.HIRAGANA) }),
    KATAKANA_ALL("⚡", "Katakana King", "Clear every katakana unit", { trackCleared(it, Track.KATAKANA) }),
    VOCAB_ALL("📚", "Wordsmith", "Clear lessons 1–5", { trackCleared(it, Track.VOCAB) }),
    WORDS_50("🧠", "Lexicon", "Know 50 words well", { s -> Course.items(Track.VOCAB).count { (s.items[it.id]?.box ?: 0) >= 3 } >= 50 }),
    LIGHTNING_30("⏱️", "Speed Demon", "Score 30 in Lightning", { it.lightningBest >= 30 }),
    LEVEL_10("👑", "Level 10", "Reach level 10", { Gamify.level(it.totalXp) >= 10 }),
    SESSIONS_50("🎌", "Dedicated", "Complete 50 sessions", { it.sessions >= 50 }),
}

private fun trackCleared(s: ProgressState, track: Track): Boolean =
    Course.units(track).all { (s.unitStars[it.id] ?: 0) >= 1 }
