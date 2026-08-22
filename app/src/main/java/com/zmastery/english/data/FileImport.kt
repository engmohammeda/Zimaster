package com.zmastery.english.data

import android.content.Context
import android.net.Uri
import java.util.zip.ZipInputStream

/**
 * Reads uploaded files (JSON and ZIP) from content URIs and returns the raw
 * JSON strings inside them. Supports:
 *   • one or more .json files selected at once
 *   • one or more .zip archives, each containing many .json entries
 */
object FileImport {

    data class Result(val jsons: List<String>, val fileCount: Int, val jsonCount: Int, val error: String? = null)

    fun readUris(context: Context, uris: List<Uri>): Result {
        if (uris.isEmpty()) return Result(emptyList(), 0, 0, "لم يتم اختيار أي ملف")
        val out = mutableListOf<String>()
        var fileCount = 0
        val problems = mutableListOf<String>()

        uris.forEach { uri ->
            val name = displayName(context, uri)
            try {
                if (name.endsWith(".zip", ignoreCase = true) || isZip(context, uri)) {
                    val entries = readZip(context, uri)
                    if (entries.isEmpty()) problems += "«$name»: لا يحتوي على ملفات JSON"
                    out += entries
                    fileCount++
                } else {
                    val content = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    if (content.isNullOrBlank()) problems += "«$name»: ملف فارغ"
                    else { out += content; fileCount++ }
                }
            } catch (e: Exception) {
                problems += "«$name»: ${e.message?.take(50) ?: "تعذّر القراءة"}"
            }
        }

        val err = if (out.isEmpty()) {
            if (problems.isEmpty()) "لم يُعثر على محتوى JSON" else problems.joinToString("\n")
        } else null
        return Result(out, fileCount, out.size, err)
    }

    private fun readZip(context: Context, uri: Uri): List<String> {
        val jsons = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                        val bytes = zip.readBytes()
                        val text = bytes.decodeToString().trim()
                        if (text.isNotEmpty()) jsons += text
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return jsons
    }

    private fun isZip(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val sig = ByteArray(2)
                val read = input.read(sig)
                read == 2 && sig[0] == 'P'.code.toByte() && sig[1] == 'K'.code.toByte()
            } ?: false
        } catch (e: Exception) { false }
    }

    private fun displayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment ?: "ملف"
            } ?: (uri.lastPathSegment ?: "ملف")
        } catch (e: Exception) { uri.lastPathSegment ?: "ملف" }
    }
}
