package com.maheshreddy.mym.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshreddy.mym.core.library.Privacy
import com.maheshreddy.mym.core.map.KnowledgeMap
import com.maheshreddy.mym.core.map.LinkState
import com.maheshreddy.mym.core.map.MapHit
import com.maheshreddy.mym.core.map.MapLayout
import com.maheshreddy.mym.core.map.MapLink
import com.maheshreddy.mym.core.map.MapPeriod
import com.maheshreddy.mym.ui.components.BUTLER_NAME
import com.maheshreddy.mym.ui.components.Caption
import com.maheshreddy.mym.ui.components.MindCard
import com.maheshreddy.mym.ui.components.Pill
import com.maheshreddy.mym.ui.components.PillStyle
import com.maheshreddy.mym.ui.components.ScreenTitle
import com.maheshreddy.mym.ui.library.LibraryUi
import com.maheshreddy.mym.ui.theme.Mind
import com.maheshreddy.mym.ui.theme.MindDimens

data class MapUiState(
    val layout: MapLayout = MapLayout.EMPTY,
    val privacy: Set<Privacy> = Privacy.entries.toSet(),
    val period: MapPeriod = MapPeriod.ALL,
    val growth: Boolean = false,
    val selected: MapHit? = null,
    val loading: Boolean = true,
)

class MapActions(
    val onTogglePrivacy: (Privacy) -> Unit = {},
    val onPeriod: (MapPeriod) -> Unit = {},
    val onGrowth: () -> Unit = {},
    val onSelect: (MapHit?) -> Unit = {},
    val onOpenTopic: (Long) -> Unit = {},
    val onTalkAbout: (String) -> Unit = {},
    val onConfirm: (MapLink) -> Unit = {},
    val onDismiss: (MapLink) -> Unit = {},
)

@Composable
fun MapScreen(onOpenTopic: (Long) -> Unit, onTalkAbout: (String) -> Unit, vm: MapViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MapContent(
        state,
        MapActions(
            onTogglePrivacy = vm::togglePrivacy, onPeriod = vm::period, onGrowth = vm::toggleGrowth, onSelect = vm::select,
            onOpenTopic = onOpenTopic, onTalkAbout = onTalkAbout, onConfirm = { vm.confirm(it) }, onDismiss = { vm.dismiss(it) },
        ),
    )
}

