package com.garton.kana.storage

import android.content.Context
import com.garton.kana.model.ItemProgress
import com.garton.kana.model.ProgressState
import org.json.JSONArray
import org.json.JSONObject

/** Persists [ProgressState] as one JSON blob in SharedPreferences. */
class ProgressRepository(context: Context) {
    private val prefs = context.getSharedPreferences("kana_progress", Context.MODE_PRIVATE)

    fun load(): ProgressState {
        val raw = prefs.getString(KEY, null) ?: return ProgressState()
        return try {
            decode(JSONObject(raw))
        } catch (e: Exception) {
            ProgressState()
        }
    }

    fun save(state: ProgressState) {
        prefs.edit().putString(KEY, encode(state).toString()).apply()
    }

    private fun encode(s: ProgressState) = JSONObject().apply {
        put("totalXp", s.totalXp)
        put("items", JSONObject().apply {
            s.items.forEach { (id, p) -> put(id, JSONArray(listOf(p.box, p.due, p.seen, p.correct))) }
        })
        put("unitStars", JSONObject().apply { s.unitStars.forEach { (id, n) -> put(id, n) } })
        put("streak", s.streak)
        put("bestStreak", s.bestStreak)
        put("lastPracticeDay", s.lastPracticeDay)
        put("todayXp", s.todayXp)
        put("todayDay", s.todayDay)
        put("dailyGoal", s.dailyGoal)
        put("goalClaimedDay", s.goalClaimedDay)
        put("bestCombo", s.bestCombo)
        put("lightningBest", s.lightningBest)
        put("achievements", JSONArray(s.achievements.toList()))
        put("sessions", s.sessions)
        put("freezes", s.freezes)
        put("remindersOn", s.remindersOn)
    }

    private fun decode(o: JSONObject): ProgressState {
        val items = mutableMapOf<String, ItemProgress>()
        o.optJSONObject("items")?.let { obj ->
            obj.keys().forEach { id ->
                val a = obj.getJSONArray(id)
                items[id] = ItemProgress(a.getInt(0), a.getLong(1), a.getInt(2), a.getInt(3))
            }
        }
        val stars = mutableMapOf<String, Int>()
        o.optJSONObject("unitStars")?.let { obj -> obj.keys().forEach { stars[it] = obj.getInt(it) } }
        val achievements = mutableSetOf<String>()
        o.optJSONArray("achievements")?.let { a -> for (i in 0 until a.length()) achievements += a.getString(i) }
        val d = ProgressState()
        return ProgressState(
            totalXp = o.optInt("totalXp", d.totalXp),
            items = items,
            unitStars = stars,
            streak = o.optInt("streak", d.streak),
            bestStreak = o.optInt("bestStreak", d.bestStreak),
            lastPracticeDay = o.optLong("lastPracticeDay", d.lastPracticeDay),
            todayXp = o.optInt("todayXp", d.todayXp),
            todayDay = o.optLong("todayDay", d.todayDay),
            dailyGoal = o.optInt("dailyGoal", d.dailyGoal),
            goalClaimedDay = o.optLong("goalClaimedDay", d.goalClaimedDay),
            bestCombo = o.optInt("bestCombo", d.bestCombo),
            lightningBest = o.optInt("lightningBest", d.lightningBest),
            achievements = achievements,
            sessions = o.optInt("sessions", d.sessions),
            freezes = o.optInt("freezes", d.freezes),
            remindersOn = o.optBoolean("remindersOn", d.remindersOn),
        )
    }

    private companion object {
        const val KEY = "state_v1"
    }
}
