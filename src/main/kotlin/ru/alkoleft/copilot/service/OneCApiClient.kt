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
        .defaultHeader("Authorization", "Bearer $token")
        .defaultHeader("Content-Type", "application/json")
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
                "message" to question,
                "conversation_id" to sessionId
            )
            
            val response = webClient.post()
                .uri("/api/v1/chat")
                .bodyValue(request)
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .timeout(Duration.ofSeconds(timeout))
                .block()
            
            return response?.get("response") as? String ?: "Ошибка: не получен ответ от 1С:Напарник"
            
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при обращении к 1С:Напарник API" }
            return "Ошибка: ${e.message}"
        }
    }
    
    private fun createNewSession(): String {
        try {
            val response = webClient.post()
                .uri("/api/v1/conversations")
                .bodyValue(mapOf("name" to "MCP Session"))
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .timeout(Duration.ofSeconds(timeout))
                .block()
            
            val sessionId = response?.get("id") as? String
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
}
