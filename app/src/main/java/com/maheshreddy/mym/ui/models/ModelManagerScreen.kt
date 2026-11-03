package com.maheshreddy.mym.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshreddy.mym.core.models.DeviceFit
import com.maheshreddy.mym.core.models.ModelCategory
import com.maheshreddy.mym.models.DownloadState
import com.maheshreddy.mym.models.ModelKind
import com.maheshreddy.mym.ui.components.BackBar
import com.maheshreddy.mym.ui.components.Caption
import com.maheshreddy.mym.ui.components.MindCard
import com.maheshreddy.mym.ui.components.MindChip
import com.maheshreddy.mym.ui.components.Pill
import com.maheshreddy.mym.ui.components.PillStyle
import com.maheshreddy.mym.ui.components.ScreenTitle
import com.maheshreddy.mym.ui.models.ModelsViewModel.Companion.sizeLabel
import com.maheshreddy.mym.ui.theme.Mind
import com.maheshreddy.mym.ui.theme.MindDimens

@Composable
fun ModelManagerScreen(onBack: () -> Unit, vm: ModelsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val confirm by vm.confirmMobile.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    val kind = ModelKind.of(state.category)
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) vm.import(kind, uri) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? -> if (uri != null) vm.import(kind, uri) }
    confirm?.let { m ->
        AlertDialog(
            onDismissRequest = { vm.confirmMobile(false) },
            title = { Text("You're not on Wi-Fi") },
            text = { Text("${m.name} is ${sizeLabel(m.sizeBytes)}. Download it over mobile data now, or wait for Wi-Fi?") },
            confirmButton = { TextButton(onClick = { vm.confirmMobile(true) }) { Text("Use mobile data") } },
            dismissButton = { TextButton(onClick = { vm.confirmMobile(false) }) { Text("Wait for Wi-Fi") } },
        )
    }
    ModelManagerContent(
        state, snackbar, onBack,
        onCategory = vm::setCategory, onSort = vm::setSort,
        onDownload = vm::download, onPause = vm::pause, onCancel = vm::cancel, onActivate = vm::activate, onDelete = vm::delete,
        onImport = { if (kind.isDirectory) pickFolder.launch(null) else pickFile.launch(arrayOf("*/*")) },
    )
}

