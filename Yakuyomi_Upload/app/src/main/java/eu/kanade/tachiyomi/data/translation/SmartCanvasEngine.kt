package eu.kanade.tachiyomi.data.translation

import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import li.joye.yakuyomi.engine.PageAnalysis
import li.joye.yakuyomi.engine.TextRegion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object SmartCanvasEngine {

    private val arabicRegex = Regex("[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]")

    fun hasArabicText(analysis: PageAnalysis): Boolean {
        return analysis.regions.any { arabicRegex.containsMatchIn(it.translatedText) }
    }

    fun renderArabicOnto(original: Bitmap, targetPage: Bitmap, analysis: PageAnalysis) {
        val work = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(work)
        val segMask = analysis.mask

        for (region in analysis.regions) {
            if (region.translatedText.isBlank()) continue
            val (textColor, strokeColor) = inpaintRegionSmart(work, region, segMask)
            drawArabicRegion(canvas, region, null, textColor, strokeColor)
        }

        if (targetPage.isMutable && targetPage.width == work.width && targetPage.height == work.height) {
            val w = work.width
            val h = work.height
            val px = IntArray(w * h)
            work.getPixels(px, 0, w, 0, 0, w, h)
            targetPage.setPixels(px, 0, w, 0, 0, w, h)
        }
        work.recycle()
    }

    fun inpaintRegionSmart(bitmap: Bitmap, region: TextRegion, segMask: Bitmap? = null): Pair<Int, Int> {
        val imgW = bitmap.width
        val imgH = bitmap.height

        val pad = 6
        val left = (region.x0.toInt() - pad).coerceIn(0, imgW - 1)
        val top = (region.y0.toInt() - pad).coerceIn(0, imgH - 1)
        val right = (region.x1.toInt() + pad).coerceIn(0, imgW - 1)
        val bottom = (region.y1.toInt() + pad).coerceIn(0, imgH - 1)

        val w = right - left + 1
        val h = bottom - top + 1
        if (w <= 4 || h <= 4) return Color.BLACK to Color.WHITE

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, left, top, w, h)

        var borderR = 0; var borderG = 0; var borderB = 0; var borderCount = 0
        for (x in 0 until w) {
            val topPx = pixels[x]
            val botPx = pixels[(h - 1) * w + x]
            borderR += Color.red(topPx) + Color.red(botPx)
            borderG += Color.green(topPx) + Color.green(botPx)
            borderB += Color.blue(topPx) + Color.blue(botPx)
            borderCount += 2
        }
        for (y in 0 until h) {
            val leftPx = pixels[y * w]
            val rightPx = pixels[y * w + (w - 1)]
            borderR += Color.red(leftPx) + Color.red(rightPx)
            borderG += Color.green(leftPx) + Color.green(rightPx)
            borderB += Color.blue(leftPx) + Color.blue(rightPx)
            borderCount += 2
        }

        val avgBgR = borderR / max(1, borderCount)
        val avgBgG = borderG / max(1, borderCount)
        val avgBgB = borderB / max(1, borderCount)

        val mask = BooleanArray(w * h)
        var textR = 0L; var textG = 0L; var textB = 0L; var textCount = 0L

        if (segMask != null && segMask.width == imgW && segMask.height == imgH) {
            val maskPixels = IntArray(w * h)
            segMask.getPixels(maskPixels, 0, w, left, top, w, h)
            for (idx in pixels.indices) {
                if ((maskPixels[idx] and 0xFF) > 80) {
                    mask[idx] = true
                    val c = pixels[idx]
                    textR += Color.red(c)
                    textG += Color.green(c)
                    textB += Color.blue(c)
                    textCount++
                }
            }
        } else {
            val threshold = 42.0
            for (idx in pixels.indices) {
                val c = pixels[idx]
                val dr = Color.red(c) - avgBgR
                val dg = Color.green(c) - avgBgG
                val db = Color.blue(c) - avgBgB
                if (sqrt((dr * dr + dg * dg + db * db).toDouble()) > threshold) {
                    mask[idx] = true
                    textR += Color.red(c)
                    textG += Color.green(c)
                    textB += Color.blue(c)
                    textCount++
                }
            }
        }

        val dilated = mask.copyOf()
        val radius = 3
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (mask[y * w + x]) {
                    for (dy in -radius..radius) {
                        for (dx in -radius..radius) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx in 0 until w && ny in 0 until h) {
                                dilated[ny * w + nx] = true
                            }
                        }
                    }
                }
            }
        }

        for (pass in 0 until 14) {
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    if (dilated[idx]) {
                        var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
                        val neighbors = intArrayOf(
                            (y - 1) * w + x,
                            (y + 1) * w + x,
                            y * w + (x - 1),
                            y * w + (x - 1)
                        )
                        for (nIdx in neighbors) {
                            if (!dilated[nIdx] || pass > 0) {
                                val nc = pixels[nIdx]
                                val weight = if (!dilated[nIdx]) 3 else 1
                                sumR += Color.red(nc) * weight
                                sumG += Color.green(nc) * weight
                                sumB += Color.blue(nc) * weight
                                count += weight
                            }
                        }
                        pixels[idx] = if (count > 0) {
                            Color.rgb(sumR / count, sumG / count, sumB / count)
                        } else {
                            Color.rgb(avgBgR, avgBgG, avgBgB)
                        }
                    }
                }
            }
        }

        bitmap.setPixels(pixels, 0, w, left, top, w, h)

        val bgLum = (0.299 * avgBgR + 0.587 * avgBgG + 0.114 * avgBgB)
        val detectedStroke = Color.rgb(avgBgR, avgBgG, avgBgB)
        val detectedText = if (textCount > 0) {
            val tr = (textR / textCount).toInt()
            val tg = (textG / textCount).toInt()
            val tb = (textB / textCount).toInt()
            val txtLum = (0.299 * tr + 0.587 * tg + 0.114 * tb)
            if (abs(txtLum - bgLum) < 60) {
                if (bgLum > 128) Color.BLACK else Color.WHITE
            } else {
                Color.rgb(tr, tg, tb)
            }
        } else {
            if (bgLum > 128) Color.BLACK else Color.WHITE
        }

        return detectedText to detectedStroke
    }

    fun drawArabicRegion(
        canvas: Canvas,
        region: TextRegion,
        typeface: Typeface? = null,
        textColor: Int = Color.BLACK,
        strokeColor: Int = Color.WHITE
    ) {
        val text = region.translatedText.trim()
        if (text.isEmpty()) return

        val boxW = max(28, (region.x1 - region.x0).toInt())
        val boxH = max(28, (region.y1 - region.y0).toInt())
        val padding = max(4, (min(boxW, boxH) * 0.04f).toInt())
        val availW = max(24, boxW - padding * 2)
        val availH = max(24, boxH - padding * 2)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var bestSize = max(15f, min(availW, availH) * 0.44f).coerceAtMost(58f)
        val minSize = 11f
        var layout: StaticLayout

        while (true) {
            paint.textSize = bestSize
            layout = buildLayout(text, paint, availW)
            if (layout.height <= availH || bestSize <= minSize) break
            bestSize -= 1.5f
        }

        val cx = (region.x0 + region.x1) / 2f
        val cy = (region.y0 + region.y1) / 2f
        val startX = cx - layout.width / 2f
        val startY = cy - layout.height / 2f

        canvas.save()
        canvas.translate(startX, startY)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(3.5f, bestSize * 0.18f)
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = strokeColor
        layout.draw(canvas)

        paint.style = Paint.Style.FILL
        paint.color = textColor
        layout.draw(canvas)

        canvas.restore()
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
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