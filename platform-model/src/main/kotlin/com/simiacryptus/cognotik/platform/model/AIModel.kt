package com.simiacryptus.cognotik.platform.model

interface AIModel {
  val modelId: String?
  val provider: APIProvider?
  fun pricing(usage: ModelSchema.Usage): Double = 0.0
}