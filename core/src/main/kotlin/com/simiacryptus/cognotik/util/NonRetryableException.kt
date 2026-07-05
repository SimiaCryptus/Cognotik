package com.simiacryptus.cognotik.util

open class NonRetryableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
open class BudgetException(message: String, cause: Throwable? = null) : NonRetryableException(message, cause)