/**
 * design-spec §4 Map: subjects as coloured clusters, topics as nodes sized by entries, links (dashed = suggested by
 * the butler until confirmed), gaps ringed in amber. Pinch to zoom, drag to pan, tap for a preview card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapContent(state: MapUiState, actions: MapActions) {
    val c = Mind.colors
    val layout = state.layout
    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Column(
            Modifier.padding(start = MindDimens.screenPadding, end = MindDimens.screenPadding, top = 28.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenTitle("Map")
            Caption(
                if (layout.isEmpty) "Nothing mapped yet. Each area below fills in as you talk."
                else listOf(
                    LibraryUi.plural(layout.nodes.size, "topic", "topics"),
                    LibraryUi.plural(layout.links.size, "link", "links"),
                    LibraryUi.plural(layout.gaps, "gap", "gaps"),
                ).joinToString(" · "),
                modifier = Modifier.testTag("mapStats"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Privacy.entries.forEach { p ->
                    val on = p in state.privacy
                    Pill(p.label, { actions.onTogglePrivacy(p) }, Modifier.testTag("mapPrivacy_${p.name}"), style = if (on) PillStyle.INK else PillStyle.OUTLINE, height = 34.dp)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapPeriod.entries.forEach { p ->
                    Pill(p.label, { actions.onPeriod(p) }, Modifier.testTag("mapPeriod_${p.name}"), style = if (p == state.period) PillStyle.INK else PillStyle.OUTLINE, height = 34.dp)
                }
                Pill("Growth", actions.onGrowth, Modifier.testTag("mapGrowth"), style = if (state.growth) PillStyle.ACCENT else PillStyle.OUTLINE, height = 34.dp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MapCanvas(state, actions.onSelect)
            Caption(
                "Dashed: suggested by $BUTLER_NAME · amber ring: a gap",
                modifier = Modifier.align(Alignment.TopStart).padding(horizontal = MindDimens.screenPadding),
            )
            state.selected?.let { hit ->
                Box(Modifier.align(Alignment.BottomCenter).padding(MindDimens.screenPadding)) { Preview(hit, layout, actions) }
            }
        }
    }
}

@Composable
private fun MapCanvas(state: MapUiState, onSelect: (MapHit?) -> Unit) {
    val c = Mind.colors
    val layout = state.layout
    val measurer = rememberTextMeasurer()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Fit the whole map on screen when it first appears or its extent changes.
    LaunchedEffect(layout.minX, layout.minY, layout.maxX, layout.maxY, size) {
        if (size.width == 0 || layout.clusters.isEmpty()) return@LaunchedEffect
        val w = layout.maxX - layout.minX; val h = layout.maxY - layout.minY
        val s = minOf(size.width / w, size.height / h).coerceIn(0.15f, 2.5f)
        scale = s
        offset = Offset(size.width / 2f - (layout.minX + w / 2) * s, size.height / 2f - (layout.minY + h / 2) * s)
    }
    // Growth: topics appear in the order they were added.
    val growth = remember { Animatable(1f) }
    LaunchedEffect(state.growth, layout.nodes.size) {
        if (state.growth) {
            growth.snapTo(0f)
            growth.animateTo(1f, tween(durationMillis = 4_000, easing = LinearEasing))
        } else growth.snapTo(1f)
    }
    val first = layout.nodes.minOfOrNull { it.addedAt } ?: 0L
    val last = layout.nodes.maxOfOrNull { it.addedAt } ?: 0L
    val cutoff = first + ((last - first) * growth.value).toLong()
    val visible = layout.nodes.filter { !state.growth || it.addedAt <= cutoff }.associateBy { it.id }
    val selectedTopic = (state.selected as? MapHit.Topic)?.node?.id
    val selectedLink = (state.selected as? MapHit.Link)?.link
    val labelStyle = Mind.type.label.copy(textAlign = TextAlign.Center)
    val captionStyle = Mind.type.caption.copy(textAlign = TextAlign.Center)
    fun Mind_label(color: Color) = labelStyle.copy(color = color)
    fun Mind_caption(color: Color) = captionStyle.copy(color = color)
    val description = "Knowledge map: ${layout.clusters.size} subjects, ${layout.nodes.size} topics, ${layout.gaps} gaps"

    Canvas(
        Modifier.fillMaxSize().onSizeChanged { size = it }.testTag("mapCanvas").semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val ns = (scale * zoom).coerceIn(0.1f, 5f)
                    offset = centroid - (centroid - offset) * (ns / scale) + pan
                    scale = ns
                }
            }
            .pointerInput(layout) {
                detectTapGestures { p ->
                    val w = (p - offset) / scale
                    onSelect(KnowledgeMap.hit(layout, w.x, w.y, slop = 14f / scale))
                }
            },
    ) {
        fun sx(x: Float) = x * scale + offset.x
        fun sy(y: Float) = y * scale + offset.y
        val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
        // Clusters.
        layout.clusters.forEach { cl ->
            val col = c.spines[cl.index % c.spines.size]
            val center = Offset(sx(cl.x), sy(cl.y))
            drawCircle(col.copy(alpha = if (c.isDark) 0.22f else 0.10f), cl.r * scale, center)
            drawCircle(
                if (cl.empty) c.amberBorder else col.copy(alpha = 0.45f), cl.r * scale, center,
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = if (cl.empty) dash else null),
            )
            label(measurer, cl.subject.title, Offset(center.x, center.y - cl.r * scale - 20.dp.toPx()), 220.dp.toPx(), Mind_label(c.ink))
            if (cl.empty) {
                label(measurer, if (cl.subject.id == null) "Start here" else "Empty: tap to fill", Offset(center.x, center.y - 8.dp.toPx()), (cl.r * scale * 1.6f).coerceAtLeast(60f), Mind_caption(c.amberText))
            }
        }
        // Links.
        layout.links.forEach { l ->
            val a = visible[l.a] ?: return@forEach
            val b = visible[l.b] ?: return@forEach
            val on = selectedLink?.a == l.a && selectedLink.b == l.b
            drawLine(
                if (on) c.accent else if (l.state == LinkState.CONFIRMED) c.ink.copy(alpha = 0.5f) else c.muted.copy(alpha = 0.8f),
                Offset(sx(a.x), sy(a.y)), Offset(sx(b.x), sy(b.y)),
                strokeWidth = (if (on) 3f else 1.5f + l.strength).dp.toPx(),
                pathEffect = if (l.state == LinkState.SUGGESTED) dash else null,
            )
        }
        // Topics.
        layout.nodes.forEach { n ->
            if (n.id !in visible) return@forEach
            val appear = if (state.growth && last > first) ((cutoff - n.addedAt).toFloat() / ((last - first) * 0.15f + 1)).coerceIn(0.2f, 1f) else 1f
            val center = Offset(sx(n.x), sy(n.y))
            val r = n.r * scale * appear
            drawCircle(c.spines[n.cluster % c.spines.size], r, center)
            if (n.gap) drawCircle(c.amberBorder, r + 5.dp.toPx(), center, style = Stroke(2.dp.toPx(), pathEffect = dash))
            if (n.id == selectedTopic) drawCircle(c.ink, r + 9.dp.toPx(), center, style = Stroke(2.5.dp.toPx()))
            if (n.entries > 0 && r > 10.dp.toPx()) label(measurer, "${n.entries}", Offset(center.x, center.y - 9.dp.toPx()), r * 2, Mind_label(c.onSpine))
            if (scale > 0.35f) label(measurer, n.title, Offset(center.x, center.y + r + 4.dp.toPx()), (n.r * 2 + 70) * scale, Mind_caption(c.ink))
        }
    }
}


private fun DrawScope.label(measurer: TextMeasurer, text: String, topCenter: Offset, maxWidth: Float, style: TextStyle) {
    val w = maxWidth.toInt().coerceAtLeast(40)
    val m = measurer.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = 2, constraints = Constraints(maxWidth = w))
    drawText(m, topLeft = Offset(topCenter.x - m.size.width / 2f, topCenter.y))
}

/** The card for whatever was tapped: a topic, a subject (gap or not), or a link to confirm. */
@Composable
private fun Preview(hit: MapHit, layout: MapLayout, actions: MapActions) {
    val c = Mind.colors
    MindCard(Modifier.testTag("mapPreview")) {
        when (hit) {
            is MapHit.Topic -> {
                val n = hit.node
                n.subject?.let { Caption(it) }
                Text(n.title, style = Mind.type.cardTitle, color = c.ink)
                if (n.summary.isNotBlank()) Text(n.summary, style = Mind.type.body, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Caption(
                    LibraryUi.plural(n.entries, "entry", "entries") +
                        if (n.openQuestions > 0) " · " + LibraryUi.plural(n.openQuestions, "open question", "open questions") else "",
                )
                if (n.gap) Caption(if (n.entries <= 1) "Thin so far: a gap worth filling." else "Open questions still waiting.", color = c.amberText)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Open topic", { actions.onOpenTopic(n.id) }, Modifier.testTag("mapOpenTopic"), style = PillStyle.INK, height = MindDimens.touch)
                    Pill(
                        if (n.gap) "Interview me on this" else "Continue by talking", { actions.onTalkAbout(n.title) },
                        Modifier.testTag("mapTalk"), style = PillStyle.OUTLINE, height = MindDimens.touch,
                    )
                }
            }
            is MapHit.Subject -> {
                val cl = hit.cluster
                val topics = layout.nodes.count { it.cluster == cl.index }
                Text(cl.subject.title, style = Mind.type.cardTitle, color = c.ink)
                Caption(
                    when {
                        cl.subject.id == null && cl.empty -> "Start here: tell $BUTLER_NAME what you know about ${cl.subject.title}."
                        cl.empty -> "An empty shelf: a gap in your map."
                        else -> LibraryUi.plural(topics, "topic", "topics")
                    },
                )
                Row { Pill(if (cl.empty) "Interview me on this" else "Continue by talking", { actions.onTalkAbout(cl.subject.title) }, Modifier.testTag("mapTalk"), style = PillStyle.INK, height = MindDimens.touch) }
            }
            is MapHit.Link -> {
                val l = hit.link
                val a = layout.nodes.firstOrNull { it.id == l.a }?.title.orEmpty()
                val b = layout.nodes.firstOrNull { it.id == l.b }?.title.orEmpty()
                Caption(if (l.state == LinkState.SUGGESTED) "$BUTLER_NAME suggests these are related" else "Linked")
                Text("$a  ↔  $b", style = Mind.type.cardTitle, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (l.state == LinkState.SUGGESTED) {
                        Pill("Confirm link", { actions.onConfirm(l) }, Modifier.testTag("mapConfirm"), style = PillStyle.INK, height = MindDimens.touch)
                        Pill("Not related", { actions.onDismiss(l) }, Modifier.testTag("mapDismiss"), style = PillStyle.OUTLINE, height = MindDimens.touch)
                    } else {
                        Pill("Remove link", { actions.onDismiss(l) }, Modifier.testTag("mapDismiss"), style = PillStyle.OUTLINE, height = MindDimens.touch)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Pill("Close", { actions.onSelect(null) }, style = PillStyle.TEXT, height = 36.dp)
        }
    }
}
