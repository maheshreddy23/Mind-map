package com.maheshreddy.mym.notes

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Writes topic pages to Documents/FresherNotes/<slug>.md via MediaStore (no storage permission needed on API 29+). */
@Singleton
class MarkdownExporter @Inject constructor(@ApplicationContext private val context: Context) {
    private val relativePath = Environment.DIRECTORY_DOCUMENTS + "/MYM/"

    suspend fun export(slug: String, markdown: String): Uri = withContext(Dispatchers.IO) {
        val name = "$slug.md"
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(relativePath, name),
            null,
        )?.use { c -> if (c.moveToFirst()) Uri.withAppendedPath(collection, c.getLong(0).toString()) else null }

        val uri = existing ?: requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                },
            ),
        ) { "Could not create $relativePath$name" }
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "Could not open $uri" }.use { it.write(markdown.toByteArray()) }
        uri
    }
}
