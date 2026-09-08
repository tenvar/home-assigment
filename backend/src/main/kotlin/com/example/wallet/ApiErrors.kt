package com.example.wallet

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

class ApiException(val status: Int, val code: String, override val message: String) : RuntimeException(message)

@RestControllerAdvice
class ApiErrors {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun domain(error: ApiException): ResponseEntity<Any> = response(error.status, error.code, error.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun fields(error: MethodArgumentNotValidException): ResponseEntity<Any> = ResponseEntity.badRequest().body(
        mapOf(
            "code" to "VALIDATION_ERROR",
            "message" to "Invalid request fields",
            "fieldErrors" to error.bindingResult.fieldErrors.map {
                mapOf("field" to it.field, "code" to "INVALID_VALUE")
            }
        )
    )

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        ServletRequestBindingException::class,
        ConstraintViolationException::class,
        HandlerMethodValidationException::class
    )
    fun invalid(error: Exception): ResponseEntity<Any> = response(400, "VALIDATION_ERROR", "Invalid request")

    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception): ResponseEntity<Any> {
        logger.error("Request failed", error)
        return response(500, "INTERNAL_ERROR", "An unexpected error occurred")
    }

    private fun response(status: Int, code: String, message: String): ResponseEntity<Any> =
        ResponseEntity.status(status).body(mapOf("code" to code, "message" to message, "fieldErrors" to emptyList<Any>()))
}
