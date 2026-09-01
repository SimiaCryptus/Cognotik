package com.simiacryptus.cognotik.platform.model

interface ImageClientInterface {
  fun createImage(request: ModelSchema.ImageGenerationRequest): ModelSchema.ImageGenerationResponse
  fun getModels(): List<ImageModel>?
}