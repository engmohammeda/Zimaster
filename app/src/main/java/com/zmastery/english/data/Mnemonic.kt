package com.zmastery.english.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

// ==========================================================================
//  Mnemonic (الرابط الذهني) generation engine.
//
//  Workflow — designed to stay simple no matter how many words you have:
//
//   1. The app picks a BATCH of dictionary words that still have no mnemonic
//      image (only words actually added to the dictionary are eligible).
//   2. It computes an exact GRID layout and builds a highly-specific image
//      prompt: canvas size, cell size, gutter, fill order, art style, a
//      recurring character, and — per cell — the word fused with its own
//      example sentence.
//   3. The learner pastes that prompt into any image model, then uploads the
//      single composite image back into the app.
//   4. The slicer crops the composite by exact fractional cell rectangles and
//      attaches one square tile to each word, ready for the review cards.
//
//  Because cropping is done on FRACTIONS of the uploaded bitmap, the image can
//  come back at any resolution and it still slices perfectly.
// ==========================================================================

/** Visual art style of the generated mnemonic scenes. */
enum class MnemonicArtStyle(
    val label: String,
    val short: String,
    /** English style directive injected into the prompt. */
    val directive: String,
) {
    CARTOON_3D(
        "كرتون ثلاثي الأبعاد", "3D",
        "playful 3D cartoon render, Pixar-like lighting, soft global illumination, " +
            "chunky friendly shapes, saturated candy colours, glossy materials, shallow depth of field",
    ),
    FLAT_VECTOR(
        "فيكتور مسطح", "Flat",
        "clean flat vector illustration, bold 3px outlines, limited 5-colour palette, " +
            "geometric shapes, no gradients, crisp edges, modern infographic look",
    ),
    STORYBOOK(
        "كتاب قصص", "Story",
        "warm children's storybook watercolour illustration, soft pencil texture, " +
            "gentle washes, cosy earthy palette, hand-painted paper grain",
    ),
    COMIC(
        "كوميك", "Comic",
        "bold western comic-book panel art, heavy black ink outlines, halftone dot shading, " +
            "dynamic exaggerated poses, punchy primary colours",
    ),
    CLAY(
        "طين مجسّم", "Clay",
        "handmade claymation stop-motion look, visible fingerprint texture on matte plasticine, " +
            "soft studio key light, tactile miniature set",
    ),
    PHOTOREAL(
        "واقعي", "Photo",
        "photorealistic editorial photograph, 50mm lens, natural window light, " +
            "shallow depth of field, true-to-life materials and skin",
    );

    companion object {
        fun from(name: String): MnemonicArtStyle =
            runCatching { valueOf(name) }.getOrDefault(CARTOON_3D)
    }
}

/**
 * A recurring character that appears across every mnemonic image.
 * Consistency is a real memory aid: the brain files the whole set under one
 * "cast", which makes each individual scene easier to retrieve.
 */
enum class MnemonicPersona(
    val label: String,
    /** English character sheet injected into the prompt. */
    val sheet: String,
) {
    NONE("بدون شخصية", ""),
    ZAID(
        "زيد — فتى عربي",
        "a cheerful 10-year-old Arab boy named Zaid: short black wavy hair, warm olive skin, " +
            "big expressive dark eyes, mustard-yellow hoodie, dark teal trousers, white sneakers",
    ),
    NOOR(
        "نور — فتاة عربية",
        "a bright 10-year-old Arab girl named Noor: dark hair in two braids, warm olive skin, " +
            "round cheeks, coral-pink jacket, denim skirt, teal backpack",
    ),
    ZBOT(
        "زي‑بوت — روبوت",
        "a friendly palm-sized robot named Z-Bot: rounded matte-white chassis with terracotta accent panels, " +
            "single large cyan visor eye, stubby articulated arms, small hover thrusters",
    ),
    CAT(
        "قط برتقالي",
        "a chubby ginger tabby cat with a white chest, oversized amber eyes and a tiny teal collar bell",
    ),
    GRANDPA(
        "الجد الحكيم",
        "a kindly bearded grandfather with round glasses, a knitted olive cardigan and a carved walking stick",
    );

    companion object {
        fun from(name: String): MnemonicPersona =
            runCatching { valueOf(name) }.getOrDefault(NONE)
    }
}