/** Settings → Models: the catalogue for one category, with fit for this phone, downloads and the installed models. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelManagerContent(
    state: ModelsUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onCategory: (ModelCategory) -> Unit,
    onSort: (DeviceFit.Sort) -> Unit,
    onDownload: (String) -> Unit,
    onPause: (String) -> Unit,
    onCancel: (String) -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onImport: () -> Unit,
) {
    val c = Mind.colors
    Scaffold(topBar = { BackBar(onBack) }, snackbarHost = { SnackbarHost(snackbar) }, containerColor = c.ground) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("models"),
            contentPadding = PaddingValues(start = MindDimens.screenPadding, end = MindDimens.screenPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScreenTitle("Models")
                    Caption("${state.device} · ${state.tier}")
                    Caption("Stored privately in ${state.storagePath} · ${sizeLabel(state.usedBytes)} used · ${sizeLabel(state.freeBytes)} free")
                }
            }
            if (!state.canDownload) item {
                MindCard(background = c.amberSoft, border = null) {
                    Text("This is the Sealed build: it has no network access. Download a model's file on a computer from the Hugging Face link shown on its card, copy it to the phone, then import it here.", style = Mind.type.body, color = c.ink)
                }
            }
            item { Chips(ModelCategory.entries.map { it to it.label }, state.category, onCategory) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Caption("Sort ")
                    Chips(DeviceFit.Sort.entries.map { it to it.name.lowercase().replaceFirstChar { ch -> ch.uppercase() } }, state.sort, onSort)
                }
            }
            items(state.rows, key = { it.model.id }) { row -> ModelCard(row, state.canDownload, state.wifiOnly, onDownload, onPause, onCancel, onActivate, onDelete) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Caption("Or import a file you downloaded yourself. It's copied into private storage and checked against the catalogue's official SHA-256 if it is one of these models.")
                    Pill("Import a file", onImport, style = PillStyle.OUTLINE)
                    state.importing?.let { p ->
                        Caption(if (p.verifying) "Verifying SHA-256…" else "Copying…")
                        LinearProgressIndicator(progress = { if (p.totalBytes > 0) p.copiedBytes.toFloat() / p.totalBytes else 0f }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.line)
                    }
                }
            }
            items(state.imported, key = { it.id }) { m ->
                MindCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.activeId == m.id) MindChip("Active", c.ink, c.onInk)
                        MindChip(if (m.verified) "Checksum OK" else "Not verified", c.neutralChip, c.neutralChipText)
                    }
                    Text(m.name, style = Mind.type.body, color = c.ink)
                    Caption(sizeLabel(m.sizeBytes))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.activeId != m.id) Pill("Activate", { onActivate(m.id) }, height = MindDimens.touch)
                        Pill("Delete", { onDelete(m.id) }, style = PillStyle.TEXT, height = MindDimens.touch)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = Mind.colors
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) c.ink else c.surface)
                    .then(if (on) Modifier else Modifier.border(1.dp, c.fieldLine, RoundedCornerShape(18.dp)))
                    .clickable(role = Role.RadioButton) { onSelect(value) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = Mind.type.label.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal), color = if (on) c.onInk else c.ink) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(
    row: ModelRow,
    canDownload: Boolean,
    wifiOnly: Boolean,
    onDownload: (String) -> Unit,
    onPause: (String) -> Unit,
    onCancel: (String) -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val c = Mind.colors
    val m = row.model
    val d = row.download
    MindCard(
        border = if (row.recommended) BorderStroke(1.5.dp, c.accent) else BorderStroke(1.dp, c.line),
        modifier = Modifier.testTag("model-${m.id}"),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (row.recommended) MindChip("Recommended for your phone", c.accentSoft, c.accent)
            if (row.active) MindChip("Active", c.ink, c.onInk) else if (row.installed) MindChip("Installed", c.neutralChip, c.neutralChipText)
            MindChip(m.licence.name, if (m.licence.openSource) c.neutralChip else c.amberTint, if (m.licence.openSource) c.neutralChipText else c.amberText)
            if (!row.fit.fits) MindChip("Too big for this phone", c.amberSoft, c.amberText)
        }
        Text(m.name, style = Mind.type.cardTitle, color = c.ink)
        Text(
            "${sizeLabel(m.sizeBytes)} · needs ~${"%.1f".format(m.ramNeededMb / 1024.0)} GB RAM · ${row.fit.speed}",
            style = Mind.type.body, color = c.ink,
        )
        Caption("Quality ${"●".repeat(m.quality)}${"○".repeat(5 - m.quality)} · ${m.languages}" + if (m.note.isNotBlank()) " · ${m.note}" else "")
        if (!canDownload) Caption("huggingface.co/${m.repo}/resolve/${m.revision.take(10)}…/${m.files.first().path}")
        when (d?.status) {
            DownloadState.Status.DOWNLOADING, DownloadState.Status.VERIFYING, DownloadState.Status.WAITING -> {
                LinearProgressIndicator(progress = { d.fraction }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.line)
                Caption(
                    when (d.status) {
                        DownloadState.Status.WAITING -> if (wifiOnly) "Waiting for Wi-Fi…" else "Starting…"
                        DownloadState.Status.VERIFYING -> "Checking SHA-256 against the official checksum…"
                        else -> "${sizeLabel(d.bytes)} of ${sizeLabel(d.total)}"
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Pause", { onPause(m.id) }, style = PillStyle.OUTLINE, height = MindDimens.touch)
                    Pill("Cancel", { onCancel(m.id) }, style = PillStyle.TEXT, height = MindDimens.touch)
                }
            }
            DownloadState.Status.PAUSED, DownloadState.Status.FAILED -> {
                LinearProgressIndicator(progress = { d.fraction }, modifier = Modifier.fillMaxWidth(), color = c.amberText, trackColor = c.line)
                Caption(d.error ?: "Paused at ${sizeLabel(d.bytes)} of ${sizeLabel(d.total)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Resume", { onDownload(m.id) }, height = MindDimens.touch)
                    Pill("Cancel", { onCancel(m.id) }, style = PillStyle.TEXT, height = MindDimens.touch)
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.installed) {
                    if (!row.active) Pill("Activate", { onActivate(m.id) }, height = MindDimens.touch)
                    Pill("Delete", { onDelete(m.id) }, style = PillStyle.TEXT, height = MindDimens.touch)
                } else if (canDownload) {
                    Pill("Download", { onDownload(m.id) }, style = if (row.recommended) PillStyle.ACCENT else PillStyle.OUTLINE, height = MindDimens.touch)
                }
            }
        }
    }
}
