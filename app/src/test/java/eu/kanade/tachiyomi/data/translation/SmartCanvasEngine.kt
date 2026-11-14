package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class BubbleRegion(
    val boundingBox: Rect,
    val elementBoxes: List<Rect>,
    val originalText: String,
    var translatedText: String = "",
    var detectedTextColor: Int = Color.BLACK,
    var detectedStrokeColor: Int = Color.WHITE
)

object SmartCanvasEngine {

    /**
     * 1. تجميع السطور المتقاربة في فقاعة حوار واحدة لضمان ترجمة مترابطة وتوسيط سليم
     */
    fun extractAndClusterBubbles(visionText: Text): List<BubbleRegion> {
        val rawBubbles = mutableListOf<BubbleRegion>()

        for (block in visionText.textBlocks) {
            val box = block.boundingBox ?: continue
            val elements = mutableListOf<Rect>()
            for (line in block.lines) {
                for (element in line.elements) {
                    element.boundingBox?.let { elements.add(it) }
                }
            }
            val cleanText = block.text.replace("\n", " ").trim()
            if (cleanText.isNotEmpty() && box.width() > 10 && box.height() > 10) {
                rawBubbles.add(
                    BubbleRegion(
                        boundingBox = Rect(box),
                        elementBoxes = elements,
                        originalText = cleanText
                    )
                )
            }
        }

        // دمج الكتل المتقاربة جداً داخل نفس الفقاعة
        val merged = mutableListOf<BubbleRegion>()
        val used = BooleanArray(rawBubbles.size)

        for (i in rawBubbles.indices) {
            if (used[i]) continue
            var currentBox = Rect(rawBubbles[i].boundingBox)
            val currentElements = rawBubbles[i].elementBoxes.toMutableList()
            val textBuilder = StringBuilder(rawBubbles[i].originalText)
            used[i] = true

            var changed = true
            while (changed) {
                changed = false
                for (j in rawBubbles.indices) {
                    if (used[j]) continue
                    val other = rawBubbles[j].boundingBox
                    val expandMargin = max(12, min(currentBox.height(), other.height()) / 3)
                    val expanded = Rect(
                        currentBox.left - expandMargin,
                        currentBox.top - expandMargin,
                        currentBox.right + expandMargin,
                        currentBox.bottom + expandMargin
                    )
                    if (Rect.intersects(expanded, other)) {
                        currentBox.union(other)
                        currentElements.addAll(rawBubbles[j].elementBoxes)
                        textBuilder.append(" ").append(rawBubbles[j].originalText)
                        used[j] = true
                        changed = true
                    }
                }
            }
            merged.add(
                BubbleRegion(
                    boundingBox = currentBox,
                    elementBoxes = currentElements,
                    originalText = textBuilder.toString()
                )
            )
        }
        return merged
    }

