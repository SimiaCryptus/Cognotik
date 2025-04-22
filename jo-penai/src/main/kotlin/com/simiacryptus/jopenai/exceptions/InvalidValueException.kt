package com.simiacryptus.jopenai.exceptions

class InvalidValueException(field: String?, value: String?) : AIServiceException("Invalid value: $field = $value", isFatal = true)