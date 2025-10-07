package ru.alkoleft.copilot.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    
    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("1C Assistant MCP Server")
                    .description("MCP (Model Context Protocol) сервер для интеграции с 1С:Напарник API")
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("1C Assistant MCP Server")
                            .email("support@example.com")
                    )
                    .license(
                        License()
                            .name("MIT License")
                            .url("https://opensource.org/licenses/MIT")
                    )
            )
            .servers(
                listOf(
                    Server()
                        .url("http://localhost:8000")
                        .description("Локальный сервер разработки"),
                    Server()
                        .url("https://your-domain.com")
                        .description("Продакшн сервер")
                )
            )
    }
}
