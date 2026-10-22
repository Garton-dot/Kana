package com.garton.kana

import com.garton.kana.data.Course
import com.garton.kana.data.DeckWord
import com.garton.kana.data.ImportedDeck
import com.garton.kana.engine.KanaPool
import com.garton.kana.engine.QuestionType
import com.garton.kana.engine.QuizEngine
import com.garton.kana.engine.Srs
import com.garton.kana.engine.Step
import com.garton.kana.model.ItemProgress
import com.garton.kana.model.ProgressState
import com.garton.kana.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KanaPoolTest {
    @After fun clear() = Course.install(null)

    @Test fun readingsSplitIntoCourseKana() {
        assertEquals(listOf("h:きょ", "h:う", "h:は"), KanaPool.itemsIn("きょうは").map { it.id })
        assertEquals(listOf("h:が", "h:こ", "h:う"), KanaPool.itemsIn("がっこう").map { it.id }) // っ isn't a course item
        assertEquals(listOf("k:コ", "k:ヒ"), KanaPool.itemsIn("コーヒー").map { it.id })
    }

    @Test fun nearMissesTagTheMixedUpKana() {
        assertEquals(listOf("h:ぬ", "h:め"), KanaPool.slips("ぬる", "める").map { it.id })
        assertEquals(listOf("h:わ", "h:ね"), KanaPool.slips("わたし", "ねたし").map { it.id })
        assertTrue(KanaPool.slips("いう", "いる").isEmpty()) // a different word, not a misread kana
        assertTrue(KanaPool.slips("ともだち", "がっこう").isEmpty()) // a different word: not a kana slip
        assertTrue(KanaPool.slips("いう", "いいえ").isEmpty())
    }

    @Test fun readingQuestionsCarryALookalikeTrap() {
        Course.install(ImportedDeck("t", listOf(
            DeckWord("塗る", "ぬる", "paint"), DeckWord("言う", "いう", "say"), DeckWord("事", "こと", "thing"),
            DeckWord("今", "いま", "now"), DeckWord("前", "まえ", "front"),
        )))
        val engine = QuizEngine(Random(4))
        val item = Course.items(Track.DECK)[0]
        repeat(20) {
            val q = engine.question(item, Course.items(Track.DECK), QuestionType.KANJI_TO_READING)
            assertEquals(4, q.options.toSet().size)
            assertTrue(q.options.toString(), q.options.contains("める") || q.options.contains("ぬろ"))
        }
    }

    @Test fun checkCoversEachBasicHiraganaOnce() {
        val steps = KanaPool.checkSteps(QuizEngine(Random(1)), Random(1))
        val ids = steps.map { (it as Step.Ask).question.item.id }
        assertEquals(KanaPool.CHECK_SIZE, ids.toSet().size)
        assertTrue(steps.all { (it as Step.Ask).question.type == QuestionType.KANA_TO_ROMAJI })
    }

    @Test fun checkGivesFlatXpAndNoCombo() {
        val engine = QuizEngine(Random(5))
        val session = com.garton.kana.engine.QuizSession(
            com.garton.kana.engine.SessionMode.CHECK, null, KanaPool.checkSteps(engine, Random(5)), retry = { engine.question(it, KanaPool.basicHiragana()) },
        )
        while (!session.isFinished) {
            session.answer((session.current as Step.Ask).question.correctIndex)
            session.advance()
        }
        assertEquals(KanaPool.CHECK_SIZE * 2, session.xp)
        assertEquals(0, session.maxCombo)
        assertEquals(KanaPool.CHECK_SIZE, session.summary().firstTryCorrect)
    }

    @Test fun checkMarksKnownKanaAndPoolsTheRest() {
        val (state, missed) = KanaPool.applyCheck(ProgressState(), listOf("h:あ" to true, "h:ぬ" to false), now = 0)
        assertEquals(listOf("h:ぬ"), missed)
        assertEquals(setOf("h:ぬ"), state.taggedKana)
        assertEquals(3, state.items["h:あ"]!!.box)
        assertEquals(1, state.lastCheckScore)
    }

    @Test fun masteredKanaGraduateFromThePool() {
        val state = ProgressState(
            taggedKana = setOf("h:ぬ", "h:ね"),
            items = mapOf("h:ぬ" to ItemProgress(box = Srs.MASTERED_BOX, seen = 5), "h:ね" to ItemProgress(box = 1, seen = 1)),
        )
        assertEquals(setOf("h:ね"), KanaPool.graduate(state).taggedKana)
        val practice = KanaPool.practiceSteps(QuizEngine(Random(3)), state, Random(3))
        assertEquals(12, practice.size)
        assertNotEquals(0, practice.count { (it as Step.Ask).question.item.id == "h:ね" })
    }
}
