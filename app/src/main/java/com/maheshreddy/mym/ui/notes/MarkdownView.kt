package com.maheshreddy.mym.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Minimal Markdown renderer: headings, bullets, numbered lists and **bold**. Enough for generated notes. */
@Composable
fun MarkdownView(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.startsWith("### ") -> Text(line.removePrefix("### "), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val indent = (line.length - line.trimStart().length) / 2
                    Text(inline("• " + line.trimStart().drop(2)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = (8 + 16 * indent).dp))
                }
                line.isBlank() -> Text("", style = MaterialTheme.typography.bodySmall)
                line.length > 2 && line.startsWith("_") && line.endsWith("_") ->
                    Text(line.trim('_'), style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text(inline(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun inline(text: String) = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}
