package com.simiacryptus.cognotik.util

import java.awt.image.BufferedImage
import kotlin.math.*

object ImagePatchLocalization {
  data class SubImageBounds(val x: Int, val y: Int, val width: Int, val height: Int, val score: Double)

  fun findBounds(
    parentImage: BufferedImage,
    subImage: BufferedImage,
    estimate: SubImageBounds? = null
  ): SubImageBounds {
    val maxDim = 200
    val globalScale = min(1.0, maxDim.toDouble() / max(parentImage.width, parentImage.height))

    val smallParent = normalize(resize(parentImage, globalScale))
    val smallSub = normalize(resize(subImage, globalScale))

    fun score(x: Double, y: Double, s: Double): Double {
      val w = (smallSub.width * s).toInt()
      val h = (smallSub.height * s).toInt()
      if (w <= 5 || h <= 5 || w > smallParent.width || h > smallParent.height) return Double.MAX_VALUE

      val ix = x.toInt()
      val iy = y.toInt()
      if (ix < 0 || iy < 0 || ix + w > smallParent.width || iy + h > smallParent.height) return Double.MAX_VALUE

      var penalty = 0.0
      if (estimate != null) {
        val estX = estimate.x * globalScale
        val estY = estimate.y * globalScale
        val estW = estimate.width * globalScale
        val estH = estimate.height * globalScale
        val currW = smallSub.width * s
        val currH = smallSub.height * s
        penalty = (x - estX).pow(2) + (y - estY).pow(2) + (currW - estW).pow(2) + (currH - estH).pow(2)
      }

      val scaledSub = resize(smallSub, w, h)
      return diff(smallParent, ix, iy, scaledSub, Double.MAX_VALUE) + penalty
    }

    var bestScore = Double.MAX_VALUE
    var bestX = 0.0
    var bestY = 0.0
    var bestS = 1.0

    if (estimate != null) {
      bestX = estimate.x * globalScale
      bestY = estimate.y * globalScale
      bestS = estimate.width.toDouble() / subImage.width
      bestScore = score(bestX, bestY, bestS)
    } else {
      val scales = listOf(0.5, 0.75, 1.0, 1.25, 1.5)
      for (s in scales) {
        val w = (smallSub.width * s).toInt()
        val h = (smallSub.height * s).toInt()
        if (w > smallParent.width || h > smallParent.height) continue
        val step = max(1, min(smallParent.width, smallParent.height) / 10)
        for (y in 0 until (smallParent.height - h) step step) {
          for (x in 0 until (smallParent.width - w) step step) {
            val sc = score(x.toDouble(), y.toDouble(), s)
            if (sc < bestScore) {
              bestScore = sc
              bestX = x.toDouble()
              bestY = y.toDouble()
              bestS = s
            }
          }
        }
      }
    }

    var stepX = if (estimate != null) 5.0 else max(1.0, smallParent.width / 4.0)
    var stepY = if (estimate != null) 5.0 else max(1.0, smallParent.height / 4.0)
    var stepS = if (estimate != null) 0.1 else 0.2

    while (stepX > 0.5 || stepY > 0.5 || stepS > 0.01) {
      var improved = false
      fun tryMove(nx: Double, ny: Double, ns: Double): Boolean {
        val s = score(nx, ny, ns)
        if (s < bestScore) {
          bestScore = s
          bestX = nx
          bestY = ny
          bestS = ns
          return true
        }
        return false
      }

      if (stepX > 0.5) {
        val s0 = bestScore
        val sLeft = score(bestX - stepX, bestY, bestS)
        val sRight = score(bestX + stepX, bestY, bestS)
        if (sLeft < s0 && sLeft < sRight) {
          bestX -= stepX; bestScore = sLeft; improved = true
        } else if (sRight < s0) {
          bestX += stepX; bestScore = sRight; improved = true
        } else if (sLeft != Double.MAX_VALUE && sRight != Double.MAX_VALUE) {
          val denom = sLeft - 2 * s0 + sRight
          if (denom > 1e-5) {
            val delta = stepX * (sLeft - sRight) / (2 * denom)
            if (abs(delta) < stepX) tryMove(bestX + delta, bestY, bestS)
          }
        }
      }
      if (stepY > 0.5) {
        val s0 = bestScore
        val sUp = score(bestX, bestY - stepY, bestS)
        val sDown = score(bestX, bestY + stepY, bestS)
        if (sUp < s0 && sUp < sDown) {
          bestY -= stepY; bestScore = sUp; improved = true
        } else if (sDown < s0) {
          bestY += stepY; bestScore = sDown; improved = true
        } else if (sUp != Double.MAX_VALUE && sDown != Double.MAX_VALUE) {
          val denom = sUp - 2 * s0 + sDown
          if (denom > 1e-5) {
            val delta = stepY * (sUp - sDown) / (2 * denom)
            if (abs(delta) < stepY) tryMove(bestX, bestY + delta, bestS)
          }
        }
      }
      if (stepS > 0.01) {
        val s0 = bestScore
        val sSmall = score(bestX, bestY, bestS - stepS)
        val sLarge = score(bestX, bestY, bestS + stepS)
        if (sSmall < s0 && sSmall < sLarge) {
          bestS -= stepS; bestScore = sSmall; improved = true
        } else if (sLarge < s0) {
          bestS += stepS; bestScore = sLarge; improved = true
        } else if (sSmall != Double.MAX_VALUE && sLarge != Double.MAX_VALUE) {
          val denom = sSmall - 2 * s0 + sLarge
          if (denom > 1e-5) {
            val delta = stepS * (sSmall - sLarge) / (2 * denom)
            if (abs(delta) < stepS) tryMove(bestX, bestY, bestS + delta)
          }
        }
      }
      if (!improved) {
        stepX /= 2
        stepY /= 2
        stepS /= 2
      }
    }

    // Clamp to parent bounds
    val finalW = (smallSub.width * bestS).toInt()
    val finalH = (smallSub.height * bestS).toInt()
    val finalX = (bestX / globalScale).toInt()
    val finalY = (bestY / globalScale).toInt()
    val finalWOrig = (finalW / globalScale).toInt()
    val finalHOrig = (finalH / globalScale).toInt()

    val cx = max(0, min(parentImage.width - 1, finalX))
    val cy = max(0, min(parentImage.height - 1, finalY))
    val cw = min(parentImage.width - cx, finalWOrig)
    val ch = min(parentImage.height - cy, finalHOrig)

    return SubImageBounds(cx, cy, cw, ch, bestScore)
  }

