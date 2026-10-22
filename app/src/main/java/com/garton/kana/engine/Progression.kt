package com.garton.kana.engine

import com.garton.kana.data.Course
import com.garton.kana.model.ItemKind
import com.garton.kana.model.ProgressState
import com.garton.kana.model.StudyItem
import com.garton.kana.model.StudyUnit
import com.garton.kana.model.Track

data class SessionSummary(
    val mode: SessionMode,
    val unitId: String?,
    val firstTryCorrect: Int,
    val questions: Int,
    val xp: Int,
    val maxCombo: Int,
    val answers: List<Pair<String, Boolean>>,
)

data class Rewards(
    val xp: Int,
    val goalBonus: Int,
    val stars: Int?,
    val newAchievements: List<Achievement>,
    val leveledUpTo: Int?,
    val unlockedUnit: StudyUnit?,
    val streak: Int,
    val streakIncreased: Boolean,
    val earnedFreeze: Boolean,
    val newLightningBest: Boolean,
)

object Progression {

    fun isUnlocked(state: ProgressState, unit: StudyUnit): Boolean {
        val list = Course.units(unit.track)
        val index = list.indexOf(unit)
        return index <= 0 || (state.unitStars[list[index - 1].id] ?: 0) >= 1 ||
            unit.items.any { (state.items[it.id]?.seen ?: 0) > 0 } // e.g. words studied in Anki before importing
    }

    fun seenCount(state: ProgressState, unit: StudyUnit): Int =
        unit.items.count { (state.items[it.id]?.seen ?: 0) > 0 }

    fun masteredCount(state: ProgressState, items: List<StudyItem>): Int =
        items.count { (state.items[it.id]?.box ?: 0) >= Srs.MASTERED_BOX }

    fun dueItems(state: ProgressState, now: Long): List<StudyItem> =
        Course.items.filter { Srs.isDue(state.items[it.id], now) }.sortedBy { state.items[it.id]!!.due }

    /** Items of the unlocked units in [track]: the only things a learner should see as answer choices. */
    fun unlockedItems(state: ProgressState, track: Track): List<StudyItem> =
        Course.units(track).filter { isUnlocked(state, it) }.flatMap { it.items }

    fun seenItems(state: ProgressState, kind: ItemKind? = null): List<StudyItem> =
        Course.items.filter { (kind == null || it.kind == kind) && (state.items[it.id]?.seen ?: 0) > 0 }

    /** The unit the big "Continue" button should open, within [track] when it has any units. */
    fun nextUnit(state: ProgressState, track: Track? = null): StudyUnit {
        val pool = track?.let { Course.units(it) }?.ifEmpty { null } ?: Course.units
        val unlocked = pool.filter { isUnlocked(state, it) }
        return unlocked.firstOrNull { (state.unitStars[it.id] ?: 0) == 0 }
            ?: unlocked.minByOrNull { state.unitStars[it.id] ?: 0 }
            ?: Course.units.first()
    }

    /** The streak as it stands today: 0 once a day was missed and no freeze covers it. */
    fun currentStreak(state: ProgressState, today: Long): Int = when {
        state.lastPracticeDay >= today - 1 -> state.streak
        state.lastPracticeDay == today - 2 && state.freezes > 0 -> state.streak
        else -> 0
    }

    fun todayXp(state: ProgressState, today: Long): Int = if (state.todayDay == today) state.todayXp else 0

    fun applyAnswers(state: ProgressState, answers: List<Pair<String, Boolean>>, now: Long): ProgressState {
        val items = state.items.toMutableMap()
        for ((id, correct) in answers) items[id] = Srs.onAnswer(items[id], correct, now)
        return state.copy(items = items)
    }

    fun completeSession(state: ProgressState, summary: SessionSummary, now: Long, today: Long): Pair<ProgressState, Rewards> {
        var s = Pace.record(state, summary.answers, today, applyAnswers(state, summary.answers, now))

        // Streak, bridging a single missed day with a freeze.
        val last = s.lastPracticeDay
        var freezes = s.freezes
        val streak = when {
            last == today -> s.streak
            last == today - 1 -> s.streak + 1
            last == today - 2 && freezes > 0 -> { freezes--; s.streak + 1 }
            else -> 1
        }
        val streakIncreased = last != today
        val earnedFreeze = streakIncreased && streak % 7 == 0 && freezes < Gamify.MAX_FREEZES
        if (earnedFreeze) freezes++

        // XP and the daily goal chest.
        val todayBefore = todayXp(s, today)
        var todayXp = todayBefore + summary.xp
        val goalBonus = if (todayXp >= s.dailyGoal && s.goalClaimedDay != today) Gamify.GOAL_BONUS_XP else 0
        todayXp += goalBonus
        val levelBefore = Gamify.level(s.totalXp)
        val totalXp = s.totalXp + summary.xp + goalBonus

        // Stars, granted once every item in the unit has been met.
        val unit = summary.unitId?.let { Course.unit(it) }
        var stars: Int? = null
        var unlocked: StudyUnit? = null
        val unitStars = s.unitStars.toMutableMap()
        if (unit != null && summary.mode == SessionMode.LESSON && seenCount(s, unit) == unit.items.size) {
            stars = Gamify.stars(summary.firstTryCorrect, summary.questions)
            val before = unitStars[unit.id] ?: 0
            if (stars > before) unitStars[unit.id] = stars
            if (before == 0 && stars >= 1) {
                val list = Course.units(unit.track)
                unlocked = list.getOrNull(list.indexOf(unit) + 1)
            }
        }

        val lightningScore = if (summary.mode == SessionMode.LIGHTNING) summary.firstTryCorrect else 0
        val newLightningBest = lightningScore > s.lightningBest

        s = s.copy(
            totalXp = totalXp,
            todayXp = todayXp,
            todayDay = today,
            goalClaimedDay = if (goalBonus > 0) today else s.goalClaimedDay,
            streak = streak,
            bestStreak = maxOf(s.bestStreak, streak),
            lastPracticeDay = today,
            freezes = freezes,
            unitStars = unitStars,
            bestCombo = maxOf(s.bestCombo, summary.maxCombo),
            lightningBest = maxOf(s.lightningBest, lightningScore),
            sessions = s.sessions + 1,
        )

        val newAchievements = Achievement.entries.filter { it.name !in s.achievements && it.earned(s) }
        s = s.copy(achievements = s.achievements + newAchievements.map { it.name })

        val levelAfter = Gamify.level(s.totalXp)
        return s to Rewards(
            xp = summary.xp,
            goalBonus = goalBonus,
            stars = stars,
            newAchievements = newAchievements,
            leveledUpTo = if (levelAfter > levelBefore) levelAfter else null,
            unlockedUnit = unlocked,
            streak = streak,
            streakIncreased = streakIncreased,
            earnedFreeze = earnedFreeze,
            newLightningBest = newLightningBest,
        )
    }
}
