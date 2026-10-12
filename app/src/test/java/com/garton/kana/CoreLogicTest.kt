package com.garton.kana

import com.garton.kana.data.Course
import com.garton.kana.data.toKatakana
import com.garton.kana.engine.Achievement
import com.garton.kana.engine.Gamify
import com.garton.kana.engine.Progression
import com.garton.kana.engine.QuizEngine
import com.garton.kana.engine.QuizSession
import com.garton.kana.engine.SessionMode
import com.garton.kana.engine.SessionSummary
import com.garton.kana.engine.Srs
import com.garton.kana.engine.Step
import com.garton.kana.model.ProgressState
import com.garton.kana.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CoreLogicTest {

    @Test fun courseHasUniqueIdsAndExpectedSizes() {
        assertEquals(Course.items.size, Course.items.map { it.id }.toSet().size)
        assertEquals(104, Course.items(Track.HIRAGANA).size)
        assertEquals(104, Course.items(Track.KATAKANA).size)
        assertEquals(5, Course.units(Track.VOCAB).size)
        Course.units(Track.VOCAB).forEach { assertEquals(25, it.items.size) }
    }

    @Test fun katakanaConversion() {
        assertEquals("カ", toKatakana("か"))
        assertEquals("キャ", toKatakana("きゃ"))
        assertEquals("ン", toKatakana("ん"))
        assertEquals("ヲ", toKatakana("を"))
    }

    @Test fun questionsHaveFourDistinctOptionsAndOneRightAnswer() {
        val engine = QuizEngine(Random(1))
        for (item in Course.items) {
            for (type in engine.types(item)) {
                val q = engine.question(item, Course.units.first { it.id == item.unitId }.items, type)
                assertEquals(4, q.options.size)
                assertEquals(4, q.options.toSet().size)
                assertEquals(engine.answerOf(item, type), q.options[q.correctIndex])
            }
        }
    }

    @Test fun jiDistractorIsNeverTheOtherJi() {
        val engine = QuizEngine(Random(3))
        val ji = Course.item("h:じ")!!
        val pool = Course.items(Track.HIRAGANA)
        repeat(200) {
            val q = engine.question(ji, pool, engine.types(ji)[1])
            assertFalse("ぢ" in q.options)
        }
    }

    @Test fun srsPromotesAndDemotes() {
        var p = Srs.onAnswer(null, true, 0)
        assertEquals(1, p.box)
        repeat(10) { p = Srs.onAnswer(p, true, 0) }
        assertEquals(Srs.MAX_BOX, p.box)
        p = Srs.onAnswer(p, false, 0)
        assertEquals(3, p.box)
        assertTrue(Srs.isDue(p, p.due))
    }

    @Test fun levelsAndMultiplier() {
        assertEquals(1, Gamify.level(0))
        assertEquals(1, Gamify.level(99))
        assertEquals(2, Gamify.level(100))
        assertEquals(3, Gamify.level(300))
        assertEquals(10, Gamify.xpFor(0))
        assertEquals(15, Gamify.xpFor(5))
        assertEquals(30, Gamify.xpFor(25))
    }

    @Test fun lessonTeachesNewItemsAndRequeuesMistakes() {
        val engine = QuizEngine(Random(7))
        val unit = Course.units.first()
        val steps = engine.lesson(unit, ProgressState(), unit.items)
        assertEquals(5, steps.count { it is Step.Teach })
        assertEquals(12, steps.count { it is Step.Ask })

        val session = QuizSession(SessionMode.LESSON, unit.id, steps, retry = { engine.question(it, unit.items) })
        var wrongOnce = false
        while (!session.isFinished) {
            val step = session.current!!
            if (step is Step.Ask) {
                val q = step.question
                val choice = if (!wrongOnce) (q.correctIndex + 1) % 4 else q.correctIndex
                wrongOnce = true
                session.answer(choice)
            }
            session.advance()
        }
        val summary = session.summary()
        assertEquals(12, summary.questions)
        assertEquals(11, summary.firstTryCorrect)
        assertEquals(12, summary.maxCombo) // 11 first tries + the retry
    }

    @Test fun completingAUnitAwardsStarsUnlocksAndStreaks() {
        val unit = Course.units.first()
        val answers = unit.items.map { it.id to true }
        val summary = SessionSummary(SessionMode.LESSON, unit.id, 12, 12, 150, 12, answers)
        val (state, rewards) = Progression.completeSession(ProgressState(), summary, now = 0, today = 100)
        assertEquals(3, rewards.stars)
        assertEquals(Course.units[1], rewards.unlockedUnit)
        assertTrue(Progression.isUnlocked(state, Course.units[1]))
        assertEquals(1, state.streak)
        assertEquals(Gamify.GOAL_BONUS_XP, rewards.goalBonus)
        assertTrue(Achievement.FIRST_STEPS in rewards.newAchievements)
        assertTrue(Achievement.COMBO_10 in rewards.newAchievements)
        assertNotNull(rewards.leveledUpTo)

        val (next, again) = Progression.completeSession(state, summary.copy(answers = emptyList()), now = 0, today = 101)
        assertEquals(2, next.streak)
        assertEquals(Gamify.GOAL_BONUS_XP, again.goalBonus) // chest resets each day
        assertEquals(0, Progression.currentStreak(next, 104))
    }

    @Test fun freezeBridgesOneMissedDay() {
        val state = ProgressState(streak = 6, lastPracticeDay = 10, freezes = 1)
        assertEquals(6, Progression.currentStreak(state, 12))
        val summary = SessionSummary(SessionMode.REVIEW, null, 1, 1, 10, 1, emptyList())
        val (after, rewards) = Progression.completeSession(state, summary, 0, 12)
        assertEquals(7, after.streak)
        assertTrue(rewards.earnedFreeze)
        assertEquals(1, after.freezes)
    }
}