/** Which image generator the prompt is being tailored for. */
enum class MnemonicModel(
    val label: String,
    val hint: String,
    /** Extra syntax appended at the very end of the prompt. */
    val suffix: String,
) {
    GEMINI(
        "Gemini / Nano Banana", "يفهم التعليمات الطويلة والشبكات بدقة",
        "",
    ),
    CHATGPT(
        "ChatGPT / GPT-Image", "ممتاز في الالتزام بالشبكة والتفاصيل",
        "Render the full grid in one image. Do not crop, do not add padding beyond the specified gutter.",
    ),
    MIDJOURNEY(
        "Midjourney", "أعلى جمالية — أضف معاملات الأبعاد",
        "--ar {AR} --style raw --q 2 --no text, letters, numbers, words, watermark, signature, frame",
    ),
    GROK(
        "Grok / Aurora", "سريع وواقعي",
        "Output a single composite image following the grid exactly.",
    ),
    GENERIC(
        "أي مولّد آخر", "مطالبة عامة متوافقة",
        "",
    );

    companion object {
        fun from(name: String): MnemonicModel =
            runCatching { valueOf(name) }.getOrDefault(GEMINI)
    }
}

/**
 * Exact geometry of one composite sheet.
 *
 * @param count   number of words in the batch
 * @param cols    grid columns
 * @param rows    grid rows
 * @param cell    edge length of one square cell, in pixels
 * @param gutter  uniform white separator between cells and around the border
 */
