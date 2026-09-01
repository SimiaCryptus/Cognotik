package com.simiacryptus.cognotik.platform.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.slf4j.LoggerFactory

class ImageModel(
  val name: String,
  override val modelId: String,
  val maxPrompt: Int,
  override val provider: APIProvider,
  val quality: String = "standard",
  @JsonIgnore val pricingFunction: (Int, Int) -> Double = { _, _ ->
    log.warn("Pricing function not defined for model $name, defaulting to 0.0")
    0.0
  },
) : AIModel {

  fun pricing(width: Int, height: Int): Double = pricingFunction(width, height)

  companion object {
    private val log = LoggerFactory.getLogger(ImageModel::class.java)
  }
}