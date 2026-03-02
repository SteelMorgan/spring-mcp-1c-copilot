package ru.alkoleft.copilot.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.async.AsyncRequestNotUsableException

private val exceptionLogger = KotlinLogging.logger {}

@ControllerAdvice
class McpExceptionHandler {
    @ExceptionHandler(AsyncRequestNotUsableException::class)
    fun handleAsyncRequestNotUsableException(ex: AsyncRequestNotUsableException): Unit {
        // Expected when SSE client disconnects during async write.
        exceptionLogger.debug { "Ignoring async request exception after SSE disconnect: ${ex.message}" }
    }
}
