package com.maheshreddy.mym.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maheshreddy.mym.ui.theme.Mind
import com.maheshreddy.mym.ui.theme.MindDimens
import com.maheshreddy.mym.core.library.EntryType
import com.maheshreddy.mym.core.library.Privacy

// ---- Bottom bar (§3) ----

enum class Tab { HOME, LIBRARY, ASK, INBOX }

/** Home · Library · [Talk] · Ask · Inbox (design-spec §3). */
@Composable
fun MindBottomBar(selected: Tab?, inboxCount: Int, onTab: (Tab) -> Unit, onTalk: () -> Unit) {
    val c = Mind.colors
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(c.surface).drawBehind {
                drawLine(c.line, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }.navigationBarsPadding().padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Five equal slots: Home, Library, (Talk), Ask, Inbox.
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { NavItem("Home", Icons.Outlined.Home, selected == Tab.HOME) { onTab(Tab.HOME) } }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { NavItem("Library", Icons.Outlined.LibraryBooks, selected == Tab.LIBRARY) { onTab(Tab.LIBRARY) } }
            Spacer(Modifier.weight(1f))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NavItem("Ask", Icons.Outlined.Search, selected == Tab.ASK, modifier = Modifier.testTag("nav-ask")) { onTab(Tab.ASK) }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NavItem(
                    "Inbox", Icons.Outlined.Inbox, selected == Tab.INBOX,
                    badge = inboxCount.takeIf { it > 0 }, modifier = Modifier.testTag("nav-inbox"),
                ) { onTab(Tab.INBOX) }
            }
        }
        // The Talk button sits over the middle of five slots, raised 28 dp above the bar.
        Row(Modifier.fillMaxWidth().offset(y = (-28).dp + 8.dp)) {
            Spacer(Modifier.weight(2f))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { TalkButton(onTalk) }
            Spacer(Modifier.weight(2f))
        }
    }
}

@Composable
fun TalkButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mind.colors
    Box(
        modifier.size(MindDimens.talkButton)
            .shadow(10.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
            .clip(CircleShape).background(c.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Talk to M" }
            .testTag("talk"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Mic, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, badge: Int? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Mind.colors
    val tint = if (selected) c.accent else c.muted
    Column(
        modifier.widthIn(min = 56.dp).heightIn(min = MindDimens.touch).clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            if (badge != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp).heightIn(min = 16.dp).widthIn(min = 16.dp)
                        .clip(CircleShape).background(c.accent).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (badge > 99) "99+" else "$badge", style = Mind.type.chip, color = c.onAccent) }
            }
        }
        Text(label, style = if (selected) Mind.type.chip else Mind.type.chip.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = tint)
    }
}

// ---- Top bar for pushed screens ----

@Composable
fun BackBar(onBack: () -> Unit, modifier: Modifier = Modifier, tint: Color = Mind.colors.ink, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tint) }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

// ---- Text helpers ----

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = Mind.type.screenTitle, color = Mind.colors.ink, modifier = modifier)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, style = Mind.type.label, color = Mind.colors.muted, modifier = modifier)

@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Mind.colors.muted) =
    Text(text, style = Mind.type.caption, color = color, modifier = modifier)

// ---- Chips (§2: 11 sp 600, padding 4×8, radius 10) ----

@Composable
fun MindChip(text: String, background: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = Mind.type.chip, color = content, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun TypeChip(type: EntryType, modifier: Modifier = Modifier) {
    val c = Mind.colors
    val (bg, fg) = when (type) {
        EntryType.KNOWLEDGE -> c.accentSoft to c.accent
        EntryType.OPEN_QUESTION -> c.amberTint to c.amberText
        EntryType.PRINCIPLE -> c.violetSoft to c.violetText
        EntryType.LESSON -> c.amberSoft to c.amberText
        EntryType.IDEA, EntryType.REFLECTION -> c.neutralChip to c.neutralChipText
    }
    MindChip(type.label, bg, fg, modifier)
}

@Composable
fun PrivacyChip(privacy: Privacy, modifier: Modifier = Modifier) =
    MindChip(privacy.label, Mind.colors.neutralChip, Mind.colors.neutralChipText, modifier)

@Composable
fun NeedsYouChip(modifier: Modifier = Modifier) = MindChip("Needs you", Mind.colors.amberSoft, Mind.colors.amberText, modifier)

// ---- Buttons (pills, radius = height / 2) ----

enum class PillStyle { ACCENT, INK, OUTLINE, TEXT }

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.ACCENT,
    enabled: Boolean = true,
    height: Dp = MindDimens.pillHeight,
    leading: ImageVector? = null,
) {
    val c = Mind.colors
    val (bg, fg) = when (style) {
        PillStyle.ACCENT -> c.accent to c.onAccent
        PillStyle.INK -> c.ink to c.onInk
        PillStyle.OUTLINE -> Color.Transparent to c.ink
        PillStyle.TEXT -> Color.Transparent to c.accent
    }
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier.heightIn(min = height).clip(shape)
            .then(if (style == PillStyle.OUTLINE) Modifier.border(1.dp, c.fieldLine, shape) else Modifier)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (style == PillStyle.TEXT) 10.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (leading != null) Icon(leading, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = Mind.type.button, color = if (enabled) fg else fg.copy(alpha = 0.6f), maxLines = 1)
    }
}

// ---- Cards (radius 14–18, 1 dp line border, no shadow) ----

@Composable
fun MindCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: Color = Mind.colors.surface,
    border: BorderStroke? = BorderStroke(1.dp, Mind.colors.line),
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MindDimens.cardRadius),
        color = background,
        border = border,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** A dashed "empty shelf" card with a call to action. */
@Composable
fun DashedCard(text: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mind.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(
                    color = c.fieldLine, size = Size(size.width, size.height), cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Mind.type.body, color = c.ink, modifier = Modifier.weight(1f))
        Text(action, style = Mind.type.button, color = c.accent)
    }
}

/** A topic as a book spine (§4 Library): rounded 6/14/14/6 with a darker 6 dp inset on the spine edge. */
@Composable
fun BookSpine(title: String, caption: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mind.colors
    val shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp)
    Column(
        modifier.height(132.dp).clip(shape).background(color)
            .drawBehind { drawRect(c.spineInset, size = Size(6.dp.toPx(), size.height)) }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = Mind.type.spineTitle, color = c.onSpine, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(caption, style = Mind.type.caption, color = c.onSpineMuted, maxLines = 1)
    }
}

/** The butler's round avatar; opens the Butler screen. */
@Composable
fun Avatar(letter: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val c = Mind.colors
    Box(
        modifier.size(size).clip(CircleShape).background(c.accent)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Open your butler" } else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(letter, style = Mind.type.cardTitle, color = c.onAccent) }
}

/** The butler's current name (from the persona profile), provided at the root of the UI. */
val LocalButlerName = androidx.compose.runtime.staticCompositionLocalOf { com.maheshreddy.mym.core.llm.ButlerProfile.DEFAULT_NAME }

val BUTLER_NAME: String
    @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalButlerName.current

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Mind.colors.line))
