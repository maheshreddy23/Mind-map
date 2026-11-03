package com.maheshreddy.mym.core.library

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A candidate entry for Ask, with what retrieval needs to know about it. */
data class Candidate(val entryId: Long, val privacy: Privacy, val vector: FloatArray?)

/**
 * Ask's retrieval (requirements-v2 §7): search by meaning (cosine similarity of sentence embeddings) and by words
 * (FTS5 bm25 order), fused with Reciprocal Rank Fusion. Entries hidden by the privacy filter are never offered
 * to the model. If nothing is close enough in meaning and no words match, the answer is "nothing relevant".
 */
object Retrieval {
    /** Cosine similarity below this is "not about the question" for the small embedders used here. */
    const val MIN_SIMILARITY = 0.45f
    private const val RRF_K = 60.0

    data class Hit(val entryId: Long, val score: Double, val similarity: Float?, val keywordRank: Int?)

    /**
     * [keywordIds] are FTS5 matches, best first. [query] is the question's embedding (null when no embedding model
     * is installed: keyword search only). [allowed] are the privacy levels the user chose to include.
     */
    fun rank(
        query: FloatArray?,
        candidates: List<Candidate>,
        keywordIds: List<Long>,
        allowed: Set<Privacy> = Privacy.entries.toSet(),
        limit: Int = 5,
    ): List<Hit> {
        val byId = candidates.associateBy { it.entryId }
        val visible = candidates.filter { it.privacy in allowed }.map { it.entryId }.toSet()
        val semantic = if (query == null) emptyList() else candidates
            .filter { it.entryId in visible && it.vector != null && it.vector.size == query.size }
            .map { it.entryId to cosine(query, it.vector!!) }
            .filter { it.second >= MIN_SIMILARITY }
            .sortedByDescending { it.second }
        val keyword = keywordIds.filter { it in visible && it in byId }
        val semRank = semantic.mapIndexed { i, (id, _) -> id to i }.toMap()
        val kwRank = keyword.mapIndexed { i, id -> id to i }.toMap()
        val sims = semantic.toMap()
        return (semRank.keys + kwRank.keys).map { id ->
            val score = (semRank[id]?.let { 1.0 / (RRF_K + it + 1) } ?: 0.0) + (kwRank[id]?.let { 1.0 / (RRF_K + it + 1) } ?: 0.0)
            Hit(id, score, sims[id], kwRank[id])
        }.sortedByDescending { it.score }.take(limit)
    }

    /** Vectors are stored L2-normalised, so this is a dot product (guarded for safety). */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0.0 || nb == 0.0) 0f else (dot / (Math.sqrt(na) * Math.sqrt(nb))).toFloat()
    }

    fun toBytes(v: FloatArray): ByteArray = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { v.forEach { putFloat(it) } }.array()

    fun fromBytes(b: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(b.size / 4) { buf.getFloat() }
    }

    /** The text that represents an entry for embedding. */
    fun entryText(title: String, body: String, topic: String?) = buildString {
        topic?.let { append(it).append(": ") }
        append(title).append('\n').append(body.lines().joinToString(" ") { it.removePrefix("- ").trim() })
    }.take(2_000)

    /**
     * Ask's empty state: 3–4 questions suggested from the user's own topics (no model needed). [topics] are
     * (title, open questions) pairs, most recently updated first.
     */
    fun suggestions(topics: List<Pair<String, Int>>, subjects: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        topics.firstOrNull { it.second > 0 }?.let { out += "What is still open on ${it.first}?" }
        topics.take(2).forEach { out += "What have I said about ${it.first}?" }
        subjects.firstOrNull()?.let { out += "What lessons have I recorded about $it?" }
        if (topics.size >= 2) out += "How does ${topics[0].first} connect to ${topics[1].first}?"
        return out.take(4)
    }
}
