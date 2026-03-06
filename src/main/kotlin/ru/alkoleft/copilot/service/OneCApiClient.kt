package ru.alkoleft.copilot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.Duration
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

@Service
class OneCApiClient(
    @Value("\${onec.ai.token:}") private val token: String,
    @Value("\${onec.ai.token-file:}") private val tokenFile: String,
    @Value("\${onec.ai.base-url:https://code.1c.ai}") private val baseUrl: String,
    @Value("\${onec.ai.timeout:30}") private val timeout: Long,
    @Value("\${onec.ai.skill-name:raw}") private val skillName: String
) {
    private val authorizationHeader = resolveAuthorizationHeader(token, tokenFile)
    private val webClientBuilder = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Content-Type", "application/json")
    private val webClient = if (authorizationHeader.isNullOrBlank()) {
        webClientBuilder.build()
    } else {
        webClientBuilder.defaultHeader("Authorization", authorizationHeader).build()
    }
    private val objectMapper: ObjectMapper = ObjectMapper()
    
    private data class HttpResponse(val statusCode: Int, val body: String)
    private var currentSessionId: String? = null
    
    fun askQuestion(question: String, createNewSession: Boolean = false): String {
        return askQuestionInternal(question, createNewSession, allowRawFallback = true)
    }

    private fun askQuestionInternal(
        question: String,
        createNewSession: Boolean,
        allowRawFallback: Boolean
    ): String {
        try {
            val sessionId = if (createNewSession || currentSessionId == null) {
                createNewSession(skillName)
            } else {
                currentSessionId!!
            }
            
            val request = buildQuestionRequest(question)
            val response: HttpResponse = executePost(
                path = "/chat_api/v1/conversations/$sessionId/messages",
                requestBody = request,
                accept = "text/event-stream"
            )
            if (response.statusCode != 200) {
                logger.error { "1C API error: status=${response.statusCode}, body=${response.body}" }
                return "Ошибка: HTTP ${response.statusCode} от 1С:Напарник"
            }
            val parsed: String = parseSseResponse(response.body)
            if (parsed.startsWith("Ошибка:")) {
                logger.error { "1C API SSE parse error: body=${response.body}" }
                if (allowRawFallback && shouldRetryWithRawSkill(response.body)) {
                    logger.warn { "1C API returned tool calls without final answer, retrying with raw skill" }
                    val rawSessionId = createNewSession("raw", persistSession = false)
                    return askQuestionInExistingSession(question, rawSessionId, allowRawFallback = false)
                }
            }
            return parsed
            
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при обращении к 1С:Напарник API" }
            return "Ошибка: ${e.message}"
        }
    }

    private fun askQuestionInExistingSession(
        question: String,
        sessionId: String,
        allowRawFallback: Boolean
    ): String {
        val request = buildQuestionRequest(question)
        val response = executePost(
            path = "/chat_api/v1/conversations/$sessionId/messages",
            requestBody = request,
            accept = "text/event-stream"
        )
        if (response.statusCode != 200) {
            logger.error { "1C API error: status=${response.statusCode}, body=${response.body}" }
            return "Ошибка: HTTP ${response.statusCode} от 1С:Напарник"
        }

        val parsed = parseSseResponse(response.body)
        if (parsed.startsWith("Ошибка:") && allowRawFallback && shouldRetryWithRawSkill(response.body)) {
            logger.warn { "1C API returned tool calls without final answer, retrying with raw skill" }
            val rawSessionId = createNewSession("raw", persistSession = false)
            return askQuestionInExistingSession(question, rawSessionId, allowRawFallback = false)
        }

        return parsed
    }
    
    private fun buildResponseInstruction(question: String): String {
        return """
            $question

            Верни только финальный ответ для пользователя.
            Не добавляй рассуждения, планы, скрытые мысли, теги <thinking> и служебные пояснения.
            Если не можешь дать ответ — верни краткое сообщение об ошибке.
        """.trimIndent()
    }

    private fun buildQuestionRequest(question: String): Map<String, Any?> {
        val responseInstruction = buildResponseInstruction(question)
        return mapOf(
            "role" to "user",
            "content" to mapOf(
                "content" to mapOf("instruction" to responseInstruction)
            ),
            "parent_uuid" to null
        )
    }

    private fun createNewSession(skillName: String, persistSession: Boolean = true): String {
        try {
            val request = mapOf(
                "skill_name" to skillName,
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
                if (persistSession) {
                    currentSessionId = sessionId
                }
                return sessionId
            } else {
                throw RuntimeException("Не удалось создать сессию")
            }
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при создании сессии" }
            throw e
        }
    }

    private fun shouldRetryWithRawSkill(responseBody: String): Boolean {
        return responseBody.contains("\"tool_calls\":[") || responseBody.contains("\"tool_calls\": [")
    }
    
    private fun parseSseResponse(sseResponse: String): String {
        try {
            var answerText = ""
            var reasoningText = ""

            for (dataStr in extractSseDataEvents(sseResponse)) {
                if (dataStr == "[DONE]") {
                    break
                }

                try {
                    val data = objectMapper.readValue(dataStr, Map::class.java)
                    var shouldStop = false
                    val eventType = (data["type"] as? String)?.lowercase()

                    val contentDelta = data["content_delta"] as? Map<*, *>
                    if (contentDelta != null) {
                        val deltaContent = sanitizeModelOutput(extractAnswerText(contentDelta))
                        if (deltaContent.isNotEmpty()) {
                            answerText += deltaContent
                        }
                        val deltaReasoning = extractReasoningText(contentDelta, eventType)
                        if (!deltaReasoning.isNullOrEmpty()) {
                            reasoningText += deltaReasoning
                        }
                    }

                    val content = data["content"] as? Map<*, *>
                    val finalContent = sanitizeModelOutput(extractAnswerText(content))
                    if (finalContent.isNotEmpty() && finalContent.length > answerText.length) {
                        answerText = finalContent
                    }
                    val finalReasoning = extractReasoningText(content, eventType)
                    if (!finalReasoning.isNullOrEmpty() && finalReasoning.length > reasoningText.length) {
                        reasoningText = finalReasoning
                    }

                    val choices = data["choices"] as? List<*>
                    if (choices != null) {
                        for (choice in choices) {
                            val choiceMap = choice as? Map<*, *> ?: continue

                            val finishReason = choiceMap["finish_reason"] as? String
                            if (!finishReason.isNullOrEmpty()) {
                                shouldStop = true
                            }

                            val delta = choiceMap["delta"] as? Map<*, *>
                            if (delta != null) {
                                val deltaContent = sanitizeModelOutput(extractAnswerText(delta))
                                if (deltaContent.isNotEmpty()) {
                                    answerText += deltaContent
                                }
                                val deltaReasoning = extractReasoningText(delta, eventType)
                                if (!deltaReasoning.isNullOrEmpty()) {
                                    reasoningText += deltaReasoning
                                }
                            }

                            val message = choiceMap["message"] as? Map<*, *>
                            val messageContent = sanitizeModelOutput(extractAnswerText(message))
                            if (messageContent.isNotEmpty() && messageContent.length > answerText.length) {
                                answerText = messageContent
                            }
                            val messageReasoning = extractReasoningText(message, eventType)
                            if (!messageReasoning.isNullOrEmpty() && messageReasoning.length > reasoningText.length) {
                                reasoningText = messageReasoning
                            }
                        }
                    }

                    if (shouldUseGenericEventExtraction(data)) {
                        val eventAnswer = sanitizeModelOutput(extractAnswerText(data, eventType))
                        if (eventAnswer.isNotEmpty()) {
                            if (shouldReplaceAnswer(data, eventType, answerText, eventAnswer)) {
                                answerText = eventAnswer
                            } else {
                                answerText += eventAnswer
                            }
                        }

                        val eventReasoning = extractReasoningText(data, eventType)
                        if (!eventReasoning.isNullOrEmpty()) {
                            if (shouldReplaceReasoning(data, eventType, reasoningText, eventReasoning)) {
                                reasoningText = eventReasoning
                            } else {
                                reasoningText += eventReasoning
                            }
                        }
                    }

                    val role = data["role"] as? String
                    val finished = when (val finishedValue = data["finished"]) {
                        is Boolean -> finishedValue
                        is String -> finishedValue.equals("true", ignoreCase = true)
                        else -> false
                    }
                    if (role == "assistant" && finished) {
                        shouldStop = true
                    }

                    if (shouldStop) {
                        break
                    }
                } catch (e: Exception) {
                    logger.warn { "Ошибка парсинга SSE chunk: $e" }
                }
            }

            val normalizedAnswer = answerText.trim()
            return when {
                normalizedAnswer.isNotEmpty() -> normalizedAnswer
                reasoningText.isNotEmpty() -> {
                    logger.warn { "От 1С:Напарник получены только рассуждения без итогового ответа" }
                    "Ошибка: получены только рассуждения модели без итогового ответа"
                }
                else -> "Ошибка: не получен ответ от 1С:Напарник"
            }
        } catch (e: Exception) {
            logger.error(e) { "Ошибка парсинга SSE ответа" }
            return "Ошибка парсинга ответа: ${e.message}"
        }
    }

    private fun extractSseDataEvents(sseResponse: String): List<String> {
        val events = mutableListOf<String>()
        val currentDataLines = mutableListOf<String>()
        var hasExplicitEventHeader = false

        fun flushCurrentEvent() {
            if (currentDataLines.isNotEmpty()) {
                events += currentDataLines.joinToString("\n")
                currentDataLines.clear()
            }
            hasExplicitEventHeader = false
        }

        for (rawLine in sseResponse.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) {
                flushCurrentEvent()
                continue
            }
            if (line.startsWith(":")) {
                continue
            }
            if (line.startsWith("event:")) {
                flushCurrentEvent()
                hasExplicitEventHeader = true
                continue
            }
            if (line.startsWith("data:")) {
                if (currentDataLines.isNotEmpty() && !hasExplicitEventHeader) {
                    flushCurrentEvent()
                }
                currentDataLines += line.removePrefix("data:").trimStart()
            }
        }

        flushCurrentEvent()
        return events
    }

    private fun extractAnswerText(payload: Any?, eventType: String? = null): String {
        if (payload == null || eventType?.contains("reason") == true) {
            return ""
        }

        return when (payload) {
            is String -> payload
            is List<*> -> payload.joinToString("") { extractAnswerText(it, eventType) }
            is Map<*, *> -> {
                val typedText = when ((payload["type"] as? String)?.lowercase()) {
                    "output_text", "text" -> flattenText(payload["text"])
                    else -> ""
                }
                if (typedText.isNotEmpty()) {
                    return typedText
                }

                val directCandidates = listOf(
                    flattenText(payload["output_text"]),
                    flattenText(payload["text"]),
                    flattenText(payload["content"]),
                    flattenText(payload["delta"])
                ).firstOrNull { it.isNotEmpty() }.orEmpty()
                if (directCandidates.isNotEmpty()) {
                    return directCandidates
                }

                flattenText(payload["message"]) +
                    flattenText(payload["response"]) +
                    flattenText(payload["output"])
            }
            else -> ""
        }
    }

    private fun extractReasoningText(payload: Any?, eventType: String? = null): String {
        if (payload == null) {
            return ""
        }

        return when (payload) {
            is String -> if (eventType?.contains("reason") == true) payload else ""
            is List<*> -> payload.joinToString("") { extractReasoningText(it, eventType) }
            is Map<*, *> -> {
                val typedText = when ((payload["type"] as? String)?.lowercase()) {
                    "reasoning", "reasoning_text" -> flattenText(payload["text"])
                    else -> ""
                }
                if (typedText.isNotEmpty()) {
                    return typedText
                }

                val directCandidates = listOf(
                    flattenText(payload["reasoning_content"]),
                    flattenText(payload["reasoning"]),
                    if (eventType?.contains("reason") == true) flattenText(payload["delta"]) else "",
                    if (eventType?.contains("reason") == true) flattenText(payload["text"]) else ""
                ).firstOrNull { it.isNotEmpty() }.orEmpty()
                if (directCandidates.isNotEmpty()) {
                    return directCandidates
                }

                flattenText(payload["message"]) +
                    flattenText(payload["response"]) +
                    flattenText(payload["output"])
            }
            else -> ""
        }
    }

    private fun flattenText(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> value
            is List<*> -> value.joinToString("") { flattenText(it) }
            is Map<*, *> -> {
                val preferredKeys = listOf("text", "content", "output_text", "delta")
                val preferred = preferredKeys
                    .mapNotNull { key -> (value[key] ?: return@mapNotNull null).let(::flattenText).takeIf { it.isNotEmpty() } }
                    .joinToString("")
                if (preferred.isNotEmpty()) {
                    preferred
                } else {
                    value.values.joinToString("") { flattenText(it) }
                }
            }
            else -> value.toString()
        }
    }

    private fun shouldUseGenericEventExtraction(data: Map<*, *>): Boolean {
        return data["content_delta"] == null &&
            data["content"] == null &&
            data["choices"] == null
    }

    private fun shouldReplaceAnswer(
        data: Map<*, *>,
        eventType: String?,
        currentAnswer: String,
        candidateAnswer: String
    ): Boolean {
        if (currentAnswer.isEmpty()) {
            return false
        }

        if (eventType == null) {
            return candidateAnswer.length > currentAnswer.length
        }

        if ("message" in data && eventType.contains("completed")) {
            return true
        }

        return eventType.contains("completed") ||
            eventType.contains("done") ||
            eventType.contains("final") ||
            candidateAnswer.length > currentAnswer.length
    }

    private fun shouldReplaceReasoning(
        data: Map<*, *>,
        eventType: String?,
        currentReasoning: String,
        candidateReasoning: String
    ): Boolean {
        if (currentReasoning.isEmpty()) {
            return false
        }

        if (eventType == null) {
            return candidateReasoning.length > currentReasoning.length
        }

        if ("message" in data && eventType.contains("completed")) {
            return true
        }

        return eventType.contains("completed") ||
            eventType.contains("done") ||
            candidateReasoning.length > currentReasoning.length
    }

    private fun sanitizeModelOutput(rawText: String?): String {
        if (rawText.isNullOrBlank()) {
            return ""
        }

        return rawText
            .replace(Regex("(?is)<thinking>.*?</thinking>"), "")
            .replace(Regex("(?is)<think>.*?</think>"), "")
            .replace("\u0000", "")
    }

    private fun resolveAuthorizationHeader(rawToken: String, tokenFilePath: String): String? {
        val tokenValue = when {
            rawToken.isNotBlank() -> rawToken.trim()
            tokenFilePath.isNotBlank() -> readTokenFromFile(tokenFilePath)
            else -> null
        } ?: return null
        return tokenValue
    }

    private fun readTokenFromFile(tokenFilePath: String): String? {
        return try {
            val tokenPath = Path.of(tokenFilePath)
            if (!Files.exists(tokenPath)) {
                logger.warn { "Файл с токеном не найден: $tokenFilePath" }
                null
            } else {
                Files.readString(tokenPath).trim().ifBlank { null }
            }
        } catch (e: Exception) {
            logger.error(e) { "Не удалось прочитать токен из файла: $tokenFilePath" }
            null
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
