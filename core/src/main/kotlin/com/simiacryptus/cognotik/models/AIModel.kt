package com.simiacryptus.cognotik.models

interface AIModel {
    val modelId: String?
    val provider: APIProvider?
}