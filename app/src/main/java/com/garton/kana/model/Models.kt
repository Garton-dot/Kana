package com.garton.kana.model

enum class ItemKind { KANA, VOCAB, WORD }

enum class Track(val title: String, val native: String, val emoji: String) {
    HIRAGANA("Hiragana", "ひらがな", "🌸"),
    KATAKANA("Katakana", "カタカナ", "⚡"),
    VOCAB("Vocabulary", "ことば", "📚"),
    /** Words imported from the learner's own Anki deck; empty until they import one. */
    DECK("Anki deck", "Anki", "🀄"),
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
    /** Example sentence and its translation, imported deck words only. */
    val sentence: String? = null,
    val sentenceMeaning: String? = null,
) {
    /** How the word is written: kanji when it has them, else kana. */
    val written: String get() = kanji ?: japanese
}

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
    /** The learner's own mnemonic per kanji; replaces the built-in story when present. */
    val mnemonics: Map<String, String> = emptyMap(),
    /** One-time wipe of Anki-imported progress has run (learners asked to start the deck fresh). */
    val deckProgressReset: Boolean = false,
    /** "My kana": ids of kana items the learner flagged or slipped on, studied as their own pool. */
    val taggedKana: Set<String> = emptySet(),
    /** Romaji under deck and vocab words; hidden by default so the kana get read. */
    val showRomaji: Boolean = false,
    /** Score out of 46 on the last hiragana check, -1 before the first. */
    val lastCheckScore: Int = -1,
)
