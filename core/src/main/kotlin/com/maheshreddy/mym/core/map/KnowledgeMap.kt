package com.maheshreddy.mym.core.map

import com.maheshreddy.mym.core.library.EntryType
import com.maheshreddy.mym.core.library.Privacy
import com.maheshreddy.mym.core.library.Retrieval
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A subject on the map. [id] is null for a starting subject that has not been created yet (empty library). */
data class MapSubject(val id: Long?, val title: String)

data class MapTopicIn(val id: Long, val subjectId: Long?, val title: String, val summary: String = "")

data class MapEntryIn(
    val topicId: Long,
    val createdAt: Long,
    val privacy: Privacy,
    val type: EntryType,
    val text: String,
    val vector: FloatArray? = null,
)

enum class LinkState { SUGGESTED, CONFIRMED }

/** A link between two topics, [a] < [b]. Suggested links are drawn dashed until the user confirms them. */
data class MapLink(val a: Long, val b: Long, val state: LinkState, val strength: Float)

data class MapNode(
    val id: Long,
    val title: String,
    val subject: String?,
    val summary: String,
    val cluster: Int,
    val entries: Int,
    val openQuestions: Int,
    /** First entry's date: when the topic was added (the Growth animation plays topics in this order). */
    val addedAt: Long,
    val gap: Boolean,
    val x: Float,
    val y: Float,
    val r: Float,
)

data class MapCluster(val index: Int, val subject: MapSubject, val x: Float, val y: Float, val r: Float, val empty: Boolean)

data class MapLayout(
    val clusters: List<MapCluster>,
    val nodes: List<MapNode>,
    val links: List<MapLink>,
    val minX: Float, val minY: Float, val maxX: Float, val maxY: Float,
) {
    val gaps: Int get() = nodes.count { it.gap } + clusters.count { it.empty }
    val isEmpty: Boolean get() = nodes.isEmpty()

    companion object { val EMPTY = MapLayout(emptyList(), emptyList(), emptyList(), 0f, 0f, 0f, 0f) }
}

/** What a tap on the map lands on. */
sealed interface MapHit {
    data class Topic(val node: MapNode) : MapHit
    data class Subject(val cluster: MapCluster) : MapHit
    data class Link(val link: MapLink) : MapHit
}

enum class MapPeriod(val label: String, val days: Int?) { ALL("All", null), MONTH("This month", 30), WEEK("This week", 7) }

/**
 * The knowledge map (on-device, no network): subjects as clusters, topics as nodes sized by entry count, links
 * between related topics, and gaps (thin topics and empty subjects). Pure functions, so layout and linking are
 * unit-tested; the Compose screen only draws the result.
 */
object KnowledgeMap {
    /** Two topics are related when they share this much vocabulary (Jaccard of their terms)… */
    const val TERM_BAR = 0.12f
    /** …or their average entry embeddings are this close. Topic averages sit higher than single texts. */
    const val VECTOR_BAR = 0.75f
    const val MAX_LINKS_PER_TOPIC = 3

    private val stop = setOf(
        "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was", "one", "our", "out",
        "has", "his", "how", "its", "may", "new", "now", "see", "two", "way", "who", "did", "get", "use", "with", "this",
        "that", "from", "they", "have", "what", "when", "where", "which", "will", "would", "there", "their", "about",
        "into", "than", "then", "them", "these", "those", "your", "also", "just", "like", "more", "most", "some", "such",
        "only", "over", "very", "each", "because", "should", "could", "being", "been", "were", "does", "done", "make",
        "made", "used", "using", "always", "never", "usually", "after", "before", "need", "needs", "same", "other",
    )

