package com.simiacryptus.cognotik.image

import com.simiacryptus.cognotik.models.ModelSchema

interface ImageClientInterface {
    fun createImage(request: ModelSchema.ImageGenerationRequest): ModelSchema.ImageGenerationResponse
    fun getModels(): List<ImageModel>?
}