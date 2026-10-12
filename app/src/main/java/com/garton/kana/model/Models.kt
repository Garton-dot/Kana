package com.garton.kana.model

enum class ItemKind { KANA, VOCAB }

enum class Track(val title: String, val native: String, val emoji: String) {
    HIRAGANA("Hiragana", "ひらがな", "🌸"),
    KATAKANA("Katakana", "カタカナ", "⚡"),
    VOCAB("Vocabulary", "ことば", "📚"),
}

/** One learnable thing: a kana (or kana combo) or a vocabulary word. */
data class StudyItem(
    val id: String,
    val kind: ItemKind,
    /** The kana (for KANA items) or the word written in kana (for VOCAB items). */
    val japanese: String,
    val romaji: String,
    val unitId: String,
    /** English meaning, vocab only. */
    val meaning: String = "",
    /** Kanji spelling, vocab only, when commonly written with kanji. */
    val kanji: String? = null,
)

data class StudyUnit(
    val id: String,
    val track: Track,
    val title: String,
    val subtitle: String,
    val items: List<StudyItem>,
)

/** Spaced-repetition memory for a single item. */
data class ItemProgress(
    val box: Int = 0,
    val due: Long = 0L,
    val seen: Int = 0,
    val correct: Int = 0,
)

data class ProgressState(
    val totalXp: Int = 0,
    val items: Map<String, ItemProgress> = emptyMap(),
    val unitStars: Map<String, Int> = emptyMap(),
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Epoch day of the last completed session, -1 if never. */
    val lastPracticeDay: Long = -1,
    val todayXp: Int = 0,
    val todayDay: Long = -1,
    val dailyGoal: Int = 100,
    val goalClaimedDay: Long = -1,
    val bestCombo: Int = 0,
    val lightningBest: Int = 0,
    val achievements: Set<String> = emptySet(),
    val sessions: Int = 0,
    val freezes: Int = 0,
    val remindersOn: Boolean = true,
)
