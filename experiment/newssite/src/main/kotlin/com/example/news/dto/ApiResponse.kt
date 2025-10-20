package com.example.news.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * Generic wrapper for successful API responses.
 * Provides a consistent structure for all successful API calls.
 *
 * @param T The type of data being returned
 * @property data The actual response data
 * @property timestamp The time when the response was generated
 * @property message Optional success message
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T,
    val timestamp: Instant = Instant.now(),
    val message: String? = null
) {
    companion object {
        /**
         * Creates a successful response with data.
         */
        fun <T> success(data: T, message: String? = null): ApiResponse<T> {
            return ApiResponse(
                success = true,
                data = data,
                message = message
            )
        }
    }
}

/**
 * Wrapper for error responses.
 * Provides detailed error information in a consistent format.
 *
 * @property success Always false for error responses
 * @property error Error details
 * @property timestamp The time when the error occurred
 * @property path The request path that caused the error
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val success: Boolean = false,
    val error: ErrorDetail,
    val timestamp: Instant = Instant.now(),
    val path: String? = null
) {
    companion object {
        /**
         * Creates an error response with the given details.
         */
        fun of(
            code: String,
            message: String,
            details: String? = null,
            path: String? = null
        ): ErrorResponse {
            return ErrorResponse(
                error = ErrorDetail(
                    code = code,
                    message = message,
                    details = details
                ),
                path = path
            )
        }

        /**
         * Creates an error response from an exception.
         */
        fun fromException(
            exception: Exception,
            code: String = "INTERNAL_ERROR",
            path: String? = null
        ): ErrorResponse {
            return ErrorResponse(
                error = ErrorDetail(
                    code = code,
                    message = exception.message ?: "An unexpected error occurred",
                    details = exception.javaClass.simpleName
                ),
                path = path
            )
        }
    }
}

/**
 * Detailed error information.
 *
 * @property code A machine-readable error code
 * @property message A human-readable error message
 * @property details Additional error details or context
 * @property field The field that caused the error (for validation errors)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
 data class ErrorDetail(
    val code: String,
    val message: String,
    val details: Any? = null,
    val field: String? = null
)

/**
 * Wrapper for paginated responses.
 * Extends ApiResponse with pagination metadata.
 *
 * @param T The type of items in the paginated list
 * @property data The paginated data
 * @property pagination Pagination metadata
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaginatedResponse<T>(
    val success: Boolean = true,
    val data: List<T>,
    val pagination: PaginationMetadata,
    val timestamp: Instant = Instant.now(),
    val message: String? = null
) {
    companion object {
        /**
         * Creates a paginated response from a list of items and pagination info.
         */
        fun <T> of(
            items: List<T>,
            page: Int,
            pageSize: Int,
            totalCount: Int,
            hasMore: Boolean
        ): PaginatedResponse<T> {
            return PaginatedResponse(
                data = items,
                pagination = PaginationMetadata(
                    page = page,
                    pageSize = pageSize,
                    totalCount = totalCount,
                    totalPages = if (pageSize > 0) (totalCount + pageSize - 1) / pageSize else 0,
                    hasMore = hasMore
                )
            )
        }
    }
}

/**
 * Pagination metadata for paginated responses.
 *
 * @property page Current page number (1-indexed)
 * @property pageSize Number of items per page
 * @property totalCount Total number of items available
 * @property totalPages Total number of pages
 * @property hasMore Whether there are more pages available
 */
data class PaginationMetadata(
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int,
    val hasMore: Boolean
)

/**
 * Wrapper for validation error responses.
 * Contains multiple field-level validation errors.
 *
 * @property success Always false for validation errors
 * @property error General error information
 * @property validationErrors List of field-specific validation errors
 * @property timestamp The time when the validation error occurred
 * @property path The request path that caused the error
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidationErrorResponse(
    val success: Boolean = false,
    val error: ErrorDetail,
    val validationErrors: List<FieldError>,
    val timestamp: Instant = Instant.now(),
    val path: String? = null
) {
    companion object {
        /**
         * Creates a validation error response with field errors.
         */
        fun of(
            message: String = "Validation failed",
            fieldErrors: List<FieldError>,
            path: String? = null
        ): ValidationErrorResponse {
            return ValidationErrorResponse(
                error = ErrorDetail(
                    code = "VALIDATION_ERROR",
                    message = message
                ),
                validationErrors = fieldErrors,
                path = path
            )
        }
    }
}

/**
 * Represents a validation error for a specific field.
 *
 * @property field The name of the field that failed validation
 * @property message The validation error message
 * @property rejectedValue The value that was rejected (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FieldError(
    val field: String,
    val message: String,
    val rejectedValue: Any? = null
)