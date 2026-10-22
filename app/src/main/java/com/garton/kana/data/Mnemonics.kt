package com.garton.kana.data

/** A kanji's keyword and a story built from its parts. */
data class KanjiNote(val kanji: String, val keyword: String, val story: String)

/** Built-in mnemonics, one per kanji, from resources/mnemonics.tsv. */
object Mnemonics {
    private val notes: Map<String, KanjiNote> by lazy {
        val stream = Mnemonics::class.java.getResourceAsStream("/mnemonics.tsv") ?: return@lazy emptyMap()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split('|', limit = 3) }
                .filter { it.size == 3 }
                .associate { (k, keyword, story) -> k to KanjiNote(k, keyword, story) }
        }
    }

    fun isKanji(c: Char) = c in '一'..'鿿' || c in '㐀'..'䶿'

    fun of(kanji: String): KanjiNote? = notes[kanji]

    /** Each distinct kanji in [text], in order, whether or not a built-in note exists for it. */
    fun kanjiIn(text: String): List<String> = text.filter(::isKanji).map { it.toString() }.distinct()
}
