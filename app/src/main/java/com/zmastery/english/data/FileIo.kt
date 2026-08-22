package com.zmastery.english.data

import android.content.Context
import android.net.Uri

/** Thin wrappers around SAF (Storage Access Framework) content URIs. */
object FileIo {

    fun writeText(context: Context, uri: Uri, content: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        true
    } catch (e: Exception) {
        false
    }

    fun readText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }
}
