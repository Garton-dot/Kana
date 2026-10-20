package com.garton.kana.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.garton.kana.engine.AnkiCard
import com.garton.kana.engine.AnkiCollection
import com.garton.kana.engine.AnkiFormatException
import com.garton.kana.engine.AnkiNote
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/** Reads an .apkg (a zip holding a SQLite collection) into an [AnkiCollection]. */
object AnkiReader {

    fun read(context: Context, uri: Uri): AnkiCollection {
        val db = File(context.cacheDir, "import.anki")
        var found: String? = null
        var sawNewFormatOnly = false
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw AnkiFormatException("Couldn't open that file.")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        // collection.anki21 beats the legacy stub collection.anki2 that newer exports also carry.
                        "collection.anki21", "collection.anki2" -> if (found != "collection.anki21") {
                            db.outputStream().use { zip.copyTo(it) }
                            found = entry.name
                        }
                        "collection.anki21b" -> sawNewFormatOnly = true
                    }
                }
            }
        }
        if (found == null) {
            throw AnkiFormatException(
                if (sawNewFormatOnly) "This export uses Anki's newest format. Export again with \"Support older Anki versions\" ticked."
                else "That doesn't look like an Anki deck (.apkg).",
            )
        }
        try {
            return SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use(::readCollection)
        } finally {
            db.delete()
        }
    }

    private fun readCollection(db: SQLiteDatabase): AnkiCollection {
        var createdSec = 0L
        var modelsJson = ""
        var decksJson = ""
        db.rawQuery("select crt, models, decks from col", null).use { c ->
            if (c.moveToFirst()) {
                createdSec = c.getLong(0)
                modelsJson = c.getString(1).orEmpty()
                decksJson = c.getString(2).orEmpty()
            }
        }

        // Field names per note type: in col.models (schema 11), or the notetypes/fields tables (schema 18).
        val fieldNames = mutableMapOf<Long, List<String>>()
        if (modelsJson.length > 2) {
            val models = JSONObject(modelsJson)
            for (key in models.keys()) {
                val flds = models.getJSONObject(key).getJSONArray("flds")
                fieldNames[key.toLong()] = List(flds.length()) { flds.getJSONObject(it).getString("name") }
            }
        } else {
            db.rawQuery("select ntid, name from fields order by ntid, ord", null).use { c ->
                val acc = mutableMapOf<Long, MutableList<String>>()
                while (c.moveToNext()) acc.getOrPut(c.getLong(0)) { mutableListOf() } += c.getString(1)
                fieldNames += acc
            }
        }

        val notes = mutableListOf<AnkiNote>()
        db.rawQuery("select id, mid, flds from notes", null).use { c ->
            while (c.moveToNext()) {
                notes += AnkiNote(c.getLong(0), fieldNames[c.getLong(1)].orEmpty(), c.getString(2).split('\u001f'))
            }
        }

        val cards = mutableListOf<AnkiCard>()
        val cardsPerDeck = mutableMapOf<Long, Int>()
        db.rawQuery("select nid, type, queue, ivl, due, reps, lapses, did from cards", null).use { c ->
            while (c.moveToNext()) {
                cards += AnkiCard(c.getLong(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getLong(4), c.getInt(5), c.getInt(6))
                cardsPerDeck.merge(c.getLong(7), 1) { a, b -> a + b }
            }
        }

        val mainDeck = cardsPerDeck.maxByOrNull { it.value }?.key
        val deckName = runCatching {
            if (decksJson.length > 2) JSONObject(decksJson).getJSONObject(mainDeck.toString()).getString("name")
            else db.rawQuery("select name from decks where id = ?", arrayOf(mainDeck.toString())).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.substringAfterLast("::") ?: "Anki deck"

        return AnkiCollection(deckName, createdSec, notes, cards)
    }
}
