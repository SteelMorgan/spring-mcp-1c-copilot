package ru.alkoleft.copilot.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OneCApiClientTest {

    private val client = OneCApiClient(
        token = "test-token",
        tokenFile = "",
        baseUrl = "https://example.com",
        timeout = 30,
        skillName = "raw"
    )

    @Test
    fun `parseSseResponse parses legacy content delta stream`() {
        val sse = """
            data: {"content_delta":{"content":"Привет"}}
            data: {"content_delta":{"content":", мир"}}
            data: [DONE]
        """.trimIndent()

        val result = invokeParseSseResponse(sse)

        assertEquals("Привет, мир", result)
    }

    @Test
    fun `parseSseResponse returns reasoning-only error for legacy stream`() {
        val sse = """
            data: {"content_delta":{"reasoning_content":"Думаю"}}
            data: {"content_delta":{"reasoning_content":" дальше"}}
            data: [DONE]
        """.trimIndent()

        val result = invokeParseSseResponse(sse)

        assertEquals("Ошибка: получены только рассуждения модели без итогового ответа", result)
    }

    @Test
    fun `parseSseResponse parses openai-like choices delta stream`() {
        val sse = """
            data: {"choices":[{"delta":{"content":"Hello"}}]}
            data: {"choices":[{"delta":{"content":" world"}}]}
            data: [DONE]
        """.trimIndent()

        val result = invokeParseSseResponse(sse)

        assertEquals("Hello world", result)
    }

    @Test
    fun `parseSseResponse returns reasoning-only error for openai-like stream`() {
        val sse = """
            data: {"choices":[{"delta":{"reasoning_content":"step 1"}}]}
            data: {"choices":[{"message":{"reasoning_content":"step 1 and 2"}}]}
            data: [DONE]
        """.trimIndent()

        val result = invokeParseSseResponse(sse)

        assertEquals("Ошибка: получены только рассуждения модели без итогового ответа", result)
    }

    @Test
    fun `parseSseResponse parses mixed legacy and choices stream`() {
        val sse = """
            data: {"content_delta":{"content":"Hi"}}
            data: {"choices":[{"delta":{"content":" there"}}]}
            data: {"choices":[{"message":{"content":"Hi there!"}}]}
            data: [DONE]
        """.trimIndent()

        val result = invokeParseSseResponse(sse)

        assertEquals("Hi there!", result)
    }

    @Test
    fun `parseSseResponse ignores event lines and handles done`() {
        val sse = """
            event: message
            data: {"choices":[{"delta":{"content":"ok"}}]}
            event: done
            data: [DONE]
            data: {"choices":[{"delta":{"content":" skipped"}}]}
        """.trimIndent()

        val result = invokeParseSseResponse(sse)

        assertEquals("ok", result)
    }

    @Test
    fun `parseSseResponse skips invalid json chunk between valid chunks`() {
        val sse = """
            data: {"choices":[{"delta":{"content":"A"}}]}
            data: {invalid-json}
            data: {"choices":[{"delta":{"content":"B"}}]}
            data: [DONE]
        """.trimIndent()

        val result = invokeParseSseResponse(sse)

        assertEquals("AB", result)
    }

    @Test
    fun `parseSseResponse stops on choices finish_reason`() {
        val sse = """
            data: {"choices":[{"delta":{"content":"Готово"},"finish_reason":"stop"}]}
            data: {"choices":[{"delta":{"content":" лишнее"}}]}
        """.trimIndent()

        val result = invokeParseSseResponse(sse)

        assertEquals("Готово", result)
    }

    @Test
    fun `parseSseResponse stops on assistant finished true`() {
        val sse = """
            data: {"role":"assistant","content":{"content":"Итог"},"finished":true}
            data: {"content_delta":{"content":" лишнее"}}
        """.trimIndent()

        val result = invokeParseSseResponse(sse)

        assertEquals("Итог", result)
    }

    @Test
    fun `parseSseResponse parses multiline SSE event payload`() {
        val sse = """
            event: response.output_text.delta
            data: {"type":"response.output_text.delta",
            data: "delta":"Ответ ",
            data: "item_id":"item_1"}

            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"готов"}

            data: [DONE]
        """.trimIndent()

        val result = invokeParseSseResponse(sse)

        assertEquals("Ответ готов", result)
    }

    @Test
    fun `parseSseResponse parses reasoning stream with final completed message`() {
        val sse = """
            event: response.reasoning.delta
            data: {"type":"response.reasoning.delta","delta":"Шаг 1"}

            event: response.completed
            data: {"type":"response.completed","response":{"output":[{"content":[{"type":"output_text","text":"Финальный ответ"}]}]}}

            data: [DONE]
        """.trimIndent()

        val result = invokeParseSseResponse(sse)

        assertEquals("Финальный ответ", result)
    }

    @Test
    fun `sanitizeModelOutput removes thinking tags`() {
        val result = invokeSanitizeModelOutput("<thinking>hidden</thinking>Visible")

        assertEquals("Visible", result)
    }

    @Test
    fun `sanitizeModelOutput removes think tags`() {
        val result = invokeSanitizeModelOutput("<think>hidden</think>Visible")

        assertEquals("Visible", result)
    }

    @Test
    fun `sanitizeModelOutput removes mixed think tags in text`() {
        val result = invokeSanitizeModelOutput("Start <thinking>inner</thinking> Mid <think>deep</think> End")

        assertEquals("Start  Mid  End", result)
    }

    private fun invokeParseSseResponse(input: String): String {
        val method = OneCApiClient::class.java.getDeclaredMethod("parseSseResponse", String::class.java)
        method.isAccessible = true
        return method.invoke(client, input) as String
    }

    private fun invokeSanitizeModelOutput(input: String?): String {
        val method = OneCApiClient::class.java.getDeclaredMethod("sanitizeModelOutput", String::class.java)
        method.isAccessible = true
        return method.invoke(client, input) as String
    }
}
