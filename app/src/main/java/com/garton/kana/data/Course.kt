package com.garton.kana.data

import com.garton.kana.model.ItemKind
import com.garton.kana.model.StudyItem
import com.garton.kana.model.StudyUnit
import com.garton.kana.model.Track

/** Words from the learner's own Anki deck, in deck order. */
data class DeckWord(
    val word: String,
    val reading: String,
    val meaning: String,
    val sentence: String? = null,
    val sentenceMeaning: String? = null,
)

data class ImportedDeck(val name: String, val words: List<DeckWord>)

object Course {
    const val DECK_UNIT_SIZE = 25

    private val base: List<StudyUnit> = HIRAGANA_UNITS + KATAKANA_UNITS + VOCAB_UNITS

    var deckName: String? = null
        private set
    var units: List<StudyUnit> = base
        private set
    var items: List<StudyItem> = units.flatMap { it.items }
        private set

    private var itemsById = items.associateBy { it.id }
    private var unitsById = units.associateBy { it.id }

    /** Swaps in (or removes, with null) the imported deck's track. */
    fun install(deck: ImportedDeck?) {
        deckName = deck?.name
        units = base + (deck?.let(::deckUnits) ?: emptyList())
        items = units.flatMap { it.items }
        itemsById = items.associateBy { it.id }
        unitsById = units.associateBy { it.id }
    }

    /** Stable ids: the same word in a re-imported deck keeps its progress. */
    fun deckItemIds(words: List<DeckWord>): List<String> {
        val count = mutableMapOf<String, Int>()
        return words.map { w ->
            val key = "d:${w.word}|${w.reading}|${w.meaning}"
            val n = (count[key] ?: 0) + 1
            count[key] = n
            if (n == 1) key else "$key#$n"
        }
    }

    private fun deckUnits(deck: ImportedDeck): List<StudyUnit> {
        val ids = deckItemIds(deck.words)
        return deck.words.indices.chunked(DECK_UNIT_SIZE).mapIndexed { n, chunk ->
            val unitId = "d-${n + 1}"
            val items = chunk.map { i ->
                val w = deck.words[i]
                val reading = w.reading.ifEmpty { w.word }
                StudyItem(
                    id = ids[i],
                    kind = ItemKind.WORD,
                    japanese = reading,
                    romaji = Romaji.of(reading).orEmpty(),
                    unitId = unitId,
                    meaning = w.meaning,
                    kanji = w.word.takeIf { it != reading },
                    sentence = w.sentence,
                    sentenceMeaning = w.sentenceMeaning,
                )
            }
            StudyUnit(unitId, Track.DECK, "Set ${n + 1}", items.take(4).joinToString(" ") { it.written }, items)
        }
    }

    fun item(id: String): StudyItem? = itemsById[id]
    fun unit(id: String): StudyUnit? = unitsById[id]
    fun units(track: Track): List<StudyUnit> = units.filter { it.track == track }
    fun items(track: Track): List<StudyItem> = units(track).flatMap { it.items }
}
