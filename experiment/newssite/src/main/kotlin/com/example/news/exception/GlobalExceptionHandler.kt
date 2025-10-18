package com.example.news.exception

import com.example.news.dto.ErrorDetail
import com.example.news.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.validation.FieldError

@RestControllerAdvice
class GlobalExceptionHandler {
    
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorDetail = ErrorDetail(
            code = "INVALID_ARGUMENT",
            message = ex.message ?: "Invalid argument provided",
            details = mapOf("path" to request.getDescription(false))
        )
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = errorDetail))
    }
    
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(
        ex: IllegalStateException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorDetail = ErrorDetail(
            code = "INVALID_STATE",
            message = ex.message ?: "Invalid state encountered",
            details = mapOf("path" to request.getDescription(false))
        )
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(error = errorDetail))
    }
    
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(
        ex: NoSuchElementException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorDetail = ErrorDetail(
            code = "NOT_FOUND",
            message = ex.message ?: "Requested resource not found",
            details = mapOf("path" to request.getDescription(false))
        )
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(error = errorDetail))
    }
   @ExceptionHandler(MethodArgumentNotValidException::class)
   fun handleValidationException(
       ex: MethodArgumentNotValidException,
       request: WebRequest
   ): ResponseEntity<ErrorResponse> {
       val errors = ex.bindingResult.allErrors.associate { error ->
           val fieldName = (error as? FieldError)?.field ?: "unknown"
           fieldName to (error.defaultMessage ?: "Validation failed")
       }
       val errorDetail = ErrorDetail(
           code = "VALIDATION_ERROR",
           message = "Request validation failed",
           details = mapOf(
               "path" to request.getDescription(false),
               "validationErrors" to errors
           )
       )
       return ResponseEntity
           .status(HttpStatus.BAD_REQUEST)
           .body(ErrorResponse(error = errorDetail))
   }
    
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorDetail = ErrorDetail(
            code = "INTERNAL_ERROR",
            message = "An unexpected error occurred: ${ex.message}",
            details = mapOf(
                "path" to request.getDescription(false),
                "exceptionType" to (ex::class.simpleName ?: "Unknown")
            )
        )
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(error = errorDetail))
    }
}