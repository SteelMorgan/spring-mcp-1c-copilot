package ru.alkoleft.copilot.config

import io.modelcontextprotocol.server.McpServerFeatures
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.alkoleft.copilot.service.OneCCopilotService

@Configuration
class McpConfiguration {
    @Bean
    fun mcpTools(oneCCopilotService: OneCCopilotService): ToolCallbackProvider =
        MethodToolCallbackProvider
            .builder()
            .toolObjects(oneCCopilotService)
            .build()

    @Bean
    fun mcpResources(): List<McpServerFeatures.SyncResourceSpecification> = emptyList()

    @Bean
    fun mcpResourceTemplates(): List<McpServerFeatures.SyncResourceTemplateSpecification> = emptyList()
}
