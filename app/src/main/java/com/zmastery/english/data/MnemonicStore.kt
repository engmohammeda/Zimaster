package com.zmastery.english.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * On-disk store for mnemonic tiles + the slicer that cuts an uploaded composite
 * sheet into one square image per word.
 *
 * Tiles live in `filesDir/mnemonics/word_<id>.webp`. Storing files (instead of
 * base64 inside the JSON snapshot) keeps the state blob small and the app fast
 * no matter how many thousands of words exist.
 */
object MnemonicStore {

    private const val DIR = "mnemonics"

    fun dir(ctx: Context): File = File(ctx.filesDir, DIR).apply { mkdirs() }

    fun fileFor(ctx: Context, wordId: Int): File = File(dir(ctx), "word_$wordId.webp")

    /** Absolute path of a word's tile, or null when it has none. */
    fun pathFor(ctx: Context, wordId: Int): String? {
        val f = fileFor(ctx, wordId)
        return if (f.exists() && f.length() > 64) f.absolutePath else null
    }

    fun has(ctx: Context, wordId: Int): Boolean = pathFor(ctx, wordId) != null

    fun delete(ctx: Context, wordId: Int): Boolean =
        runCatching { fileFor(ctx, wordId).delete() }.getOrDefault(false)

    /** Total bytes used by all tiles — shown in the UI. */
    fun totalBytes(ctx: Context): Long =
        runCatching { dir(ctx).listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)

    fun count(ctx: Context): Int =
        runCatching { dir(ctx).listFiles()?.count { it.length() > 64 } ?: 0 }.getOrDefault(0)

    /** Remove every tile (used by "reset" / "clear images"). */
    fun clearAll(ctx: Context): Int {
        val files = dir(ctx).listFiles() ?: return 0
        var n = 0
        files.forEach { if (runCatching { it.delete() }.getOrDefault(false)) n++ }
        return n
    }

    /** Result of slicing one uploaded sheet. */
    data class SliceResult(
        val success: Boolean,
        val saved: Int,
        val message: String,
        val sourceW: Int = 0,
        val sourceH: Int = 0,
    )

    /**
     * Decode the picked image, then crop [spec] cells in reading order and save
     * one tile per word id in [wordIds].
     *
     * The crop uses FRACTIONAL rectangles, so the uploaded sheet may be any
     * resolution (or lightly re-scaled by the generator) and still slices
     * correctly. Tiles are downsized to [MnemonicSpec.TILE_PX] and stored as
     * WEBP for a small footprint.
     */
    suspend fun sliceAndSave(
        ctx: Context,
        uri: Uri,
        wordIds: List<Int>,
        spec: MnemonicSpec,
    ): SliceResult = withContext(Dispatchers.IO) {
        if (wordIds.isEmpty()) {
            return@withContext SliceResult(false, 0, "لا توجد كلمات في هذه الدفعة")
        }

        val source = runCatching { decodeOriented(ctx, uri) }.getOrNull()
            ?: return@withContext SliceResult(false, 0, "تعذّر قراءة الصورة — تأكد أنها PNG أو JPG صالحة")

        val sw = source.width
        val sh = source.height
        if (sw < 64 || sh < 64) {
            source.recycle()
            return@withContext SliceResult(false, 0, "الصورة صغيرة جداً (${sw}×${sh})")
        }

        var saved = 0
        try {
            wordIds.forEachIndexed { i, wordId ->
                if (i >= spec.cells) return@forEachIndexed
                val fr = spec.fractionalRect(i)
                val rect = Rect(
                    (fr[0] * sw).roundToInt().coerceIn(0, sw - 1),
                    (fr[1] * sh).roundToInt().coerceIn(0, sh - 1),
                    (fr[2] * sw).roundToInt().coerceIn(1, sw),
                    (fr[3] * sh).roundToInt().coerceIn(1, sh),
                )
                val w = (rect.right - rect.left).coerceAtLeast(1)
                val h = (rect.bottom - rect.top).coerceAtLeast(1)
                if (w < 8 || h < 8) return@forEachIndexed

                val piece = Bitmap.createBitmap(source, rect.left, rect.top, w, h)
                // Force a perfect square tile, then cap the long edge.
                val squared = centerSquare(piece)
                if (squared != piece) piece.recycle()
                val target = MnemonicSpec.TILE_PX
                val finalBmp = if (squared.width > target) {
                    Bitmap.createScaledBitmap(squared, target, target, true).also {
                        if (it != squared) squared.recycle()
                    }
                } else squared

                val out = fileFor(ctx, wordId)
                val ok = runCatching {
                    out.outputStream().use { os ->
                        @Suppress("DEPRECATION")
                        finalBmp.compress(Bitmap.CompressFormat.WEBP, 88, os)
                    }
                    true
                }.getOrDefault(false)
                finalBmp.recycle()
                if (ok && out.length() > 64) saved++
            }
        } finally {
            source.recycle()
        }

        if (saved == 0) {
            SliceResult(false, 0, "فشل القص — تحقّق من أن الصورة تطابق مخطط الشبكة", sw, sh)
        } else {
            SliceResult(true, saved, "تم ربط $saved صورة بالكلمات بنجاح", sw, sh)
        }
    }

