package com.garton.kana.data

import com.garton.kana.model.ItemKind
import com.garton.kana.model.StudyItem
import com.garton.kana.model.StudyUnit
import com.garton.kana.model.Track

/** Hiragana rows; katakana is derived by shifting each code point. */
private data class Row(val key: String, val title: String, val chars: List<Pair<String, String>>)

private val ROWS = listOf(
    Row("vowels", "Vowels", listOf("あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o")),
    Row("k", "K row", listOf("か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko")),
    Row("s", "S row", listOf("さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so")),
    Row("t", "T row", listOf("た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to")),
    Row("n", "N row", listOf("な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no")),
    Row("h", "H row", listOf("は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho")),
    Row("m", "M row", listOf("ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo")),
    Row("y", "Y row", listOf("や" to "ya", "ゆ" to "yu", "よ" to "yo")),
    Row("r", "R row", listOf("ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro")),
    Row("w", "W row + n", listOf("わ" to "wa", "を" to "wo", "ん" to "n")),
    Row("g", "G (dakuten)", listOf("が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go")),
    Row("z", "Z (dakuten)", listOf("ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo")),
    Row("d", "D (dakuten)", listOf("だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do")),
    Row("b", "B (dakuten)", listOf("ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo")),
    Row("p", "P (handakuten)", listOf("ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po")),
    Row("y1", "Combos I", listOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
    )),
    Row("y2", "Combos II", listOf(
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
    )),
    Row("y3", "Combos III", listOf(
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
    )),
)

/** Hiragana → katakana: the blocks are exactly 0x60 apart. */
fun toKatakana(hiragana: String): String = buildString {
    for (c in hiragana) append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
}

private fun buildTrack(track: Track): List<StudyUnit> {
    val prefix = if (track == Track.HIRAGANA) "h" else "k"
    return ROWS.map { row ->
        val unitId = "$prefix-${row.key}"
        val items = row.chars.map { (hira, romaji) ->
            val jp = if (track == Track.HIRAGANA) hira else toKatakana(hira)
            StudyItem(id = "$prefix:$jp", kind = ItemKind.KANA, japanese = jp, romaji = romaji, unitId = unitId)
        }
        val preview = items.take(5).joinToString(" ") { it.japanese }
        StudyUnit(unitId, track, row.title, preview, items)
    }
}

val HIRAGANA_UNITS: List<StudyUnit> = buildTrack(Track.HIRAGANA)
val KATAKANA_UNITS: List<StudyUnit> = buildTrack(Track.KATAKANA)
