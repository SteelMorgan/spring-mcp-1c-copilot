package ru.alkoleft.copilot.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import ru.alkoleft.copilot.service.OneCCopilotService

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api")
@Tag(name = "REST API", description = "REST API endpoints для прямого взаимодействия с 1С:Напарник")
class RestApiController(
    private val oneCCopilotService: OneCCopilotService
) {

    @PostMapping("/ask-ai", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Задать вопрос ИИ",
        description = "Отправить вопрос к 1С:Напарник AI и получить ответ"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Ответ получен успешно",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = Map::class),
                    examples = [
                        ExampleObject(
                            name = "Success Response",
                            value = """
                            {
                                "result": "🤖 **Ответ от 1С:Напарник:**\n\nДля создания справочника в 1С нужно...",
                                "error": null
                            }
                            """
                        )
                    ]
                )]
            )
        ]
    )
    fun askAI(
        @Parameter(description = "Вопрос для ИИ")
        @RequestParam question: String,
        @Parameter(description = "Язык программирования (опционально)")
        @RequestParam(required = false) programmingLanguage: String?,
        @Parameter(description = "Создать новую сессию")
        @RequestParam(defaultValue = "false") createNewSession: Boolean
    ): Map<String, Any?> {
        logger.info { "REST API ask-ai called with question: $question" }
        
        return try {
            val result = oneCCopilotService.ask1CAI(question, programmingLanguage, createNewSession)
            mapOf("result" to result, "error" to null as Any?)
        } catch (e: Exception) {
            logger.error(e) { "Error in REST API ask-ai" }
            mapOf("result" to "", "error" to (e.message ?: "Unknown error"))
        }
    }

    @PostMapping("/explain-syntax", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Объяснить синтаксис 1С",
        description = "Получить объяснение синтаксиса элемента 1С"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Объяснение получено успешно",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = Map::class),
                    examples = [
                        ExampleObject(
                            name = "Success Response",
                            value = """
                            {
                                "result": "📚 **Объяснение синтаксиса 'Справочник':**\n\nСправочник в 1С - это...",
                                "error": null
                            }
                            """
                        )
                    ]
                )]
            )
        ]
    )
    fun explainSyntax(
        @Parameter(description = "Элемент синтаксиса для объяснения")
        @RequestParam syntaxElement: String,
        @Parameter(description = "Контекст использования (опционально)")
        @RequestParam(required = false) context: String?
    ): Map<String, Any?> {
        logger.info { "REST API explain-syntax called with element: $syntaxElement" }
        
        return try {
            val result = oneCCopilotService.explain1CSyntax(syntaxElement, context)
            mapOf("result" to result, "error" to null as Any?)
        } catch (e: Exception) {
            logger.error(e) { "Error in REST API explain-syntax" }
            mapOf("result" to "", "error" to (e.message ?: "Unknown error"))
        }
    }

    @PostMapping("/check-code", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Проверить код 1С",
        description = "Проверить код 1С на ошибки и получить рекомендации"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Проверка выполнена успешно",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = Map::class),
                    examples = [
                        ExampleObject(
                            name = "Success Response",
                            value = """
                            {
                                "result": "🔍 **Проверка кода на синтаксические ошибки:**\n\nКод выглядит корректно...",
                                "error": null
                            }
                            """
                        )
                    ]
                )]
            )
        ]
    )
    fun checkCode(
        @Parameter(description = "Код 1С для проверки")
        @RequestParam code: String,
        @Parameter(description = "Тип проверки: syntax, logic, performance")
        @RequestParam(defaultValue = "syntax") checkType: String
    ): Map<String, Any?> {
        logger.info { "REST API check-code called with checkType: $checkType" }
        
        return try {
            val result = oneCCopilotService.check1CCode(code, checkType)
            mapOf("result" to result, "error" to null as Any?)
        } catch (e: Exception) {
            logger.error(e) { "Error in REST API check-code" }
            mapOf("result" to "", "error" to (e.message ?: "Unknown error"))
        }
    }

    @GetMapping("/health", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Проверка состояния",
        description = "Проверить состояние сервера и доступность 1С:Напарник API"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Сервер работает нормально",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = Map::class),
                    examples = [
                        ExampleObject(
                            name = "Health Check Response",
                            value = """
                            {
                                "status": "ok",
                                "service": "1c-copilot-mcp-server",
                                "timestamp": "2025-01-07T20:50:00Z"
                            }
                            """
                        )
                    ]
                )]
            )
        ]
    )
    fun healthCheck(): Map<String, Any> {
        return mapOf(
            "status" to "ok",
            "service" to "1c-copilot-mcp-server",
            "timestamp" to java.time.Instant.now().toString()
        )
    }
}
