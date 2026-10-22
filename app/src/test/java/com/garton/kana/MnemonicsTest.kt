package com.garton.kana

import com.garton.kana.data.Course
import com.garton.kana.data.Mnemonics
import com.garton.kana.engine.AnkiImport
import com.garton.kana.model.ItemProgress
import com.garton.kana.model.ProgressState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MnemonicsTest {
    @Test fun builtInStoriesLoad() {
        val rest = Mnemonics.of("休")
        assertNotNull(rest)
        assertEquals("rest", rest!!.keyword)
        assertTrue(rest.story.contains("tree"))
        assertNull(Mnemonics.of("あ"))
    }

    @Test fun kanjiAreFoundInWords() {
        assertEquals(listOf("食"), Mnemonics.kanjiIn("食べる"))
        assertEquals(listOf("学", "生"), Mnemonics.kanjiIn("学生"))
        assertEquals(emptyList<String>(), Mnemonics.kanjiIn("ありがとう"))
    }

    @Test fun everyStoryIsComplete() {
        val text = javaClass.getResourceAsStream("/mnemonics.tsv")!!.bufferedReader().readLines().filter { !it.startsWith("#") }
        assertTrue(text.size > 1000)
        val kanji = text.map { it.substringBefore('|') }
        assertEquals("each kanji once", kanji.size, kanji.toSet().size)
        text.forEach { line ->
            val parts = line.split('|')
            assertEquals(line, 3, parts.size)
            assertTrue(line, parts[1].isNotBlank() && parts[2].length > 20)
        }
    }

    @Test fun vocabularyLessonKanjiMostlyCovered() {
        val kanji = Course.items.mapNotNull { it.kanji }.flatMap { Mnemonics.kanjiIn(it) }.toSet()
        val covered = kanji.count { Mnemonics.of(it) != null }
        assertTrue("$covered of ${kanji.size}", covered * 10 >= kanji.size * 8)
    }

    @Test fun deckProgressCanBeCleared() {
        val state = ProgressState(
            items = mapOf("d:食べる|たべる|to eat" to ItemProgress(box = 3, seen = 4), "h:あ" to ItemProgress(box = 2, seen = 2)),
            unitStars = mapOf("d-1" to 2, "h-vowels" to 3),
        )
        val cleared = AnkiImport.clearDeckProgress(state)
        assertEquals(setOf("h:あ"), cleared.items.keys)
        assertEquals(setOf("h-vowels"), cleared.unitStars.keys)
    }
}
