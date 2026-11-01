package com.freshernotes.app.search

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class DocKind { TOPIC, SESSION, ENTRY }

data class SearchHit(val kind: DocKind, val refId: Long, val title: String, val snippet: String)

/**
 * Full-text search over topic pages and session transcripts using SQLite FTS5.
 * Android's framework SQLite is not built with FTS5, so this index uses the bundled SQLite from
 * androidx.sqlite. It is derived data: Room stays the source of truth and the index can be rebuilt.
 */
@Singleton
class SearchIndex internal constructor(private val file: File?) {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "search.db"))

    private val connection: SQLiteConnection by lazy {
        BundledSQLiteDriver().open(file?.absolutePath ?: ":memory:").also { c ->
            c.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS docs USING fts5(" +
                    "kind UNINDEXED, ref_id UNINDEXED, title, body, tokenize = 'porter unicode61')",
            )
        }
    }

    suspend fun upsert(kind: DocKind, refId: Long, title: String, body: String) = io {
        delete(kind, refId)
        connection.prepare("INSERT INTO docs(kind, ref_id, title, body) VALUES (?, ?, ?, ?)").use { st ->
            st.bindText(1, kind.name)
            st.bindLong(2, refId)
            st.bindText(3, title)
            st.bindText(4, body)
            st.step()
        }
    }

    suspend fun remove(kind: DocKind, refId: Long) = io { delete(kind, refId) }

    private fun delete(kind: DocKind, refId: Long) {
        connection.prepare("DELETE FROM docs WHERE kind = ? AND ref_id = ?").use { st ->
            st.bindText(1, kind.name)
            st.bindLong(2, refId)
            st.step()
        }
    }

    /** Ranked (bm25, title weighted x3) search. Free text is turned into a prefix AND query. */
    suspend fun search(query: String, limit: Int = 30): List<SearchHit> = io {
        val match = toMatchExpression(query) ?: return@io emptyList()
        connection.prepare(
            "SELECT kind, ref_id, title, snippet(docs, 3, '**', '**', '…', 12) FROM docs " +
                "WHERE docs MATCH ? ORDER BY bm25(docs, 0.0, 0.0, 3.0, 1.0) LIMIT ?",
        ).use { st ->
            st.bindText(1, match)
            st.bindLong(2, limit.toLong())
            val hits = ArrayList<SearchHit>()
            while (st.step()) {
                hits += SearchHit(DocKind.valueOf(st.getText(0)), st.getLong(1), st.getText(2), st.getText(3))
            }
            hits
        }
    }

    suspend fun clear() = io { connection.execSQL("DELETE FROM docs") }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { synchronized(this@SearchIndex) { block() } }

    companion object {
        fun inMemory() = SearchIndex(null)

        /** "drill jig" -> "\"drill\"* AND \"jig\"*"; quotes neutralize FTS5 syntax characters in user input. */
        fun toMatchExpression(query: String): String? {
            val terms = query.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
            if (terms.isEmpty()) return null
            return terms.joinToString(" AND ") { "\"${it.lowercase()}\"*" }
        }
    }
}
