package fr.batgard.prewave_assignment.exception

import fr.batgard.prewave_assignment.edge.repository.exception.EdgeAlreadyExistsException
import fr.batgard.prewave_assignment.edge.repository.exception.EdgeNotFoundException
import fr.batgard.prewave_assignment.edge.repository.exception.EmptyEdgeDatabaseException
import fr.batgard.prewave_assignment.edge.repository.exception.PageIndexOutOfBoundsException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus

@ControllerAdvice
class GlobalExceptionHandler {
    data class ApiError(
        val status: Int? = null,
        val message: String? = null,
    )

    @ExceptionHandler(Exception::class)
    fun defaultErrorHandler(e: Exception): ResponseEntity<ApiError> {
        val httpStatus = HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity(
            ApiError(httpStatus.value(), "Exception : ${e::class.simpleName} \n ${e.message}"),
            httpStatus
        )
    }

    @ExceptionHandler(EdgeAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleEdgeAlreadyExistsException(e: EdgeAlreadyExistsException): ResponseEntity<ApiError> {
        val httpStatus = HttpStatus.CONFLICT
        return ResponseEntity(ApiError(httpStatus.value(), e.message), httpStatus)
    }

    @ExceptionHandler(EdgeNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleEdgeNotFoundException(e: EdgeNotFoundException): ResponseEntity<ApiError> {
        val httpStatus = HttpStatus.NOT_FOUND
        return ResponseEntity(ApiError(httpStatus.value(), e.message), httpStatus)
    }

    @ExceptionHandler(EmptyEdgeDatabaseException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleEmptyDatabaseException(e: EmptyEdgeDatabaseException): ResponseEntity<ApiError> {
        val httpStatus = HttpStatus.NOT_FOUND
        return ResponseEntity(ApiError(httpStatus.value(), e.message), httpStatus)
    }

    @ExceptionHandler(PageIndexOutOfBoundsException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleInvalidPageException(e: PageIndexOutOfBoundsException): ResponseEntity<ApiError> {
        val httpStatus = HttpStatus.NOT_FOUND
        return ResponseEntity(ApiError(httpStatus.value(), e.message), httpStatus)
    }
}
