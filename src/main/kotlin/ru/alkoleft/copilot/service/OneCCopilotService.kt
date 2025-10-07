package ru.alkoleft.copilot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class OneCCopilotService(
    private val oneCApiClient: OneCApiClient
) {
    
    fun ask1CAI(
        question: String,
        programmingLanguage: String? = null,
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
    
    fun explain1CSyntax(
        syntaxElement: String,
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
    
    fun check1CCode(
        code: String,
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
