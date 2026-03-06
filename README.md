# 🚀 Spring Boot MCP Server для 1С:Напарник

**Полнофункциональный MCP (Model Context Protocol) сервер для интеграции с 1С:Напарник API**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)
[![MCP](https://img.shields.io/badge/MCP-Protocol-orange.svg)](https://modelcontextprotocol.io/)
[![License](https://img.shields.io/badge/License-Open%20Source%20(Non--Commercial)-yellow.svg)](LICENSE)

> **📄 Лицензия:** Open Source для личного и некоммерческого использования. Коммерческое использование только с разрешения автора.

## ✨ Возможности

- 🎯 **MCP Protocol Support**: Полная поддержка протокола MCP для интеграции с AI ассистентами
- 🤖 **1С:Напарник Integration**: Прямая интеграция с API 1С:Напарник через правильные endpoints
- ⚡ **SSE Support**: Поддержка Server-Sent Events для real-time коммуникации
- 🌐 **REST API**: Дополнительные REST endpoints для прямого взаимодействия
- 📚 **Swagger UI**: Интерактивная документация API
- 🐳 **Docker Ready**: Готовые Docker конфигурации для развертывания
- 🔐 **Авторизация**: Поддержка Bearer токенов для 1С:Напарник API

## 🛠️ Инструменты MCP

### 1. `ask_1c_ai`
Задать вопрос ИИ 1С:Напарник
- **question** (обязательный): Вопрос для ИИ
- **programming_language** (опционально): Язык программирования
- **create_new_session** (опционально): Создать новую сессию

### 2. `explain_1c_syntax`
Объяснить синтаксис элемента 1С
- **syntax_element** (обязательный): Элемент синтаксиса для объяснения
- **context** (опционально): Контекст использования

### 3. `check_1c_code`
Проверить код 1С на ошибки
- **code** (обязательный): Код 1С для проверки
- **check_type** (опционально): Тип проверки (syntax, logic, performance)

## 🚀 Быстрый старт

### Требования
- Java 17+
- Docker (рекомендуется)
- Токен доступа к 1С:Напарник API

### 1. Получение токена 1С:Напарник

1. Зарегистрируйтесь на [code.1c.ai](https://code.1c.ai)
2. Получите токен доступа в личном кабинете
3. Сохраните токен для использования в переменных окружения

### 2. Локальный запуск

```bash
# Клонируйте репозиторий
git clone <repository-url>
cd spring-mcp-1c-copilot

# 1. Скопируйте пример переменных окружения
cp env.example .env

# 2. Вставьте токен в файл .env в строку:
# ONEC_AI_TOKEN=your_token_here

# 3. Перенесите токен из .env в локальный secret-файл
./scripts/prepare-token-secret.sh

# 4. Запустите приложение
export ONEC_AI_TOKEN_FILE="$PWD/.secrets/onec_ai_token"
export ONEC_AI_BASE_URL="https://code.1c.ai"
export ONEC_AI_SKILL_NAME="raw"

./gradlew bootRun
```

### 3. Docker запуск (рекомендуется)

```bash
# 1. Скопируйте env и вставьте токен в .env в строку ONEC_AI_TOKEN=...
cp env.example .env

# 2. Перенесите токен в локальный secret-файл
./scripts/prepare-token-secret.sh

# 3. Сборка образа
docker build -f Dockerfile.build -t spring-mcp-1c-copilot .

# 4. Запуск контейнера
docker compose up -d
```

### 4. Сеть Docker

Текущий `docker-compose.yml` подключает контейнер к внешней сети `infra`.

Это актуально при работе в песочнице из репозитория [SteelMorgan/1c-agent-based-dev-framework](https://github.com/SteelMorgan/1c-agent-based-dev-framework), где такая сеть уже существует и используется для взаимодействия контейнеров между собой.

В этом случае MCP endpoint внутри docker-сети будет доступен по адресу:

```text
http://spring-mcp-1c-copilot:8000/mcp
```

Если вы запускаете сервис вне этой песочницы, попросите своего агента помочь вам настроить сеть под ваше окружение. В других проектах внешняя сеть `infra` может отсутствовать или называться иначе.

## 📡 API Endpoints

### MCP Endpoints
- `POST /mcp` - Основной MCP endpoint для JSON-RPC запросов
- `GET /mcp` - SSE stream для MCP клиентов

### REST API Endpoints
- `POST /api/ask-ai` - Задать вопрос ИИ 1С:Напарник
- `POST /api/explain-syntax` - Объяснить синтаксис 1С
- `POST /api/check-code` - Проверить код 1С на ошибки
- `GET /api/health` - Проверка состояния сервера

### Документация
- `http://localhost:8000/swagger-ui.html` - Swagger UI документация
- `http://localhost:8000/api-docs` - OpenAPI JSON схема

## ⚙️ Конфигурация

### Что важно в текущей версии

- По умолчанию используется `ONEC_AI_SKILL_NAME=raw`, потому что для справочного режима это стабильнее и не уводит Напарника во внутренние `tool_calls`.
- Рекомендуемый способ хранения токена: файл `.secrets/onec_ai_token` через `ONEC_AI_TOKEN_FILE`.
- Для миграции токена из `.env` в secret-файл добавлен скрипт `./scripts/prepare-token-secret.sh`.
- В docker-compose контейнер монтирует secret-файл и может работать в сети `infra` для песочницы из `1c-agent-based-dev-framework`.

### Переменные окружения

| Переменная | Описание | По умолчанию |
|------------|----------|--------------|
| `ONEC_AI_TOKEN` | Токен доступа к 1С:Напарник API | **Обязательно** |
| `ONEC_AI_TOKEN_FILE` | Путь к файлу с токеном; используется вместо `ONEC_AI_TOKEN`, если env пуст | - |
| `ONEC_AI_BASE_URL` | Базовый URL API | `https://code.1c.ai` |
| `ONEC_AI_TIMEOUT` | Таймаут запросов (сек) | `30` |
| `ONEC_AI_SKILL_NAME` | Режим сессии Напарника; для прямых ответов используйте `raw` | `raw` |
| `SSE_PORT` | Порт сервера | `8000` |

### application.yml

```yaml
onec:
  ai:
    token: ${ONEC_AI_TOKEN:}
    token-file: ${ONEC_AI_TOKEN_FILE:}
    base-url: ${ONEC_AI_BASE_URL:https://code.1c.ai}
    timeout: ${ONEC_AI_TIMEOUT:30}
    skill-name: ${ONEC_AI_SKILL_NAME:raw}

server:
  port: ${SSE_PORT:8000}

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
```

## 🔗 Интеграция с Cursor IDE

Добавьте в `~/.cursor/mcp.json`:

```json
{
  "servers": {
    "1c-copilot-proxy": {
      "url": "http://localhost:8000/mcp"
    }
  }
}
```

После этого в Cursor IDE будут доступны все инструменты для работы с 1С:Напарник!

## 🏗️ Структура проекта

```
src/main/kotlin/ru/alkoleft/copilot/
├── McpCopilotApplication.kt          # Главный класс приложения
├── config/
│   ├── McpConfiguration.kt           # MCP конфигурация
│   └── OpenApiConfig.kt              # Swagger конфигурация
├── controller/
│   ├── McpController.kt              # MCP контроллер
│   └── RestApiController.kt          # REST API контроллер
└── service/
    ├── OneCApiClient.kt              # Клиент для 1С:Напарник API
    └── OneCCopilotService.kt         # Сервис с MCP инструментами
```

## 🧪 Тестирование

### Тест MCP инициализации
```bash
curl -X POST http://localhost:8000/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize",
    "params": {
      "protocolVersion": "2025-06-18",
      "capabilities": {"tools": true}
    }
  }'
```

### Тест REST API
```bash
curl -X POST "http://localhost:8000/api/ask-ai?question=Как создать справочник в 1С?"
```

## 🔧 Разработка

### Безопасное хранение токена

Рекомендуемый путь такой:

```bash
# 1. Скопируйте пример env
cp env.example .env

# 2. Откройте .env и вставьте токен в строку:
# ONEC_AI_TOKEN=your_token_here

# 3. Один раз выполните миграцию в локальный secret-файл
./scripts/prepare-token-secret.sh

# После этого:
# - токен будет записан в .secrets/onec_ai_token
# - строка ONEC_AI_TOKEN=... будет удалена из .env
# - в .env появится ONEC_AI_TOKEN_FILE=.secrets/onec_ai_token
```

Если хотите положить токен в файл вручную, используйте тот же путь:

```bash
mkdir -p .secrets
chmod 700 .secrets
printf '%s' 'your_token_here' > .secrets/onec_ai_token
chmod 600 .secrets/onec_ai_token

export ONEC_AI_TOKEN_FILE="$PWD/.secrets/onec_ai_token"
unset ONEC_AI_TOKEN
./gradlew bootRun
```

Клиент отправляет токен в заголовок `Authorization` ровно в том виде, как он лежит в файле. Для токенов 1С:Напарник в этом окружении префикс `Bearer` не требуется.

Для Docker Compose перед первым запуском достаточно выполнить:

```bash
./scripts/prepare-token-secret.sh
docker compose up -d
```

### Сборка
```bash
./gradlew build
```

### Запуск тестов
```bash
./gradlew test
```

### Локальная разработка
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## 📋 Логи и отладка

### Просмотр логов Docker контейнера
```bash
docker logs spring-mcp-1c-copilot --follow
```

### Уровни логирования
- `DEBUG` - Подробные логи для разработки
- `INFO` - Основные события (по умолчанию)
- `WARN` - Предупреждения
- `ERROR` - Ошибки

## 🚀 Производственное развертывание

См. [DEPLOYMENT.md](DEPLOYMENT.md) для подробного руководства по развертыванию на серверах.

## 🙏 Благодарности

Этот проект основан на наработках следующих репозиториев:

- **[artesk/1copilot_MCP](https://github.com/artesk/1copilot_MCP)** - оригинальный MCP сервер для 1С:Напарник на Python
- **[rentgengl/copilot-1c-proxy](https://github.com/rentgengl/copilot-1c-proxy)** - прокси-сервер для запросов в 1С:Напарник

**Огромное спасибо авторам этих проектов!** 🎉

Наш Spring Boot MCP Server развивает идеи этих проектов, добавляя:
- Современную архитектуру на Spring Boot и Kotlin
- Полную поддержку MCP протокола с SSE
- Swagger UI документацию
- Готовые конфигурации для продакшена
- Автоматизацию развертывания

## 🤝 Вклад в проект

1. Fork репозитория
2. Создайте feature branch (`git checkout -b feature/amazing-feature`)
3. Commit изменения (`git commit -m 'Add amazing feature'`)
4. Push в branch (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

## 📄 Лицензия

**Лицензия: Open Source (Персональное и некоммерческое использование)**

- ✅ **Личное использование** - свободно
- ✅ **Некоммерческое использование** - свободно  
- ✅ **Образовательные цели** - свободно
- ❌ **Коммерческое использование** - только с разрешения автора

Для получения разрешения на коммерческое использование свяжитесь с автором.

**Основанные проекты:**
- [artesk/1copilot_MCP](https://github.com/artesk/1copilot_MCP) - Unlicense
- [rentgengl/copilot-1c-proxy](https://github.com/rentgengl/copilot-1c-proxy) - MIT License

## 🆘 Поддержка

Если у вас возникли проблемы:

1. Проверьте [Issues](https://github.com/your-repo/issues)
2. Убедитесь, что токен 1С:Напарник действителен
3. Проверьте логи контейнера: `docker logs spring-mcp-1c-copilot`
4. Создайте новый Issue с подробным описанием проблемы

---

**🎉 Наслаждайтесь работой с 1С:Напарник через MCP!**
