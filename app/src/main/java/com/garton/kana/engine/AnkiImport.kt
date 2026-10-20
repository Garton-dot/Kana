package com.garton.kana.engine

import com.garton.kana.data.Course
import com.garton.kana.data.DeckWord
import com.garton.kana.data.ImportedDeck
import com.garton.kana.model.ItemProgress
import com.garton.kana.model.ProgressState

/** What an Anki collection holds, read from its SQLite file by a platform reader. */
data class AnkiNote(val id: Long, val fieldNames: List<String>, val fields: List<String>)

/** One Anki card's schedule, in Anki's own units (see [AnkiImport.toProgress]). */
data class AnkiCard(val noteId: Long, val type: Int, val queue: Int, val ivl: Int, val due: Long, val reps: Int, val lapses: Int)

data class AnkiCollection(val deckName: String, val createdSec: Long, val notes: List<AnkiNote>, val cards: List<AnkiCard>)

data class ImportReport(val words: Int, val studied: Int, val mastered: Int)

class AnkiFormatException(message: String) : Exception(message)

/** Turns an Anki deck into a Kana Rush track, and its review history into SRS progress. */
object AnkiImport {

    private val WORD = listOf("word", "expression", "vocab", "vocabulary", "vocabulary-kanji", "kanji", "japanese", "front")
    private val READING = listOf("reading", "kana", "hiragana", "furigana", "vocabulary-kana", "vocabulary-furigana", "pronunciation")
    private val MEANING = listOf("glossary", "meaning", "english", "definition", "translation", "vocabulary-english", "back")
    private val SENTENCE = listOf("sentence", "example", "sentence-kanji", "example sentence")
    private val SENTENCE_MEANING = listOf(
        "sentence-english", "sentence english", "sentence translation", "sentence-meaning", "sentence meaning", "example translation",
    )

    private fun find(names: List<String>, keys: List<String>): Int? =
        names.indexOfFirst { n -> keys.any { n.trim().equals(it, ignoreCase = true) } }.takeIf { it >= 0 }

    /** Field text without HTML, audio tags or entities. */
    fun clean(raw: String): String = raw
        .replace(Regex("\\[sound:[^\\]]*]"), "")
        .replace(Regex("(?i)<br\\s*/?>|<div>"), " ")
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Anki furigana "食[た]べる": the written form drops the brackets, the reading keeps only them. */
    private fun furiganaBase(s: String) = s.replace(Regex("\\s?([^\\s\\[\\]]+)\\[[^\\]]*]"), "$1")
    private fun furiganaReading(s: String) = s.replace(Regex("\\s?[^\\s\\[\\]]+\\[([^\\]]*)]"), "$1")

    private fun notesInOrder(c: AnkiCollection) = c.notes.sortedBy { it.id }

    private fun wordOf(note: AnkiNote): DeckWord? {
        val names = note.fieldNames
        val f = { i: Int? -> i?.let { note.fields.getOrNull(it) }?.let(::clean).orEmpty() }
        val wordIdx = find(names, WORD) ?: 0
        val readingIdx = find(names, READING)
        val meaningIdx = find(names, MEANING) ?: names.indices.firstOrNull { it != wordIdx && it != readingIdx }
        val rawWord = f(wordIdx)
        val word = furiganaBase(rawWord).replace(" ", "")
        val reading = f(readingIdx).let { if ('[' in it) furiganaReading(it) else it }.replace(" ", "")
            .ifEmpty { if ('[' in rawWord) furiganaReading(rawWord).replace(" ", "") else "" }
        val meaning = f(meaningIdx)
        if (word.isEmpty() || meaning.isEmpty()) return null
        return DeckWord(
            word = word,
            reading = reading.ifEmpty { word },
            meaning = meaning,
            sentence = f(find(names, SENTENCE)).let(::furiganaBase).ifEmpty { null },
            sentenceMeaning = f(find(names, SENTENCE_MEANING)).ifEmpty { null },
        )
    }

    fun deck(c: AnkiCollection): ImportedDeck {
        val words = notesInOrder(c).mapNotNull(::wordOf)
        if (words.isEmpty()) throw AnkiFormatException("This deck has no notes with a word and a meaning.")
        return ImportedDeck(c.deckName, words)
    }

    /**
     * Anki card → SRS box. type: 0 new, 1 learning, 2 review, 3 relearning. ivl is in days for review cards.
     * due is a day number counted from the collection's creation for review cards (and queue 3),
     * and epoch seconds for cards in (re)learning.
     */
    fun toProgress(card: AnkiCard, createdSec: Long): ItemProgress? {
        if (card.type == 0 || card.reps == 0) return null
        val box = when {
            card.type == 1 || card.type == 3 -> 1
            card.ivl >= 21 -> 5
            card.ivl >= 7 -> 4
            card.ivl >= 3 -> 3
            else -> 2
        }
        val dueSec = if (card.type == 2 || card.queue == 3) createdSec + card.due * 86_400 else card.due
        return ItemProgress(box = box, due = dueSec * 1000, seen = card.reps, correct = maxOf(0, card.reps - card.lapses))
    }

    /** Adds the deck's review history to [state]; existing progress wins when it is further along. */
    fun merge(state: ProgressState, c: AnkiCollection): Pair<ProgressState, ImportReport> {
        val noteIds = notesInOrder(c).filter { wordOf(it) != null }.map { it.id }
        val ids = Course.deckItemIds(deck(c).words)
        val itemFor = noteIds.zip(ids).toMap()
        val best = mutableMapOf<String, ItemProgress>()
        for (card in c.cards) {
            val id = itemFor[card.noteId] ?: continue
            val p = toProgress(card, c.createdSec) ?: continue
            val prev = best[id]
            if (prev == null || p.box > prev.box) best[id] = p
        }
        val items = state.items.toMutableMap()
        for ((id, p) in best) {
            val mine = items[id]
            if (mine == null || mine.seen == 0 || p.box > mine.box) items[id] = p
        }
        val report = ImportReport(
            words = ids.size,
            studied = best.size,
            mastered = best.values.count { it.box >= Srs.MASTERED_BOX },
        )
        return state.copy(items = items) to report
    }
}