    /**
     * Slice a sheet into preview tiles (in memory) without saving — used by the
     * confirmation step so the learner can verify alignment before committing.
     */
    suspend fun previewSlices(
        ctx: Context,
        uri: Uri,
        count: Int,
        spec: MnemonicSpec,
        previewPx: Int = 220,
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val source = runCatching { decodeOriented(ctx, uri, maxEdge = 1600) }.getOrNull()
            ?: return@withContext emptyList()
        val sw = source.width
        val sh = source.height
        val out = ArrayList<Bitmap>(count)
        try {
            for (i in 0 until count.coerceAtMost(spec.cells)) {
                val fr = spec.fractionalRect(i)
                val left = (fr[0] * sw).roundToInt().coerceIn(0, sw - 1)
                val top = (fr[1] * sh).roundToInt().coerceIn(0, sh - 1)
                val right = (fr[2] * sw).roundToInt().coerceIn(1, sw)
                val bottom = (fr[3] * sh).roundToInt().coerceIn(1, sh)
                val w = (right - left).coerceAtLeast(1)
                val h = (bottom - top).coerceAtLeast(1)
                if (w < 8 || h < 8) continue
                val piece = Bitmap.createBitmap(source, left, top, w, h)
                val sq = centerSquare(piece)
                if (sq != piece) piece.recycle()
                val scaled = if (sq.width > previewPx) {
                    Bitmap.createScaledBitmap(sq, previewPx, previewPx, true).also {
                        if (it != sq) sq.recycle()
                    }
                } else sq
                out.add(scaled)
            }
        } finally {
            source.recycle()
        }
        out
    }

    /** Crop the largest centred square out of [src]. */
    private fun centerSquare(src: Bitmap): Bitmap {
        if (src.width == src.height) return src
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        return Bitmap.createBitmap(src, x, y, side, side)
    }

    /**
     * Decode a content URI honouring EXIF rotation, downsampling so the longest
     * edge stays within [maxEdge] (protects against OOM on huge uploads while
     * remaining far above what the tiles need).
     */
    private fun decodeOriented(ctx: Context, uri: Uri, maxEdge: Int = 4096): Bitmap? {
        val cr = ctx.contentResolver

        // Pass 1 — bounds only, to compute the sample size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / sample > maxEdge) sample *= 2

        // Pass 2 — real decode.
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        // EXIF orientation (phone screenshots / camera shots).
        val rotation = runCatching {
            cr.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (rotation == 0f) return bmp
        val m = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated != bmp) bmp.recycle()
        return rotated
    }
}