    /** Content words of a text, lower-cased, with a light plural strip, so "jigs" and "jig" match. */
    fun terms(text: String): Set<String> = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 3 && it !in stop && !it.all(Char::isDigit) }
        .map { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }
        .toSet()

    fun jaccard(a: Set<String>, b: Set<String>): Float =
        if (a.isEmpty() || b.isEmpty()) 0f else a.intersect(b).size.toFloat() / a.union(b).size

    /** A topic is a gap when it is thin (one entry or none) or still has open questions. */
    fun isGap(entries: Int, openQuestions: Int) = entries <= 1 || openQuestions > 0

    private fun key(a: Long, b: Long) = if (a < b) a to b else b to a

    /**
     * Related topics. Confirmed links always show; dismissed ones never come back; the rest are the butler's
     * suggestions (at most [MAX_LINKS_PER_TOPIC] per topic, strongest first).
     */
    fun links(
        topicTerms: Map<Long, Set<String>>,
        topicVectors: Map<Long, FloatArray>,
        confirmed: Set<Pair<Long, Long>>,
        dismissed: Set<Pair<Long, Long>>,
        termBar: Float = TERM_BAR,
        vectorBar: Float = VECTOR_BAR,
    ): List<MapLink> {
        val ids = topicTerms.keys.sorted()
        val out = HashMap<Pair<Long, Long>, MapLink>()
        val suggested = ArrayList<MapLink>()
        for (i in ids.indices) for (j in i + 1 until ids.size) {
            val k = key(ids[i], ids[j])
            if (k in dismissed) continue
            val jac = jaccard(topicTerms[k.first].orEmpty(), topicTerms[k.second].orEmpty())
            val va = topicVectors[k.first]; val vb = topicVectors[k.second]
            val cos = if (va != null && vb != null && va.size == vb.size) Retrieval.cosine(va, vb) else 0f
            // Map both signals to 0..1 above their bars, keep the stronger.
            val strength = max(
                if (jac >= termBar) 0.5f + 0.5f * ((jac - termBar) / (1 - termBar)) else 0f,
                if (cos >= vectorBar) 0.5f + 0.5f * ((cos - vectorBar) / (1 - vectorBar)) else 0f,
            )
            if (k in confirmed) out[k] = MapLink(k.first, k.second, LinkState.CONFIRMED, max(strength, 0.5f))
            else if (strength > 0f) suggested += MapLink(k.first, k.second, LinkState.SUGGESTED, strength)
        }
        confirmed.filter { it.first in topicTerms && it.second in topicTerms }.forEach { k ->
            out.getOrPut(key(k.first, k.second)) { MapLink(minOf(k.first, k.second), maxOf(k.first, k.second), LinkState.CONFIRMED, 0.5f) }
        }
        val perTopic = HashMap<Long, Int>()
        suggested.sortedByDescending { it.strength }.forEach { l ->
            if ((perTopic[l.a] ?: 0) < MAX_LINKS_PER_TOPIC && (perTopic[l.b] ?: 0) < MAX_LINKS_PER_TOPIC) {
                out[l.a to l.b] = l
                perTopic[l.a] = (perTopic[l.a] ?: 0) + 1
                perTopic[l.b] = (perTopic[l.b] ?: 0) + 1
            }
        }
        return out.values.sortedWith(compareBy({ it.a }, { it.b }))
    }

    fun nodeRadius(entries: Int): Float = 18f + 9f * sqrt(entries.coerceAtLeast(0).toFloat())

    /**
     * Builds the map. Entries outside [privacy] or older than [since] are left out; a topic left with no entries
     * is hidden unless no filter is active (then it shows as a gap). With no topics at all, [starters] become empty
     * clusters ("Start here").
     */
    fun build(
        subjects: List<MapSubject>,
        topics: List<MapTopicIn>,
        entries: List<MapEntryIn>,
        confirmed: Set<Pair<Long, Long>> = emptySet(),
        dismissed: Set<Pair<Long, Long>> = emptySet(),
        privacy: Set<Privacy> = Privacy.entries.toSet(),
        since: Long? = null,
        starters: List<String> = emptyList(),
    ): MapLayout {
        val filtering = privacy.size < Privacy.entries.size || since != null
        val byTopic = entries.filter { it.privacy in privacy && (since == null || it.createdAt >= since) }.groupBy { it.topicId }
        val visible = topics.filter { !filtering || byTopic[it.id].orEmpty().isNotEmpty() }
        val clusterSubjects = when {
            subjects.isEmpty() && visible.isEmpty() -> starters.map { MapSubject(null, it) }
            else -> subjects + if (visible.any { it.subjectId == null }) listOf(MapSubject(null, UNSHELVED)) else emptyList()
        }
        val terms = visible.associate { t -> t.id to terms(t.title + " " + byTopic[t.id].orEmpty().joinToString(" ") { it.text }) }
        val vectors = visible.mapNotNull { t -> mean(byTopic[t.id].orEmpty().mapNotNull { it.vector })?.let { t.id to it } }.toMap()
        val links = links(terms, vectors, confirmed, dismissed)
        return layout(clusterSubjects, visible, byTopic, links)
    }

    /** Label of the cluster for topics that are not on a subject shelf yet (a grouping, not a subject). */
    const val UNSHELVED = "Not on a shelf yet"

    private fun mean(vs: List<FloatArray>): FloatArray? {
        if (vs.isEmpty()) return null
        val dim = vs[0].size
        val m = FloatArray(dim)
        vs.filter { it.size == dim }.forEach { v -> for (i in 0 until dim) m[i] += v[i] }
        return m
    }

    private fun layout(subjects: List<MapSubject>, topics: List<MapTopicIn>, byTopic: Map<Long, List<MapEntryIn>>, links: List<MapLink>): MapLayout {
        if (subjects.isEmpty()) return MapLayout.EMPTY
        data class Local(val topic: MapTopicIn, val dx: Float, val dy: Float, val r: Float)
        val names = subjects.associate { (it.id ?: -1L) to it.title }
        val groups = subjects.map { s -> topics.filter { (it.subjectId ?: -1L) == (s.id ?: -1L) && (s.id != null || s.title == UNSHELVED) } }
        // Topics around their subject's centre: one ring, or two when crowded, biggest first.
        val locals = groups.map { group ->
            val sorted = group.sortedByDescending { byTopic[it.id].orEmpty().size }
            val radii = sorted.map { nodeRadius(byTopic[it.id].orEmpty().size) }
            when (sorted.size) {
                0 -> emptyList()
                1 -> listOf(Local(sorted[0], 0f, 0f, radii[0]))
                else -> {
                    val circumference = radii.sumOf { (2 * it + 22).toDouble() }.toFloat()
                    val ring = max(radii.max() + 30f, (circumference / (2 * PI)).toFloat())
                    var angle = -PI / 2
                    sorted.mapIndexed { i, t ->
                        val step = 2 * PI * (2 * radii[i] + 22) / circumference
                        angle += step / 2
                        val l = Local(t, (ring * cos(angle)).toFloat(), (ring * sin(angle)).toFloat(), radii[i])
                        angle += step / 2
                        l
                    }
                }
            }
        }
        val clusterR = locals.map { ls -> if (ls.isEmpty()) 70f else ls.maxOf { sqrt(it.dx * it.dx + it.dy * it.dy) + it.r } + 26f }
        // Cluster centres on a sunflower spiral, then pushed apart until no two overlap.
        val gap = 36f
        val cx = FloatArray(subjects.size); val cy = FloatArray(subjects.size)
        val spacing = (clusterR.maxOrNull() ?: 70f) * 1.6f
        for (i in subjects.indices) {
            val a = i * 2.399963f
            val d = spacing * sqrt(i.toFloat())
            cx[i] = d * cos(a); cy[i] = d * sin(a)
        }
        repeat(120) {
            var moved = false
            for (i in subjects.indices) for (j in i + 1 until subjects.size) {
                val dx = cx[j] - cx[i]; val dy = cy[j] - cy[i]
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                val need = clusterR[i] + clusterR[j] + gap
                if (dist < need) {
                    val push = (need - dist) / 2
                    val ux = if (dist < 0.02f) 1f else dx / dist; val uy = if (dist < 0.02f) 0f else dy / dist
                    cx[i] -= ux * push; cy[i] -= uy * push; cx[j] += ux * push; cy[j] += uy * push
                    moved = true
                }
            }
            if (!moved) return@repeat
        }
        val clusters = subjects.mapIndexed { i, s -> MapCluster(i, s, cx[i], cy[i], clusterR[i], locals[i].isEmpty()) }
        val nodes = locals.flatMapIndexed { i, ls ->
            ls.map { l ->
                val es = byTopic[l.topic.id].orEmpty()
                val open = es.count { it.type == EntryType.OPEN_QUESTION }
                MapNode(
                    l.topic.id, l.topic.title, names[subjects[i].id ?: -1L]?.takeIf { subjects[i].id != null }, l.topic.summary, i,
                    es.size, open, es.minOfOrNull { it.createdAt } ?: 0L, isGap(es.size, open), cx[i] + l.dx, cy[i] + l.dy, l.r,
                )
            }
        }
        val pad = 40f
        return MapLayout(
            clusters, nodes, links.filter { l -> nodes.any { it.id == l.a } && nodes.any { it.id == l.b } },
            clusters.minOf { it.x - it.r } - pad, clusters.minOf { it.y - it.r } - pad,
            clusters.maxOf { it.x + it.r } + pad, clusters.maxOf { it.y + it.r } + pad,
        )
    }

    /** What lies under world point ([x], [y]): a topic first, then a link (within [slop]), then a subject. */
    fun hit(layout: MapLayout, x: Float, y: Float, slop: Float = 14f): MapHit? {
        layout.nodes.firstOrNull { n -> (n.x - x) * (n.x - x) + (n.y - y) * (n.y - y) <= (n.r + 6) * (n.r + 6) }?.let { return MapHit.Topic(it) }
        val pos = layout.nodes.associateBy { it.id }
        layout.links.minByOrNull { l -> segmentDistance(x, y, pos.getValue(l.a), pos.getValue(l.b)) }?.let { l ->
            if (segmentDistance(x, y, pos.getValue(l.a), pos.getValue(l.b)) <= slop) return MapHit.Link(l)
        }
        return layout.clusters.firstOrNull { c -> (c.x - x) * (c.x - x) + (c.y - y) * (c.y - y) <= c.r * c.r }?.let { MapHit.Subject(it) }
    }

    private fun segmentDistance(px: Float, py: Float, a: MapNode, b: MapNode): Float {
        val vx = b.x - a.x; val vy = b.y - a.y
        val len2 = vx * vx + vy * vy
        val t = if (len2 == 0f) 0f else (((px - a.x) * vx + (py - a.y) * vy) / len2).coerceIn(0f, 1f)
        val dx = a.x + t * vx - px; val dy = a.y + t * vy - py
        return sqrt(dx * dx + dy * dy)
    }
}