  private fun normalize(image: BufferedImage): BufferedImage {
    val width = image.width
    val height = image.height
    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
    var sumR = 0.0
    var sumG = 0.0
    var sumB = 0.0
    var sumSqR = 0.0
    var sumSqG = 0.0
    var sumSqB = 0.0
    for (pixel in pixels) {
      val r = (pixel shr 16) and 0xFF
      val g = (pixel shr 8) and 0xFF
      val b = pixel and 0xFF
      sumR += r
      sumG += g
      sumB += b
      sumSqR += r * r
      sumSqG += g * g
      sumSqB += b * b
    }
    val count = pixels.size.toDouble()
    val meanR = sumR / count
    val meanG = sumG / count
    val meanB = sumB / count
    val stdR = sqrt(max(0.0, sumSqR / count - meanR * meanR))
    val stdG = sqrt(max(0.0, sumSqG / count - meanG * meanG))
    val stdB = sqrt(max(0.0, sumSqB / count - meanB * meanB))
    for (i in pixels.indices) {
      val pixel = pixels[i]
      val r = (pixel shr 16) and 0xFF
      val g = (pixel shr 8) and 0xFF
      val b = pixel and 0xFF
      val newR = ((r - meanR) / (stdR + 1.0) * 64.0 + 128.0).coerceIn(0.0, 255.0).toInt()
      val newG = ((g - meanG) / (stdG + 1.0) * 64.0 + 128.0).coerceIn(0.0, 255.0).toInt()
      val newB = ((b - meanB) / (stdB + 1.0) * 64.0 + 128.0).coerceIn(0.0, 255.0).toInt()
      pixels[i] = (newR shl 16) or (newG shl 8) or newB
    }
    val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    result.setRGB(0, 0, width, height, pixels, 0, width)
    return result
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