    /**
     * 2. المسح الذكي للحروف فقط (Pixel-Level Inpainting) دون تشويه الخلفية + استخراج لون الخط الأصلي
     */
    fun inpaintBubble(bitmap: Bitmap, bubble: BubbleRegion) {
        val imgW = bitmap.width
        val imgH = bitmap.height
        val targetRects = if (bubble.elementBoxes.isNotEmpty()) bubble.elementBoxes else listOf(bubble.boundingBox)

        var textR = 0L; var textG = 0L; var textB = 0L; var textCount = 0L
        var bgR = 0L; var bgG = 0L; var bgB = 0L; var bgCount = 0L

        for (wordRect in targetRects) {
            val pad = 4
            val left = (wordRect.left - pad).coerceIn(0, imgW - 1)
            val top = (wordRect.top - pad).coerceIn(0, imgH - 1)
            val right = (wordRect.right + pad).coerceIn(0, imgW - 1)
            val bottom = (wordRect.bottom + pad).coerceIn(0, imgH - 1)

            val w = right - left + 1
            val h = bottom - top + 1
            if (w <= 2 || h <= 2) continue

            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, left, top, w, h)

            // أ) حساب متوسط لون الخلفية المحيطة بحواف الكلمة
            var borderR = 0; var borderG = 0; var borderB = 0; var borderPixels = 0
            for (x in 0 until w) {
                val topPx = pixels[x]
                val botPx = pixels[(h - 1) * w + x]
                borderR += Color.red(topPx) + Color.red(botPx)
                borderG += Color.green(topPx) + Color.green(botPx)
                borderB += Color.blue(topPx) + Color.blue(botPx)
                borderPixels += 2
            }
            for (y in 0 until h) {
                val leftPx = pixels[y * w]
                val rightPx = pixels[y * w + (w - 1)]
                borderR += Color.red(leftPx) + Color.red(rightPx)
                borderG += Color.green(leftPx) + Color.green(rightPx)
                borderB += Color.blue(leftPx) + Color.blue(rightPx)
                borderPixels += 2
            }
            val avgBgR = borderR / max(1, borderPixels)
            val avgBgG = borderG / max(1, borderPixels)
            val avgBgB = borderB / max(1, borderPixels)

            bgR += avgBgR; bgG += avgBgG; bgB += avgBgB; bgCount++

            // ب) بناء قناع الحروف (Mask) بعزل البكسلات المخالفة للون الخلفية المحلي
            val mask = BooleanArray(w * h)
            val threshold = 42.0 // حساسية عزل الحرف عن الخلفية

            for (idx in pixels.indices) {
                val c = pixels[idx]
                val dr = Color.red(c) - avgBgR
                val dg = Color.green(c) - avgBgG
                val db = Color.blue(c) - avgBgB
                val dist = sqrt((dr * dr + dg * dg + db * db).toDouble())
                if (dist > threshold) {
                    mask[idx] = true
                    textR += Color.red(c)
                    textG += Color.green(c)
                    textB += Color.blue(c)
                    textCount++
                }
            }

            // ج) توسيع القناع بمقدار 2 بكسل (Dilation) لمسح الحواف والظلال المحيطة بالحرف الإنجليزي
            val dilatedMask = mask.copyOf()
            val radius = 2
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (mask[y * w + x]) {
                        for (dy in -radius..radius) {
                            for (dx in -radius..radius) {
                                val nx = x + dx
                                val ny = y + dy
                                if (nx in 0 until w && ny in 0 until h) {
                                    dilatedMask[ny * w + nx] = true
                                }
                            }
                        }
                    }
                }
            }

            // د) ترميم البكسلات الممسوحة من الخلفية المحيطة (Multi-pass Diffusion Inpainting)
            val passes = 12
            for (pass in 0 until passes) {
                for (y in 1 until h - 1) {
                    for (x in 1 until w - 1) {
                        val idx = y * w + x
                        if (dilatedMask[idx]) {
                            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
                            val neighbors = intArrayOf(
                                (y - 1) * w + x,
                                (y + 1) * w + x,
                                y * w + (x - 1),
                                y * w + (x + 1)
                            )
                            for (nIdx in neighbors) {
                                if (!dilatedMask[nIdx] || pass > 0) {
                                    val nc = pixels[nIdx]
                                    val weight = if (!dilatedMask[nIdx]) 3 else 1
                                    sumR += Color.red(nc) * weight
                                    sumG += Color.green(nc) * weight
                                    sumB += Color.blue(nc) * weight
                                    count += weight
                                }
                            }
                            if (count > 0) {
                                pixels[idx] = Color.rgb(sumR / count, sumG / count, sumB / count)
                            } else {
                                pixels[idx] = Color.rgb(avgBgR, avgBgG, avgBgB)
                            }
                        }
                    }
                }
            }

            bitmap.setPixels(pixels, 0, w, left, top, w, h)
        }

        // حفظ ألوان النص الأصلي والخلفية لاستخدامها في الكتابة العربية
        if (textCount > 0 && bgCount > 0) {
            bubble.detectedTextColor = Color.rgb(
                (textR / textCount).toInt(),
                (textG / textCount).toInt(),
                (textB / textCount).toInt()
            )
            bubble.detectedStrokeColor = Color.rgb(
                (bgR / bgCount).toInt(),
                (bgG / bgCount).toInt(),
                (bgB / bgCount).toInt()
            )
        }
    }

    /**
     * 3. رسم النص العربي داخل الفقاعة مع تحجيم تلقائي وإطار خارجي (Stroke)
     */
    fun drawArabicText(
        canvas: Canvas,
        bubble: BubbleRegion,
        typeface: Typeface?,
        useOriginalColor: Boolean = true
    ) {
        val text = bubble.translatedText.trim()
        if (text.isEmpty()) return

        val box = bubble.boundingBox
        val padding = max(4, (box.width() * 0.04f).toInt())
        val availWidth = max(20, box.width() - padding * 2)
        val availHeight = max(20, box.height() - padding * 2)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // البحث عن أفضل حجم خط يملأ الفقاعة دون الخروج عن حدودها
        var bestSize = max(14f, min(box.width(), box.height()) * 0.45f)
        val minSize = 11f
        var layout: StaticLayout

        while (true) {
            paint.textSize = bestSize
            layout = buildStaticLayout(text, paint, availWidth)
            if (layout.height <= availHeight || bestSize <= minSize) {
                break
            }
            bestSize -= 1.5f
        }

        val startX = box.left + padding + (availWidth - layout.width) / 2f
        val startY = box.top + padding + (availHeight - layout.height) / 2f

        canvas.save()
        canvas.translate(startX, startY)

        // أ) رسم الإطار الخارجي (Stroke) لضمان وضوح النص فوق أي خلفية ملونة
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(3f, bestSize * 0.16f)
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = if (useOriginalColor) bubble.detectedStrokeColor else Color.WHITE
        layout.draw(canvas)

        // ب) رسم النص الأساسي (Fill)
        paint.style = Paint.Style.FILL
        paint.color = if (useOriginalColor) bubble.detectedTextColor else Color.BLACK
        layout.draw(canvas)

        canvas.restore()
    }

    private fun buildStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.0f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0f, false)
        }
    }
}