data class MnemonicSpec(
    val count: Int,
    val cols: Int,
    val rows: Int,
    val cell: Int = CELL,
    val gutter: Int = GUTTER,
) {
    val cells: Int get() = cols * rows
    val emptyCells: Int get() = (cells - count).coerceAtLeast(0)

    /** Full canvas width in pixels. */
    val canvasW: Int get() = cols * cell + (cols + 1) * gutter

    /** Full canvas height in pixels. */
    val canvasH: Int get() = rows * cell + (rows + 1) * gutter

    /** Aspect ratio string for models that need it (e.g. Midjourney). */
    val aspect: String get() = aspectRatio(canvasW, canvasH)

    /** Zero-based (row, col) of the [index]-th word, in reading order. */
    fun cellOf(index: Int): Pair<Int, Int> = Pair(index / cols, index % cols)

    /**
     * Fractional crop rectangle (0..1) of the cell at [index] — left, top,
     * right, bottom. Fractions make slicing resolution-independent.
     */
    fun fractionalRect(index: Int, insetRatio: Float = CROP_INSET): FloatArray {
        val (r, c) = cellOf(index)
        val w = canvasW.toFloat()
        val h = canvasH.toFloat()
        val x0 = (gutter + c * (cell + gutter)) / w
        val y0 = (gutter + r * (cell + gutter)) / h
        val cw = cell / w
        val ch = cell / h
        val ix = cw * insetRatio
        val iy = ch * insetRatio
        return floatArrayOf(x0 + ix, y0 + iy, x0 + cw - ix, y0 + ch - iy)
    }

    companion object {
        /** Rendered edge of one cell. 512 keeps tiles crisp on any phone. */
        const val CELL = 512

        /** White separator width — also gives the slicer a safety margin. */
        const val GUTTER = 16

        /** Trim this fraction off each side of a cell when cropping, so a
         *  slightly misaligned generation never bleeds a neighbour in. */
        const val CROP_INSET = 0.022f

        /** Longest edge stored per tile (keeps the app tiny). */
        const val TILE_PX = 512

        /** Batch bounds — 15..20 is the sweet spot for image-model fidelity. */
        const val MIN_BATCH = 2
        const val MAX_BATCH = 24
        const val DEFAULT_BATCH = 16

        /** Aspect ratios beyond this are penalised when choosing a grid. */
        private const val ASPECT_LIMIT = 1.30

        /**
         * Choose the most balanced grid for [count] words: as square as
         * possible, with as few empty cells as possible, never more than 5
         * columns (beyond that each cell loses too much detail).
         */
        fun forCount(count: Int): MnemonicSpec {
            val n = count.coerceAtLeast(1)
            if (n == 1) return MnemonicSpec(1, 1, 1)
            if (n == 2) return MnemonicSpec(2, 2, 1)
            // n >= 3 is scored below — a 3-word batch becomes 2x2 (one blank
            // cell) rather than a 3:1 strip, which models render far better.

            // Prefer near-square sheets: image models keep grid alignment far
            // more reliably close to 1:1 than on tall/wide canvases. A couple of
            // blank cells is a cheap price for a much more accurate generation.
            val ideal = sqrt(n.toDouble())
            var best: MnemonicSpec? = null
            var bestScore = Double.MAX_VALUE
            for (cols in 2..5) {
                val rows = ceil(n.toDouble() / cols).toInt()
                if (rows < 1) continue
                val waste = cols * rows - n
                val ar = maxOf(cols, rows).toDouble() / minOf(cols, rows).toDouble()
                val aspectPenalty = if (ar <= ASPECT_LIMIT) 0.0 else (ar - ASPECT_LIMIT) * (ar - ASPECT_LIMIT) * 26.0
                val score = waste * 1.15 + aspectPenalty + abs(cols - ideal) * 0.35
                if (score < bestScore) {
                    bestScore = score
                    best = MnemonicSpec(n, cols, rows)
                }
            }
            return best ?: MnemonicSpec(n, 4, ceil(n / 4.0).toInt())
        }

        private fun aspectRatio(w: Int, h: Int): String {
            val g = gcd(w, h)
            return "${w / g}:${h / g}"
        }

        private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}

/** Everything the user can tune before generating a sheet. */
data class MnemonicConfig(
    val style: MnemonicArtStyle = MnemonicArtStyle.CARTOON_3D,
    val persona: MnemonicPersona = MnemonicPersona.NONE,
    val model: MnemonicModel = MnemonicModel.GEMINI,
    /** Draw a tiny index number in each cell's gutter corner (helps verify order). */
    val numbering: Boolean = false,
)

/** Builds the precision prompt that produces a perfectly sliceable sheet. */
object MnemonicPrompt {

    /**
     * @param words the batch, in the exact order they will fill the grid
     */
    fun build(words: List<VocabWord>, spec: MnemonicSpec, cfg: MnemonicConfig): String {
        val sb = StringBuilder()

        sb.appendLine("ROLE")
        sb.appendLine(
            "You are a senior mnemonic illustrator for a vocabulary-learning app. Your images are " +
                "memory hooks: instantly readable, emotionally vivid, and impossible to confuse with each other."
        )
        sb.appendLine()

        sb.appendLine("TASK")
        sb.appendLine(
            "Produce ONE single composite image: a strict, perfectly aligned grid of " +
                "${spec.count} independent illustrations. Each grid cell is the memory image for exactly " +
                "ONE English word, and the scene must visually FUSE that word's meaning together with its " +
                "own example sentence, so that seeing the picture replays the sentence in the mind."
        )
        sb.appendLine()

        // ---------------- canvas ----------------
        sb.appendLine("== CANVAS SPECIFICATION (follow exactly) ==")
        sb.appendLine("• Output size: ${spec.canvasW} x ${spec.canvasH} pixels (width x height), aspect ${spec.aspect}.")
        sb.appendLine("• Grid: ${spec.cols} columns x ${spec.rows} rows = ${spec.cells} cells.")
        sb.appendLine("• Every cell is a PERFECT SQUARE of exactly ${spec.cell} x ${spec.cell} pixels.")
        sb.appendLine("• Separator: a uniform ${spec.gutter}px PURE WHITE (#FFFFFF) gutter between all cells AND around the outer edge.")
        sb.appendLine("• The grid must be mathematically exact — identical cell sizes, identical gutters, zero drift, no perspective.")
        sb.appendLine("• Each cell is a self-contained scene. Nothing may cross, overlap or bleed into the gutter or a neighbouring cell.")
        sb.appendLine("• Compose each subject CENTRED with a comfortable inner margin (about 8% padding) so that cropping the cell never cuts the subject.")
        if (spec.emptyCells > 0) {
            sb.appendLine(
                "• The last ${spec.emptyCells} cell(s) are UNUSED: leave them completely PURE WHITE and empty."
            )
        }
        sb.appendLine()

        // ---------------- order ----------------
        sb.appendLine("== FILL ORDER (critical) ==")
        sb.appendLine("Fill cells in strict READING ORDER: LEFT to RIGHT, then TOP to BOTTOM.")
        sb.appendLine("Cell 1 is the TOP-LEFT corner. Cell ${spec.cols} is the TOP-RIGHT corner.")
        sb.appendLine("Cell ${spec.cols + 1} begins the second row on the LEFT. Never reorder, never mirror the layout.")
        sb.appendLine()

        // ---------------- style ----------------
        sb.appendLine("== VISUAL STYLE (identical for all cells) ==")
        sb.appendLine("• ${cfg.style.directive}.")
        sb.appendLine("• One single consistent style, palette, light direction and camera height across the whole sheet.")
        sb.appendLine("• High contrast, clean uncluttered background per cell, one unmistakable focal subject.")
        sb.appendLine("• Exaggerate the key action or attribute — clarity beats realism. The idea must read in under one second.")
        if (cfg.persona != MnemonicPersona.NONE) {
            sb.appendLine()
            sb.appendLine("== RECURRING CHARACTER (must look identical in every cell) ==")
            sb.appendLine("• ${cfg.persona.sheet}.")
            sb.appendLine("• This same character performs or witnesses the action in every scene. Keep the face, hair, outfit and proportions perfectly consistent.")
        }
        sb.appendLine()

        // ---------------- hard rules ----------------
        sb.appendLine("== ABSOLUTE RULES ==")
        if (cfg.numbering) {
            sb.appendLine("1. The ONLY text allowed is a tiny neutral-grey index number placed in the white gutter just OUTSIDE the top-left corner of each cell. Never inside the artwork.")
        } else {
            sb.appendLine("1. NO text of any kind. No letters, no words, no numbers, no captions, no labels, no signage, no watermark, no signature.")
        }
        sb.appendLine("2. NO frames, borders, drop shadows, rounded corners or decorative dividers around cells — the white gutter is the only separator.")
        sb.appendLine("3. NO collage seams, NO torn-paper or polaroid effects, NO mock-up device frames.")
        sb.appendLine("4. Each cell depicts ONLY its assigned word. Do not blend two words into one cell.")
        sb.appendLine("5. Keep every scene wholesome, safe and culturally neutral.")
        sb.appendLine("6. Deliver the complete grid in a SINGLE image at full resolution — never a series, never a partial sheet.")
        sb.appendLine()

        // ---------------- per-cell content ----------------
        sb.appendLine("== CELL CONTENT — ${spec.count} SCENES ==")
        words.forEachIndexed { i, w ->
            val (r, c) = spec.cellOf(i)
            sb.appendLine()
            sb.appendLine("[CELL ${i + 1}] row ${r + 1}, column ${c + 1}")
            sb.appendLine("  WORD    : ${w.english}")
            if (w.arabic.isNotBlank() && w.arabic != "—") {
                sb.appendLine("  MEANING : ${w.arabic}")
            }
            if (w.exampleEn.isNotBlank()) {
                sb.appendLine("  SENTENCE: \"${w.exampleEn}\"")
                sb.appendLine(
                    "  SCENE   : Illustrate the sentence literally and memorably, staging it so that the " +
                        "meaning of \"${w.english}\" is the single most obvious thing in the frame."
                )
            } else {
                sb.appendLine(
                    "  SCENE   : Illustrate the meaning of \"${w.english}\" as one vivid, exaggerated, " +
                        "unmistakable moment."
                )
            }
        }
        sb.appendLine()

        // ---------------- negatives ----------------
        sb.appendLine("== NEGATIVE PROMPT ==")
        sb.appendLine(
            "text, letters, words, numbers, captions, watermark, signature, logo, frames, borders, " +
                "uneven grid, misaligned cells, different cell sizes, cells bleeding into each other, " +
                "cropped subjects, blurry, low detail, duplicated subjects, extra limbs, distorted faces, " +
                "cluttered background, dark muddy colours, collage seams"
        )

        val suffix = cfg.model.suffix.replace("{AR}", spec.aspect)
        if (suffix.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(suffix)
        }

        return sb.toString().trim()
    }

    /** Short human summary shown above the prompt in the UI. */
    fun summary(spec: MnemonicSpec): String =
        "${spec.count} كلمة · شبكة ${spec.cols}×${spec.rows} · ${spec.canvasW}×${spec.canvasH}px · خلية ${spec.cell}px"
}
