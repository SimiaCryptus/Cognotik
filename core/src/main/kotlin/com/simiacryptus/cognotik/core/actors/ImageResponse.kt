package com.simiacryptus.cognotik.core.actors

import java.awt.image.BufferedImage

interface ImageResponse {
  val text: String
  val image: BufferedImage
}