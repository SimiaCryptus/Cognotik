package com.simiacryptus.cognotik.exceptions

import java.io.IOException

open class AIServiceException(message: String?, val isFatal: Boolean = false) : IOException(message)