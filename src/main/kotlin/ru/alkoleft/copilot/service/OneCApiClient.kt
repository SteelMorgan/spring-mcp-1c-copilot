package ru.alkoleft.copilot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.Duration
import com.fasterxml.jackson.databind.ObjectMapper

private val logger = KotlinLogging.logger {}

@Service
class OneCApiClient(
    @Value("\${onec.ai.token:}") private val token: String,
    @Value("\${onec.ai.base-url:https://code.1c.ai}") private val baseUrl: String,
    @Value("\${onec.ai.timeout:30}") private val timeout: Long
) {
    
    private val webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Content-Type", "application/json")
        .defaultHeader("Authorization", token)
        .build()
    private val objectMapper: ObjectMapper = ObjectMapper()
    
    private data class HttpResponse(val statusCode: Int, val body: String)
    private var currentSessionId: String? = null
    
    fun askQuestion(question: String, createNewSession: Boolean = false): String {
        try {
            val sessionId = if (createNewSession || currentSessionId == null) {
                createNewSession()
            } else {
                currentSessionId!!
            }
            
            val request = mapOf(
                "role" to "user",
                "content" to mapOf(
                    "content" to mapOf("instruction" to question)
                ),
                "parent_uuid" to null
            )
            val response: HttpResponse = executePost(
                path = "/chat_api/v1/conversations/$sessionId/messages",
                requestBody = request,
                accept = "text/event-stream"
            )
            if (response.statusCode != 200) {
                logger.error { "1C API error: status=${response.statusCode}, body=${response.body.take(2000)}" }
                return "Ошибка: HTTP ${response.statusCode} от 1С:Напарник"
            }
            val parsed: String = parseSseResponse(response.body)
            if (parsed.startsWith("Ошибка:")) {
                logger.error { "1C API SSE parse error: body=${response.body.take(2000)}" }
            }
            return parsed
            
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при обращении к 1С:Напарник API" }
            return "Ошибка: ${e.message}"
        }
    }
    
    private fun createNewSession(): String {
        try {
            val request = mapOf(
                "skill_name" to "custom",
                "is_chat" to true
            )
            val response: HttpResponse = executePost(
                path = "/chat_api/v1/conversations/",
                requestBody = request,
                accept = "application/json"
            )
            if (response.statusCode != 200) {
                logger.error { "1C API session init error: status=${response.statusCode}, body=${response.body.take(2000)}" }
                throw RuntimeException("Ошибка создания сессии: HTTP ${response.statusCode}")
            }
            val responseMap: Map<*, *> = objectMapper.readValue(response.body, Map::class.java)
            val sessionId: String? = responseMap["uuid"] as? String
            if (sessionId != null) {
                currentSessionId = sessionId
                return sessionId
            } else {
                throw RuntimeException("Не удалось создать сессию")
            }
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при создании сессии" }
            throw e
        }
    }
    
    private fun parseSseResponse(sseResponse: String): String {
        try {
            val lines = sseResponse.split("\n")
            var answerText = ""       // финальный ответ из content_delta.content
            var reasoningText = ""    // размышления из content_delta.reasoning_content
            for (line in lines) {
                if (!line.startsWith("data: ")) {
                    continue
                }
                try {
                    val dataStr = line.substring(6)
                    val data = objectMapper.readValue(dataStr, Map::class.java)
                    val contentDelta = data["content_delta"] as? Map<*, *>
                    if (contentDelta != null) {
                        // финальный текст ответа
                        val deltaContent = contentDelta["content"] as? String
                        if (!deltaContent.isNullOrEmpty()) {
                            answerText += deltaContent
                        }
                        // текст размышлений модели
                        val deltaReasoning = contentDelta["reasoning_content"] as? String
                        if (!deltaReasoning.isNullOrEmpty()) {
                            reasoningText += deltaReasoning
                        }
                    }
                    // финальный content из завершённого сообщения (приоритет над дельтами)
                    val content = data["content"] as? Map<*, *>
                    val finalContent = content?.get("content") as? String
                    if (!finalContent.isNullOrEmpty() && finalContent.length > answerText.length) {
                        answerText = finalContent
                    }
                    val finalReasoning = content?.get("reasoning_content") as? String
                    if (!finalReasoning.isNullOrEmpty() && finalReasoning.length > reasoningText.length) {
                        reasoningText = finalReasoning
                    }
                } catch (e: Exception) {
                    logger.warn { "Ошибка парсинга SSE chunk: $e" }
                }
            }
            return when {
                answerText.isNotEmpty() && reasoningText.isNotEmpty() ->
                    "<thinking>\n$reasoningText\n</thinking>\n\n$answerText"
                answerText.isNotEmpty() -> answerText
                reasoningText.isNotEmpty() -> reasoningText
                else -> "Ошибка: не получен ответ от 1С:Напарник"
            }
        } catch (e: Exception) {
            logger.error(e) { "Ошибка парсинга SSE ответа" }
            return "Ошибка парсинга ответа: ${e.message}"
        }
    }

    private fun executePost(path: String, requestBody: Any, accept: String? = null): HttpResponse {
        val requestSpec = if (accept != null) {
            webClient.post().uri(path).header("Accept", accept)
        } else {
            webClient.post().uri(path)
        }
        val response: HttpResponse? = requestSpec
            .bodyValue(requestBody)
            .exchangeToMono { clientResponse ->
                clientResponse.bodyToMono<String>().defaultIfEmpty("").map { body ->
                    HttpResponse(clientResponse.statusCode().value(), body)
                }
            }
            .timeout(Duration.ofSeconds(timeout))
            .block()
        if (response == null) {
            return HttpResponse(0, "")
        }
        return response
    }
}
