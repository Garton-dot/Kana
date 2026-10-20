package com.garton.kana.storage

import android.content.Context
import com.garton.kana.data.DeckWord
import com.garton.kana.data.ImportedDeck
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The imported Anki deck, kept as a JSON file in app storage (never bundled with the app). */
class DeckRepository(context: Context) {
    private val file = File(context.filesDir, "deck.json")

    fun load(): ImportedDeck? = try {
        if (!file.exists()) null else {
            val o = JSONObject(file.readText())
            val a = o.getJSONArray("words")
            ImportedDeck(o.getString("name"), List(a.length()) { i ->
                val w = a.getJSONArray(i)
                DeckWord(w.getString(0), w.getString(1), w.getString(2), w.optString(3).ifEmpty { null }, w.optString(4).ifEmpty { null })
            })
        }
    } catch (e: Exception) {
        null
    }

    fun save(deck: ImportedDeck) {
        val words = JSONArray()
        deck.words.forEach { w -> words.put(JSONArray(listOf(w.word, w.reading, w.meaning, w.sentence.orEmpty(), w.sentenceMeaning.orEmpty()))) }
        file.writeText(JSONObject().put("name", deck.name).put("words", words).toString())
    }

    fun clear() {
        file.delete()
    }
}
