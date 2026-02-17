package com.simiacryptus.cognotik.image

import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.util.LoggerFactory

class ImageModel(
  val name: String,
  override val modelId: String,
  val maxPrompt: Int,
  override val provider: APIProvider,
  val quality: String = "standard",
  val pricingFunction: (width: Int, height: Int) -> Double
) : AIModel {

    fun pricing(width: Int, height: Int): Double = pricingFunction(width, height)

    companion object {
        private val log = LoggerFactory.getLogger(ImageModel::class.java)
    }
}