# Spring Boot MCP Server для 1С:Напарник

MCP (Model Context Protocol) сервер на Spring Boot для интеграции с 1С:Напарник API.

## Возможности

- **ask_1c_ai** - задать вопрос ИИ 1С:Напарник
- **explain_1c_syntax** - объяснить синтаксис 1С
- **check_1c_code** - проверить код 1С на ошибки

## Быстрый старт

### 1. Сборка и запуск через Docker

```bash
# Сборка образа
docker build -f Dockerfile.build -t spring-mcp-1c-copilot .

# Запуск контейнера
docker run -d --name spring-mcp-1c-copilot -p 8000:8000 \
  -e ONEC_AI_TOKEN="your_token_here" \
  spring-mcp-1c-copilot
```

### 2. Настройка в Cursor IDE

Добавьте в `~/.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "1c-copilot-proxy": {
      "url": "http://localhost:8000/mcp"
    }
  }
}
```

### 3. Тестирование

```bash
# Запуск тестов
.\test_mcp_simple.ps1
```

## Конфигурация

### Переменные окружения

- `ONEC_AI_TOKEN` - токен для 1С:Напарник API
- `ONEC_AI_BASE_URL` - базовый URL API (по умолчанию: https://code.1c.ai)
- `ONEC_AI_TIMEOUT` - таймаут запросов (по умолчанию: 30 сек)
- `SSE_PORT` - порт сервера (по умолчанию: 8000)

### Профили Spring

- `sse` - режим Server-Sent Events (по умолчанию)

## Архитектура

```
src/main/kotlin/ru/alkoleft/copilot/
├── McpCopilotApplication.kt          # Главный класс приложения
├── config/
│   └── McpConfiguration.kt          # Конфигурация MCP
├── controller/
│   └── McpController.kt             # REST контроллер для MCP
└── service/
    ├── OneCApiClient.kt             # Клиент для 1С:Напарник API
    └── OneCCopilotService.kt        # Сервис с MCP инструментами
```

## MCP Протокол

Сервер поддерживает следующие MCP методы:

- `initialize` - инициализация соединения
- `tools/list` - список доступных инструментов
- `tools/call` - вызов инструмента

## Разработка

### Локальная сборка

```bash
./gradlew build
./gradlew bootRun
```

### Docker Compose

```bash
docker-compose up --build
```

## Лицензия

MIT License
