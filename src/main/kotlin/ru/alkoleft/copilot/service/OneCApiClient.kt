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
    @Value("\${onec.ai.skill-name:raw}") private val skillName: String,
    @Value("\${onec.ai.ui-language:ru}") private val uiLanguage: String,
    @Value("\${onec.ai.programming-language:1c}") private val defaultProgrammingLanguage: String,
    @Value("\${onec.ai.script-language:ru}") private val scriptLanguage: String
) {
    companion object {
        private const val MAX_TOOL_CALL_ROUNDS = 4
    }

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
    private data class ConversationState(
        val sessionId: String,
        var lastMessageUuid: String? = null
    )
    private data class ParsedSseResponse(
        val answerText: String,
        val reasoningText: String,
        val assistantMessageUuid: String?,
        val toolCalls: List<Map<*, *>>
    )

    private var currentSession: ConversationState? = null
    
    fun askQuestion(
        question: String,
        createNewSession: Boolean = false,
        programmingLanguage: String? = null
    ): String {
        return askQuestionInternal(question, createNewSession, programmingLanguage, allowRawFallback = true)
    }

    private fun askQuestionInternal(
        question: String,
        createNewSession: Boolean,
        programmingLanguage: String?,
        allowRawFallback: Boolean
    ): String {
        try {
            val session = if (createNewSession || currentSession == null) {
                createNewSession(skillName, programmingLanguage)
            } else {
                currentSession!!
            }
            return askQuestionInExistingSession(question, session, programmingLanguage, allowRawFallback)
            
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при обращении к 1С:Напарник API" }
            return "Ошибка: ${e.message}"
        }
    }

    private fun askQuestionInExistingSession(
        question: String,
        session: ConversationState,
        programmingLanguage: String?,
        allowRawFallback: Boolean
    ): String {
        val answerSegments = mutableListOf<String>()
        var request: Map<String, Any?> = buildQuestionRequest(question, session.lastMessageUuid)

        repeat(MAX_TOOL_CALL_ROUNDS + 1) { round ->
            val response = executePost(
                path = "/chat_api/v1/conversations/${session.sessionId}/messages",
                requestBody = request,
                accept = "text/event-stream"
            )
            if (response.statusCode != 200) {
                logger.error { "1C API error: status=${response.statusCode}, body=${response.body}" }
                return "Ошибка: HTTP ${response.statusCode} от 1С:Напарник"
            }

            val parsed = parseSseResponseDetails(response.body)
            if (parsed.assistantMessageUuid != null) {
                session.lastMessageUuid = parsed.assistantMessageUuid
            }
            if (parsed.answerText.isNotBlank()) {
                appendAnswerSegment(answerSegments, parsed.answerText)
            }

            if (parsed.toolCalls.isEmpty()) {
                return buildFinalAnswer(answerSegments, parsed)
            }

            if (session.lastMessageUuid == null) {
                logger.error { "1C API returned tool calls without assistant message uuid: body=${response.body}" }
                if (allowRawFallback) {
                    logger.warn { "Retrying with raw skill because tool call continuation is impossible" }
                    val rawSession = createNewSession("raw", programmingLanguage, persistSession = false)
                    return askQuestionInExistingSession(question, rawSession, programmingLanguage, allowRawFallback = false)
                }
                return "Ошибка: 1С:Напарник запросил tool_calls без uuid сообщения"
            }

            if (round >= MAX_TOOL_CALL_ROUNDS) {
                logger.error { "1C API tool call loop exceeded $MAX_TOOL_CALL_ROUNDS rounds" }
                return buildFinalAnswer(answerSegments, parsed)
                    .takeIf { !it.startsWith("Ошибка:") }
                    ?: "Ошибка: превышен лимит обработки tool_calls 1С:Напарник"
            }

            logger.info { "1C API returned ${parsed.toolCalls.size} tool_calls, sending accepted tool results" }
            request = buildToolResultRequest(session.lastMessageUuid!!, parsed.toolCalls)
        }

        return "Ошибка: превышен лимит обработки tool_calls 1С:Напарник"
    }
    
    private fun buildResponseInstruction(question: String): String {
        return """
            $question

            Верни только финальный ответ для пользователя.
            Не добавляй рассуждения, планы, скрытые мысли, теги <thinking> и служебные пояснения.
            Если не можешь дать ответ — верни краткое сообщение об ошибке.
        """.trimIndent()
    }

    private fun buildQuestionRequest(question: String, parentUuid: String?): Map<String, Any?> {
        val responseInstruction = buildResponseInstruction(question)
        return mapOf(
            "role" to "user",
            "content" to mapOf(
                "content" to mapOf("instruction" to responseInstruction)
            ),
            "parent_uuid" to parentUuid
        )
    }

    private fun buildToolResultRequest(parentUuid: String, toolCalls: List<Map<*, *>>): Map<String, Any?> {
        return mapOf(
            "role" to "tool",
            "parent_uuid" to parentUuid,
            "content" to toolCalls.mapNotNull { toolCall ->
                val toolCallId = toolCall["id"] as? String
                if (toolCallId.isNullOrBlank()) {
                    logger.warn { "Skipping tool_call without id: $toolCall" }
                    null
                } else {
                    mapOf(
                        "tool_call_id" to toolCallId,
                        "status" to "accepted",
                        "content" to null
                    )
                }
            }
        )
    }

    private fun createNewSession(
        skillName: String,
        programmingLanguage: String?,
        persistSession: Boolean = true
    ): ConversationState {
        try {
            val request = mapOf(
                "skill_name" to skillName,
                "ui_language" to uiLanguage,
                "programming_language" to normalizeProgrammingLanguage(programmingLanguage),
                "script_language" to scriptLanguage,
                "is_chat" to true
            )
            val response: HttpResponse = executePost(
                path = "/chat_api/v1/conversations/",
                requestBody = request,
                accept = "application/json",
                headers = mapOf("Session-Id" to "")
            )
            if (response.statusCode != 200) {
                logger.error { "1C API session init error: status=${response.statusCode}, body=${response.body.take(2000)}" }
                throw RuntimeException("Ошибка создания сессии: HTTP ${response.statusCode}")
            }
            val responseMap: Map<*, *> = objectMapper.readValue(response.body, Map::class.java)
            val sessionId: String? = responseMap["uuid"] as? String
            if (sessionId != null) {
                val session = ConversationState(sessionId)
                if (persistSession) {
                    currentSession = session
                }
                return session
            } else {
                throw RuntimeException("Не удалось создать сессию")
            }
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при создании сессии" }
            throw e
        }
    }

    private fun normalizeProgrammingLanguage(programmingLanguage: String?): String {
        val normalized = programmingLanguage?.trim()?.takeIf { it.isNotBlank() } ?: defaultProgrammingLanguage
        return when (normalized.lowercase()) {
            "bsl", "1с", "1c:enterprise", "1c_enterprise" -> "1c"
            else -> normalized
        }
    }
    
    private fun parseSseResponse(sseResponse: String): String {
        return buildFinalAnswer(emptyList(), parseSseResponseDetails(sseResponse))
    }

    private fun parseSseResponseDetails(sseResponse: String): ParsedSseResponse {
        try {
            var answerText = ""
            var reasoningText = ""
            var assistantMessageUuid: String? = null
            var toolCalls = emptyList<Map<*, *>>()

            for (dataStr in extractSseDataEvents(sseResponse)) {
                if (dataStr == "[DONE]") {
                    break
                }

                try {
                    val data = objectMapper.readValue(dataStr, Map::class.java)
                    var shouldStop = false
                    val eventType = (data["type"] as? String)?.lowercase()
                    val role = data["role"] as? String

                    if (role == "user") {
                        continue
                    }

                    val contentDelta = data["content_delta"] as? Map<*, *>
                    if (contentDelta != null) {
                        val deltaToolCalls = extractToolCalls(contentDelta)
                        if (deltaToolCalls.isNotEmpty()) {
                            toolCalls = deltaToolCalls
                        }
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
                    val contentToolCalls = extractToolCalls(content)
                    if (contentToolCalls.isNotEmpty()) {
                        toolCalls = contentToolCalls
                    }
                    val finalContent = sanitizeModelOutput(extractAnswerText(content))
                    if (role != "tool" && finalContent.isNotEmpty() && finalContent.length > answerText.length) {
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

                    if (role != "tool" && shouldUseGenericEventExtraction(data)) {
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

                    val finished = when (val finishedValue = data["finished"]) {
                        is Boolean -> finishedValue
                        is String -> finishedValue.equals("true", ignoreCase = true)
                        else -> false
                    }
                    if (role == "assistant" && finished) {
                        assistantMessageUuid = data["uuid"] as? String ?: assistantMessageUuid
                        shouldStop = true
                    }

                    if (shouldStop) {
                        break
                    }
                } catch (e: Exception) {
                    logger.warn { "Ошибка парсинга SSE chunk: $e" }
                }
            }

            return ParsedSseResponse(
                answerText = answerText.trim(),
                reasoningText = reasoningText,
                assistantMessageUuid = assistantMessageUuid,
                toolCalls = toolCalls
            )
        } catch (e: Exception) {
            logger.error(e) { "Ошибка парсинга SSE ответа" }
            return ParsedSseResponse(
                answerText = "Ошибка парсинга ответа: ${e.message}",
                reasoningText = "",
                assistantMessageUuid = null,
                toolCalls = emptyList()
            )
        }
    }

    private fun buildFinalAnswer(answerSegments: List<String>, parsed: ParsedSseResponse): String {
        val allSegments = answerSegments.toMutableList()
        if (parsed.answerText.isNotBlank()) {
            appendAnswerSegment(allSegments, parsed.answerText)
        }
        val normalizedAnswer = allSegments.joinToString("\n\n").trim()
        return when {
            normalizedAnswer.isNotEmpty() -> normalizedAnswer
            parsed.toolCalls.isNotEmpty() -> "Ошибка: 1С:Напарник запросил tool_calls, но не вернул итоговый ответ"
            parsed.reasoningText.isNotEmpty() -> {
                logger.warn { "От 1С:Напарник получены только рассуждения без итогового ответа" }
                "Ошибка: получены только рассуждения модели без итогового ответа"
            }
            else -> "Ошибка: не получен ответ от 1С:Напарник"
        }
    }

    private fun appendAnswerSegment(answerSegments: MutableList<String>, text: String) {
        val normalized = text.trim()
        if (normalized.isNotEmpty() && answerSegments.lastOrNull() != normalized) {
            answerSegments += normalized
        }
    }

    private fun extractToolCalls(content: Map<*, *>?): List<Map<*, *>> {
        val toolCalls = content?.get("tool_calls") as? List<*> ?: return emptyList()
        return toolCalls.mapNotNull { it as? Map<*, *> }
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

    private fun executePost(
        path: String,
        requestBody: Any,
        accept: String? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse {
        var requestSpec = if (accept != null) {
            webClient.post().uri(path).header("Accept", accept)
        } else {
            webClient.post().uri(path)
        }
        headers.forEach { (name, value) ->
            requestSpec = requestSpec.header(name, value)
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
