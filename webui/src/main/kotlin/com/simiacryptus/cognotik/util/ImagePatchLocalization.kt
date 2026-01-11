package com.simiacryptus.cognotik.util

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

object ImagePatchLocalization {
    data class SubImageBounds(val x: Int, val y: Int, val width: Int, val height: Int, val score: Double)

    fun findBounds(parentImage: BufferedImage, subImage: BufferedImage, estimate: SubImageBounds? = null): SubImageBounds {
        val maxDim = 200
        val scale = min(1.0, maxDim.toDouble() / max(parentImage.width, parentImage.height))

        val smallParent = resize(parentImage, scale)
        val smallSub = resize(subImage, scale)

        var bestScore = Double.MAX_VALUE
        var bestRect = SubImageBounds(0, 0, parentImage.width, parentImage.height, Double.MAX_VALUE)
        if (estimate != null) {
            val estX = (estimate.x * scale).toInt()
            val estY = (estimate.y * scale).toInt()
            val estW = (estimate.width * scale).toInt()
            val estH = (estimate.height * scale).toInt()
            if (estW > 0 && estH > 0 && estX >= 0 && estY >= 0 && estX + estW <= smallParent.width && estY + estH <= smallParent.height) {
                val scaledSub = resize(smallSub, estW, estH)
                val score = diff(smallParent, estX, estY, scaledSub, bestScore)
                if (score < bestScore) {
                    bestScore = score
                    bestRect = estimate.copy(score = score)
                }
            }
        }


        // Search scales from 0.5 to 1.5 to handle expanded context (zoomed out) or slight zoom in
        val scales = (50..150 step 5).map { it / 100.0 }

        for (s in scales) {
            val targetW = (smallSub.width * s).toInt()
            val targetH = (smallSub.height * s).toInt()
            if (targetW <= 5 || targetH <= 5 || targetW > smallParent.width || targetH > smallParent.height) continue

            val scaledSub = resize(smallSub, targetW, targetH)
            val step = 1

            for (y in 0 until (smallParent.height - targetH) step step) {
                for (x in 0 until (smallParent.width - targetW) step step) {
                    val score = diff(smallParent, x, y, scaledSub, bestScore)
                    if (score < bestScore) {
                        bestScore = score
                        val origX = (x / scale).toInt()
                        val origY = (y / scale).toInt()
                        val origW = (targetW / scale).toInt()
                        val origH = (targetH / scale).toInt()
                        bestRect = SubImageBounds(origX, origY, origW, origH, score)
                    }
                }
            }
        }

        // Clamp to parent bounds
        val finalX = max(0, min(parentImage.width - 1, bestRect.x))
        val finalY = max(0, min(parentImage.height - 1, bestRect.y))
        val finalW = min(parentImage.width - finalX, bestRect.width)
        val finalH = min(parentImage.height - finalY, bestRect.height)

        return bestRect.copy(x = finalX, y = finalY, width = finalW, height = finalH)
    }

    private fun resize(img: BufferedImage, scale: Double): BufferedImage {
        val w = max(1, (img.width * scale).toInt())
        val h = max(1, (img.height * scale).toInt())
        return resize(img, w, h)
    }

    private fun resize(img: BufferedImage, w: Int, h: Int): BufferedImage {
        val resized = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(img, 0, 0, w, h, null)
        g.dispose()
        return resized
    }

    private fun diff(img1: BufferedImage, x1: Int, y1: Int, img2: BufferedImage, maxScore: Double): Double {
        var sum = 0.0
        val w = img2.width
        val h = img2.height
        val limit = maxScore * w * h
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rgb1 = img1.getRGB(x1 + x, y1 + y)
                val rgb2 = img2.getRGB(x, y)
                val dr = ((rgb1 shr 16) and 0xFF) - ((rgb2 shr 16) and 0xFF)
                val dg = ((rgb1 shr 8) and 0xFF) - ((rgb2 shr 8) and 0xFF)
                val db = (rgb1 and 0xFF) - (rgb2 and 0xFF)
                sum += (dr * dr + dg * dg + db * db)
                if (sum > limit) return Double.MAX_VALUE
            }
        }
        return sum / (w * h)
    }
}