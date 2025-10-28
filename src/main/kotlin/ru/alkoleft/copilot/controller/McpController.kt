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
import reactor.core.publisher.Flux
import ru.alkoleft.copilot.service.OneCCopilotService
import java.time.Duration

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/mcp")
@Tag(name = "MCP Server", description = "Model Context Protocol сервер для 1С:Напарник")
class McpController(
    private val oneCCopilotService: OneCCopilotService
) {
    
    @GetMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(
        summary = "SSE Stream",
        description = "Server-Sent Events поток для MCP клиентов"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "SSE поток успешно установлен",
                content = [Content(mediaType = "text/event-stream")]
            )
        ]
    )
    fun sseStream(): Flux<String> {
        logger.info { "SSE stream started" }
        
        return Flux.interval(Duration.ofSeconds(30))
            .map { "data: {\"type\":\"heartbeat\",\"timestamp\":${System.currentTimeMillis()}}\n\n" }
            .doOnNext { logger.debug { "Sending heartbeat" } }
    }
    
    @PostMapping(consumes = ["application/json;charset=UTF-8"], produces = ["application/json;charset=UTF-8"])
    @Operation(
        summary = "MCP Request Handler",
        description = "Основной обработчик MCP (Model Context Protocol) запросов"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "MCP запрос успешно обработан",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = Map::class),
                    examples = [
                        ExampleObject(
                            name = "Initialize Response",
                            value = """
                            {
                                "jsonrpc": "2.0",
                                "id": "1",
                                "result": {
                                    "protocolVersion": "2025-06-18",
                                    "capabilities": {
                                        "tools": {"listChanged": true},
                                        "prompts": {},
                                        "resources": {},
                                        "logging": {}
                                    },
                                    "serverInfo": {
                                        "name": "1C Copilot MCP Server",
                                        "version": "1.0.0"
                                    }
                                }
                            }
                            """
                        ),
                        ExampleObject(
                            name = "Tools List Response",
                            value = """
                            {
                                "jsonrpc": "2.0",
                                "id": "2",
                                "result": {
                                    "tools": [
                                        {
                                            "name": "ask_1c_ai",
                                            "description": "Ask question to 1C:Assistant AI",
                                            "inputSchema": {
                                                "type": "object",
                                                "properties": {
                                                    "question": {
                                                        "type": "string",
                                                        "description": "Question for 1C:Assistant AI"
                                                    }
                                                },
                                                "required": ["question"]
                                            }
                                        }
                                    ]
                                }
                            }
                            """
                        )
                    ]
                )]
            )
        ]
    )
    fun handleMcpRequest(
        @Parameter(description = "MCP JSON-RPC запрос")
        @RequestBody request: Map<String, Any>
    ): Map<String, Any> {
        logger.info { "MCP request received: $request" }
        
        return when (request["method"]) {
            "initialize" -> handleInitialize(request)
            "tools/list" -> handleToolsList(request)
            "tools/call" -> handleToolsCall(request)
            "prompts/list" -> handlePromptsList(request)
            "resources/list" -> handleResourcesList(request)
            "notifications/initialized" -> handleNotificationInitialized(request)
            else -> {
                logger.warn { "Unknown MCP method: ${request["method"]}" }
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to (request["id"] ?: ""),
                    "error" to mapOf(
                        "code" to -32601,
                        "message" to "Method not found: ${request["method"]}"
                    )
                )
            }
        }
    }
    
    private fun handleInitialize(request: Map<String, Any>): Map<String, Any> {
        return mapOf(
            "jsonrpc" to "2.0",
            "id" to (request["id"] ?: ""),
            "result" to mapOf(
                "protocolVersion" to "2025-06-18",
                "capabilities" to mapOf(
                    "tools" to mapOf("listChanged" to true),
                    "prompts" to emptyMap<String, Any>(),
                    "resources" to emptyMap<String, Any>(),
                    "logging" to emptyMap<String, Any>()
                ),
                "serverInfo" to mapOf(
                    "name" to "1C Copilot MCP Server",
                    "version" to "1.0.0"
                )
            )
        )
    }
    
    private fun handleToolsList(request: Map<String, Any>): Map<String, Any> {
        val tools = listOf(
            mapOf(
                "name" to "ask_1c_ai",
                "description" to "Ask question to 1C:Assistant AI",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "question" to mapOf(
                            "type" to "string",
                            "description" to "Question for 1C:Assistant AI"
                        ),
                        "programming_language" to mapOf(
                            "type" to "string",
                            "description" to "Programming language (optional)"
                        ),
                        "create_new_session" to mapOf(
                            "type" to "boolean",
                            "description" to "Create new session",
                            "default" to false
                        )
                    ),
                    "required" to listOf("question")
                )
            ),
            mapOf(
                "name" to "explain_1c_syntax",
                "description" to "Explain 1C syntax",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "syntax_element" to mapOf(
                            "type" to "string",
                            "description" to "Syntax element to explain"
                        ),
                        "context" to mapOf(
                            "type" to "string",
                            "description" to "Usage context (optional)"
                        )
                    ),
                    "required" to listOf("syntax_element")
                )
            ),
            mapOf(
                "name" to "check_1c_code",
                "description" to "Check 1C code for errors",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "code" to mapOf(
                            "type" to "string",
                            "description" to "1C code to check"
                        ),
                        "check_type" to mapOf(
                            "type" to "string",
                            "description" to "Check type: syntax, logic, performance",
                            "default" to "syntax"
                        )
                    ),
                    "required" to listOf("code")
                )
            )
        )
        
        return mapOf(
            "jsonrpc" to "2.0",
            "id" to (request["id"] ?: ""),
            "result" to mapOf("tools" to tools)
        )
    }
    
    private fun handleToolsCall(request: Map<String, Any>): Map<String, Any> {
        val params = request["params"] as? Map<String, Any> ?: return mapOf("error" to "No params")
        val toolName = params["name"] as? String ?: return mapOf("error" to "No tool name")
        val arguments = params["arguments"] as? Map<String, Any> ?: emptyMap()
        
        return try {
            val result = when (toolName) {
                "ask_1c_ai" -> {
                    val question = arguments["question"] as? String ?: return mapOf("error" to "No question")
                    val programmingLanguage = arguments["programming_language"] as? String
                    val createNewSession = arguments["create_new_session"] as? Boolean ?: false
                    oneCCopilotService.ask1CAI(question, programmingLanguage, createNewSession)
                }
                "explain_1c_syntax" -> {
                    val syntaxElement = arguments["syntax_element"] as? String ?: return mapOf("error" to "No syntax element")
                    val context = arguments["context"] as? String
                    oneCCopilotService.explain1CSyntax(syntaxElement, context)
                }
                "check_1c_code" -> {
                    val code = arguments["code"] as? String ?: return mapOf("error" to "No code")
                    val checkType = arguments["check_type"] as? String ?: "syntax"
                    oneCCopilotService.check1CCode(code, checkType)
                }
                else -> "Unknown tool: $toolName"
            }
            
            mapOf(
                "jsonrpc" to "2.0",
                "id" to (request["id"] ?: ""),
                "result" to mapOf("content" to listOf(mapOf("type" to "text", "text" to result)))
            )
        } catch (e: Exception) {
            logger.error(e) { "Error handling tool call: $toolName" }
            mapOf(
                "jsonrpc" to "2.0",
                "id" to (request["id"] ?: ""),
                "error" to mapOf("message" to (e.message ?: "Unknown error"))
            )
        }
    }
    
    private fun handlePromptsList(request: Map<String, Any>): Map<String, Any> {
        logger.debug { "Handling prompts/list request" }
        return mapOf(
            "jsonrpc" to "2.0",
            "id" to (request["id"] ?: ""),
            "result" to mapOf("prompts" to emptyList<Any>())
        )
    }
    
    private fun handleResourcesList(request: Map<String, Any>): Map<String, Any> {
        logger.debug { "Handling resources/list request" }
        return mapOf(
            "jsonrpc" to "2.0",
            "id" to (request["id"] ?: ""),
            "result" to mapOf("resources" to emptyList<Any>())
        )
    }
    
    private fun handleNotificationInitialized(request: Map<String, Any>): Map<String, Any> {
        logger.info { "Client initialized notification received" }
        // Уведомления не требуют ответа согласно MCP протоколу
        // Но возвращаем пустой объект для совместимости
        return mapOf(
            "jsonrpc" to "2.0"
        )
    }
}
