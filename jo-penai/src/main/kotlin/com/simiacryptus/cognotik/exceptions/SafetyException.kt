/**
 * Exception thrown when a safety violation is detected
 */
package com.simiacryptus.cognotik.exceptions

/**
 * Exception thrown when a safety violation is detected
 */
class SafetyException : AIServiceException {
    constructor() : super("Safety violation")
}