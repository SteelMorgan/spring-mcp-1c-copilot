package ru.alkoleft.copilot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
class OneCApiClient(
    @Value("\${onec.ai.token:}") private val token: String,
    @Value("\${onec.ai.base-url:https://code.1c.ai}") private val baseUrl: String,
    @Value("\${onec.ai.timeout:30}") private val timeout: Long
) {
    
    private val webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Accept", "*/*")
        .defaultHeader("Accept-Charset", "utf-8")
        .defaultHeader("Accept-Encoding", "gzip, deflate, br")
        .defaultHeader("Accept-Language", "ru-ru,en-us;q=0.8,en;q=0.7")
        .defaultHeader("Content-Type", "application/json; charset=utf-8")
        .defaultHeader("Origin", baseUrl)
        .defaultHeader("Referer", "$baseUrl/chat/")
        .defaultHeader("User-Agent", "Mozilla/5.0")
        .defaultHeader("Authorization", token)
        .defaultHeader("X-API-Key", token)
        .defaultHeader("X-Auth-Token", token)
        .build()
    
    private var currentSessionId: String? = null
    
    fun askQuestion(question: String, createNewSession: Boolean = false): String {
        try {
            val sessionId = if (createNewSession || currentSessionId == null) {
                createNewSession()
            } else {
                currentSessionId!!
            }
            
            val request = mapOf(
                "parent_uuid" to null,
                "tool_content" to mapOf("instruction" to question)
            )
            
            val response = webClient.post()
                .uri("/chat_api/v1/conversations/$sessionId/messages")
                .header("Accept", "text/event-stream")
                .bodyValue(request)
                .retrieve()
                .bodyToMono<String>()
                .timeout(Duration.ofSeconds(timeout))
                .block()
            
            return parseSseResponse(response ?: "")
            
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при обращении к 1С:Напарник API" }
            return "Ошибка: ${e.message}"
        }
    }
    
    private fun createNewSession(): String {
        try {
            val request = mapOf(
                "tool_name" to "custom",
                "ui_language" to "russian",
                "programming_language" to "",
                "script_language" to ""
            )
            
            val response = webClient.post()
                .uri("/chat_api/v1/conversations/")
                .header("Session-Id", "")
                .bodyValue(request)
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .timeout(Duration.ofSeconds(timeout))
                .block()
            
            val sessionId = response?.get("uuid") as? String
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
            var fullText = ""
            
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    try {
                        val dataStr = line.substring(6)
                        val data = com.fasterxml.jackson.databind.ObjectMapper().readValue(dataStr, Map::class.java)
                        
                        val role = data["role"] as? String
                        val content = data["content"] as? Map<String, Any>
                        val finished = data["finished"] as? Boolean ?: false
                        
                        if (role == "assistant" && content != null && content.containsKey("text")) {
                            val text = content["text"] as? String
                            if (text != null && text.isNotEmpty()) {
                                fullText = text
                            }
                            if (finished) {
                                break
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn { "Ошибка парсинга SSE chunk: $e" }
                        continue
                    }
                }
            }
            
            return fullText.ifEmpty { "Ошибка: не получен ответ от 1С:Напарник" }
        } catch (e: Exception) {
            logger.error(e) { "Ошибка парсинга SSE ответа" }
            return "Ошибка парсинга ответа: ${e.message}"
        }
    }
}
