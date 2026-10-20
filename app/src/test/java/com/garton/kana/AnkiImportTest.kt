package com.garton.kana

import com.garton.kana.data.Course
import com.garton.kana.data.Romaji
import com.garton.kana.engine.AnkiCard
import com.garton.kana.engine.AnkiCollection
import com.garton.kana.engine.AnkiImport
import com.garton.kana.engine.AnkiNote
import com.garton.kana.engine.Progression
import com.garton.kana.engine.QuestionType
import com.garton.kana.engine.QuizEngine
import com.garton.kana.model.ItemKind
import com.garton.kana.model.ItemProgress
import com.garton.kana.model.ProgressState
import com.garton.kana.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AnkiImportTest {
    private val fields = listOf("Word", "Reading", "Glossary", "Sentence", "Sentence-English", "Audio")
    private val crt = 1_700_000_000L

    private fun note(id: Long, word: String, reading: String, gloss: String) =
        AnkiNote(id, fields, listOf(word, reading, gloss, "友達と<b>$word</b>。", "With a friend.", "[sound:x.mp3]"))

    private fun collection(): AnkiCollection {
        val words = listOf("言う" to "いう", "事" to "こと", "する" to "する") + (1..30).map { "語$it" to "ご" }
        val notes = words.mapIndexed { i, (w, r) -> note(100L + i, w, r, "meaning $i") }
        val cards = listOf(
            AnkiCard(100, type = 2, queue = 2, ivl = 30, due = 10, reps = 8, lapses = 1), // review, 30-day interval
            AnkiCard(101, type = 3, queue = 1, ivl = 1, due = crt + 5000, reps = 4, lapses = 2), // relearning
            AnkiCard(102, type = 0, queue = 0, ivl = 0, due = 5, reps = 0, lapses = 0), // new
        )
        return AnkiCollection("Core2.3k", crt, notes, cards)
    }

    @After fun clear() = Course.install(null)

    @Test fun romaji() {
        assertEquals("sayounara", Romaji.of("さようなら"))
        assertEquals("gakkou", Romaji.of("がっこう"))
        assertEquals("matcha", Romaji.of("まっちゃ"))
        assertEquals("koohii", Romaji.of("コーヒー"))
        assertEquals("kyou", Romaji.of("きょう"))
        assertNull(Romaji.of("言う"))
    }

    @Test fun deckBecomesATrackOfUnits() {
        val deck = AnkiImport.deck(collection())
        assertEquals(33, deck.words.size)
        assertEquals("友達と言う。", deck.words[0].sentence)
        Course.install(deck)
        val units = Course.units(Track.DECK)
        assertEquals(2, units.size) // 25 + 8
        val iu = units[0].items[0]
        assertEquals(ItemKind.WORD, iu.kind)
        assertEquals("言う", iu.written)
        assertEquals("いう", iu.japanese)
        assertEquals("iu", iu.romaji)
        assertNull(units[0].items[2].kanji) // する is kana only
        assertEquals(33, Course.items(Track.DECK).map { it.id }.toSet().size) // duplicates made unique
    }

    @Test fun reviewHistoryCarriesOver() {
        val c = collection()
        Course.install(AnkiImport.deck(c))
        val (state, report) = AnkiImport.merge(ProgressState(), c)
        assertEquals(2, report.studied)
        assertEquals(1, report.mastered)
        val items = Course.items(Track.DECK)
        val review = state.items[items[0].id]!!
        assertEquals(5, review.box)
        assertEquals((crt + 10 * 86_400) * 1000, review.due)
        assertEquals(7, review.correct)
        assertEquals(1, state.items[items[1].id]!!.box)
        assertNull(state.items[items[2].id])
        // A unit with studied words opens even without stars.
        assertTrue(Progression.isUnlocked(state, Course.units(Track.DECK)[0]))
    }

    @Test fun strongerLocalProgressIsKept() {
        val c = collection()
        Course.install(AnkiImport.deck(c))
        val id = Course.items(Track.DECK)[1].id
        val (state, _) = AnkiImport.merge(ProgressState(items = mapOf(id to ItemProgress(box = 4, seen = 9))), c)
        assertEquals(4, state.items[id]!!.box)
    }

    @Test fun kanjiWordsAskForTheirReading() {
        Course.install(AnkiImport.deck(collection()))
        val engine = QuizEngine(Random(2))
        val item = Course.items(Track.DECK)[0]
        val q = engine.question(item, Course.items(Track.DECK), QuestionType.KANJI_TO_READING)
        assertEquals("言う", q.prompt)
        assertEquals("いう", q.options[q.correctIndex])
        assertEquals(4, q.options.toSet().size)
    }
}
