package com.simiacryptus.cognotik.agents

import java.awt.image.BufferedImage

data class ImageAndText(
    val text: String,
    val image: BufferedImage? = null,
)