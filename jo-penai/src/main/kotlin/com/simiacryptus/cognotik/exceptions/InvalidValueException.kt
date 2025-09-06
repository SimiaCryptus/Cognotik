package com.simiacryptus.cognotik.exceptions

class InvalidValueException(field: String?, value: String?) :
    AIServiceException("Invalid value: $field = $value", isFatal = true)