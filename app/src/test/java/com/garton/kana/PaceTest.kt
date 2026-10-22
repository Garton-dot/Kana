package com.garton.kana

import com.garton.kana.data.Course
import com.garton.kana.data.DeckWord
import com.garton.kana.data.ImportedDeck
import com.garton.kana.engine.Pace
import com.garton.kana.engine.Progression
import com.garton.kana.engine.SessionMode
import com.garton.kana.engine.SessionSummary
import com.garton.kana.model.ItemProgress
import com.garton.kana.model.ProgressState
import com.garton.kana.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PaceTest {
    @Before fun deck() = Course.install(ImportedDeck("t", List(1000) { DeckWord("語$it", "ご", "word $it") }))
    @After fun clear() = Course.install(null)

    @Test fun newDeckWordsAreLoggedOncePerSession() {
        val ids = Course.items(Track.DECK).take(3).map { it.id }
        val summary = SessionSummary(SessionMode.LESSON, "d-1", 5, 5, 50, 5, ids.map { it to true } + (ids[0] to true) + ("h:あ" to true))
        val (after, _) = Progression.completeSession(ProgressState(), summary, now = 0, today = 50)
        assertEquals(mapOf(50L to 3), after.newWordsByDay)
        val (again, _) = Progression.completeSession(after, summary, now = 0, today = 51)
        assertEquals(mapOf(50L to 3), again.newWordsByDay) // already seen: not new
    }

    @Test fun paceNeedsAFewDaysThenProjects() {
        val learned = Course.items(Track.DECK).take(100).associate { it.id to ItemProgress(box = 1, seen = 1) }
        assertNull(Pace.of(ProgressState(items = learned, newWordsByDay = mapOf(100L to 10)), today = 100))
        val state = ProgressState(items = learned, newWordsByDay = (91L..100L).associateWith { 10 })
        val pace = Pace.of(state, today = 100)!!
        assertEquals(10.0, pace.perDay, 0.001)
        assertEquals(10, pace.days)
        assertEquals(90, pace.daysToFinish) // 900 left at 10 a day
        assertEquals(70, pace.daysToMilestone) // 700 to the 800 mark
    }

    @Test fun oldDaysFallOutOfTheWindow() {
        val state = ProgressState(newWordsByDay = mapOf(60L to 500, 99L to 14))
        val pace = Pace.of(state, today = 100)!!
        assertEquals(1.0, pace.perDay, 0.001) // only the last 14 days count
    }
}
