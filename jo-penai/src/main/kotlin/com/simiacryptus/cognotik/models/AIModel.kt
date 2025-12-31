package com.simiacryptus.cognotik.models

interface AIModel {
    val modelName: String?
    val provider: APIProvider?
}