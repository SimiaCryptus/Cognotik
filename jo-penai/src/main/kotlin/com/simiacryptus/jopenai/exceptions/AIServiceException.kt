package com.simiacryptus.jopenai.exceptions

import java.io.IOException

open class AIServiceException(message: String?, val isFatal: Boolean = false) : IOException(message)