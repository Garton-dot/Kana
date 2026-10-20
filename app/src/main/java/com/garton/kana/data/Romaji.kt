package com.garton.kana.data

/** Kana → Hepburn romaji in the course's style (long vowels spelled out: "sayounara", "koohii"). */
object Romaji {
    private val digraphs = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo", "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho", "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo", "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo", "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo", "ぢゃ" to "ja", "ぢゅ" to "ju", "ぢょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo", "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "しぇ" to "she", "ちぇ" to "che", "じぇ" to "je", "てぃ" to "ti", "でぃ" to "di", "ふぁ" to "fa",
        "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo", "うぃ" to "wi", "うぇ" to "we", "ゔぁ" to "va",
    )
    private val singles = buildMap {
        val rows = listOf(
            "あいうえお" to listOf("a", "i", "u", "e", "o"), "かきくけこ" to listOf("ka", "ki", "ku", "ke", "ko"),
            "さしすせそ" to listOf("sa", "shi", "su", "se", "so"), "たちつてと" to listOf("ta", "chi", "tsu", "te", "to"),
            "なにぬねの" to listOf("na", "ni", "nu", "ne", "no"), "はひふへほ" to listOf("ha", "hi", "fu", "he", "ho"),
            "まみむめも" to listOf("ma", "mi", "mu", "me", "mo"), "らりるれろ" to listOf("ra", "ri", "ru", "re", "ro"),
            "がぎぐげご" to listOf("ga", "gi", "gu", "ge", "go"), "ざじずぜぞ" to listOf("za", "ji", "zu", "ze", "zo"),
            "だぢづでど" to listOf("da", "ji", "zu", "de", "do"), "ばびぶべぼ" to listOf("ba", "bi", "bu", "be", "bo"),
            "ぱぴぷぺぽ" to listOf("pa", "pi", "pu", "pe", "po"), "ぁぃぅぇぉ" to listOf("a", "i", "u", "e", "o"),
        )
        for ((kana, ro) in rows) kana.forEachIndexed { i, c -> put(c.toString(), ro[i]) }
        put("や", "ya"); put("ゆ", "yu"); put("よ", "yo"); put("わ", "wa"); put("を", "o"); put("ん", "n"); put("ゔ", "vu")
        put("ゃ", "ya"); put("ゅ", "yu"); put("ょ", "yo"); put("ゎ", "wa")
    }

    /** Returns null when the text holds anything but kana (kanji, Latin, symbols). */
    fun of(text: String): String? {
        val kana = buildString { for (c in text) append(if (c in 'ァ'..'ヶ') c - 0x60 else c) }
        val out = StringBuilder()
        var double = false
        var i = 0
        while (i < kana.length) {
            val c = kana[i]
            if (c == 'っ') { double = true; i++; continue }
            if (c == 'ー') { out.lastOrNull { it in "aeiou" }?.let { out.append(it) }; i++; continue }
            if (c == ' ' || c == '　' || c == '・') { out.append(' '); i++; continue }
            val pair = if (i + 1 < kana.length) kana.substring(i, i + 2) else ""
            val ro = digraphs[pair]?.also { i += 2 } ?: singles[c.toString()]?.also { i++ } ?: return null
            if (double) out.append(if (ro.startsWith("ch")) 't' else ro[0])
            double = false
            out.append(ro)
        }
        return out.toString()
    }
}
