package com.simiacryptus.cognotik.models

interface AIModel {
  val modelId: String?
  val provider: APIProvider?
  fun pricing(usage: ModelSchema.Usage): Double = 0.0
}