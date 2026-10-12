package com.garton.kana.data

import com.garton.kana.model.StudyItem
import com.garton.kana.model.StudyUnit
import com.garton.kana.model.Track

object Course {
    val units: List<StudyUnit> = HIRAGANA_UNITS + KATAKANA_UNITS + VOCAB_UNITS
    val items: List<StudyItem> = units.flatMap { it.items }

    private val itemsById = items.associateBy { it.id }
    private val unitsById = units.associateBy { it.id }

    fun item(id: String): StudyItem? = itemsById[id]
    fun unit(id: String): StudyUnit? = unitsById[id]
    fun units(track: Track): List<StudyUnit> = units.filter { it.track == track }
    fun items(track: Track): List<StudyItem> = units(track).flatMap { it.items }
}
