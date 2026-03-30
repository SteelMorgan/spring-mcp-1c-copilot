package ru.alkoleft.copilot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class OneCCopilotService(
    private val oneCApiClient: OneCApiClient
) {
    @Tool(
        name = "ask_1c_ai",
        description = "Ask question to 1C:Assistant AI"
    )
    fun ask1CAI(
        @ToolParam(description = "Question for 1C:Assistant AI")
        question: String,
        @ToolParam(description = "Programming language", required = false)
        programmingLanguage: String? = null,
        @ToolParam(description = "Create new session", required = false)
        createNewSession: Boolean = false
    ): String {
        logger.debug { "ask_1c_ai called with question='$question', createNewSession='$createNewSession'" }
        
        return try {
            val result = oneCApiClient.askQuestion(question, createNewSession)
            logger.debug { "ask_1c_ai result: $result" }
            result
        } catch (e: Exception) {
            logger.error(e) { "Error in ask_1c_ai" }
            "❌ **Error:** ${e.message}"
        }
    }

    @Tool(
        name = "explain_1c_syntax",
        description = "Explain 1C syntax"
    )
    fun explain1CSyntax(
        @ToolParam(description = "Syntax element to explain")
        syntaxElement: String,
        @ToolParam(description = "Usage context", required = false)
        context: String? = null
    ): String {
        logger.debug { "explain_1c_syntax called with syntaxElement='$syntaxElement', context='$context'" }
        
        return try {
            val question = if (context != null) {
                "Объясни синтаксис и использование: $syntaxElement в контексте: $context"
            } else {
                "Объясни синтаксис и использование: $syntaxElement"
            }
            
            val result = oneCApiClient.askQuestion(question)
            logger.debug { "explain_1c_syntax result: $result" }
            result
        } catch (e: Exception) {
            logger.error(e) { "Error in explain_1c_syntax" }
            "❌ **Error:** ${e.message}"
        }
    }

    @Tool(
        name = "check_1c_code",
        description = "Check 1C code for errors"
    )
    fun check1CCode(
        @ToolParam(description = "1C code to check")
        code: String,
        @ToolParam(description = "Check type: syntax, logic, performance", required = false)
        checkType: String = "syntax"
    ): String {
        logger.debug { "check_1c_code called with code='$code', checkType='$checkType'" }
        
        return try {
            val checkTypes = mapOf(
                "syntax" to "синтаксические ошибки",
                "logic" to "логические ошибки и потенциальные проблемы",
                "performance" to "проблемы производительности и оптимизации"
            )
            val checkDesc = checkTypes[checkType.lowercase()] ?: "ошибки"
            
            val question = "Проверь этот код 1С на $checkDesc и дай рекомендации:\n\n```1c\n$code\n```"
            
            val result = oneCApiClient.askQuestion(question)
            logger.debug { "check_1c_code result: $result" }
            result
        } catch (e: Exception) {
            logger.error(e) { "Error in check_1c_code" }
            "❌ **Error:** ${e.message}"
        }
    }
}
