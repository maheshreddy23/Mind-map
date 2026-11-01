package com.freshernotes.app.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshernotes.app.models.ModelKind
import com.freshernotes.core.OfficialChecksums

/** Lets the user pick model files with the Storage Access Framework; they are copied into private storage. */
@Composable
fun ModelSection(vm: ModelsViewModel = hiltViewModel()) {
    val installed by vm.installed.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val integrity by vm.integrity.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<ModelKind?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val kind = pending
        if (uri != null && kind != null) vm.import(kind, uri)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val kind = pending
        if (uri != null && kind != null) vm.import(kind, uri)
    }

    Column {
        Text("Models", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        ModelKind.entries.forEach { kind ->
            val file = installed[kind]
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(kind.label + if (kind.required) " *" else "", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        file?.let { "${it.name} (${sizeLabel(it)})" } ?: "Not installed: ${kind.hint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (file == null && kind.required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    integrity[kind]?.takeIf { file != null && it.fileName == file.name }?.let { i ->
                        val (label, bad) = when (i.verdict) {
                            OfficialChecksums.Verdict.MATCHES_OFFICIAL -> "✓ SHA-256 matches the official ${i.officialName}" to false
                            OfficialChecksums.Verdict.DIFFERS_FROM_OFFICIAL -> "✗ SHA-256 does NOT match the official ${i.officialName}: damaged, download again" to true
                            OfficialChecksums.Verdict.UNKNOWN_FILE -> "SHA-256 recorded (no official checksum known for this file)" to false
                        }
                        Text(
                            "$label\n${i.sha256}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (file != null && !kind.isDirectory) TextButton(enabled = progress == null, onClick = { vm.verify(kind) }) { Text("Verify") }
                if (file != null) TextButton(onClick = { vm.remove(kind) }) { Text("Remove") }
                OutlinedButton(enabled = progress == null, onClick = {
                    pending = kind
                    if (kind.isDirectory) pickFolder.launch(null) else pickFile.launch(arrayOf("*/*"))
                }) { Text(if (file == null) "Pick" else "Replace") }
            }
            progress?.takeIf { it.kind == kind }?.let { p ->
                Text(if (p.verifying) "Verifying SHA-256…" else "Copying…", style = MaterialTheme.typography.bodySmall)
                if (p.totalBytes > 0) LinearProgressIndicator(progress = { p.copiedBytes.toFloat() / p.totalBytes }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun sizeLabel(f: java.io.File): String {
    val bytes = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
    return when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
        else -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    }
}
