/**
 * Exception thrown when a safety violation is detected
 */
package com.simiacryptus.jopenai.exceptions

/**
 * Exception thrown when a safety violation is detected
 */
class SafetyException : AIServiceException {
    constructor() : super("Safety violation")
}