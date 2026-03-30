# 🚀 Быстрый старт Spring Boot MCP Server

Это руководство поможет вам быстро развернуть Spring Boot MCP Server для 1С:Напарник.

## ⚡ За 5 минут

### 1. Получите токен 1С:Напарник

1. Зарегистрируйтесь на [code.1c.ai](https://code.1c.ai)
2. Получите токен в личном кабинете
3. Сохраните токен

### 2. Клонируйте репозиторий

```bash
git clone <repository-url>
cd spring-mcp-1c-copilot
```

### 3. Установите переменные окружения

**Linux/macOS:**
```bash
export ONEC_AI_TOKEN="your_token_here"
```

**Windows PowerShell:**
```powershell
$env:ONEC_AI_TOKEN = "your_token_here"
```

### 4. Запустите развертывание

**Linux/macOS:**
```bash
./deploy.sh dev
```

**Windows PowerShell:**
```powershell
.\deploy.ps1 dev
```

### 5. Проверьте работу

Откройте в браузере:
- **MCP Server**: http://localhost:8000
- **Swagger UI**: http://localhost:8000/swagger-ui.html
- **Health Check**: http://localhost:8000/api/health

## 🔧 Настройка Cursor IDE

По умолчанию сервер запускается в режиме streamable HTTP на `http://localhost:8000/mcp`.

Если клиенту нужен SSE transport, запускайте контейнер с `SPRING_PROFILES_ACTIVE=sse`.

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

Перезапустите Cursor IDE. Теперь у вас есть доступ к инструментам 1С:Напарник!

## 🧪 Тестирование

### Тест через REST API

```bash
# Задать вопрос
curl -X POST "http://localhost:8000/api/ask-ai?question=Как создать справочник в 1С?"

# Объяснить синтаксис
curl -X POST "http://localhost:8000/api/explain-syntax?syntax_element=Справочник"

# Проверить код
curl -X POST "http://localhost:8000/api/check-code?code=Процедура Тест() КонецПроцедуры"
```

### Тест через MCP

```bash
# Инициализация
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

# Список инструментов
curl -X POST http://localhost:8000/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list",
    "params": {}
  }'
```

## 🐳 Docker команды

```bash
# Просмотр логов
docker logs spring-mcp-1c-copilot -f

# Остановка
docker-compose down

# Перезапуск
docker-compose restart

# Обновление
docker-compose down
docker-compose up -d --build
```

## 🔍 Устранение неполадок

### Сервер не запускается

```bash
# Проверьте логи
docker logs spring-mcp-1c-copilot

# Проверьте переменные окружения
docker exec spring-mcp-1c-copilot env | grep ONEC_AI
```

### API недоступен

```bash
# Проверьте порты
netstat -tlnp | grep 8000

# Проверьте firewall
sudo ufw status
```

### Ошибки авторизации

```bash
# Проверьте токен
echo $ONEC_AI_TOKEN

# Тест API 1С:Напарник
curl -H "Authorization: Bearer $ONEC_AI_TOKEN" https://code.1c.ai/api/health
```

## 📚 Дополнительные ресурсы

- [Полная документация](README.md)
- [Руководство по развертыванию](DEPLOYMENT.md)
- [Примеры конфигурации](env.example)

## 📄 Лицензия

**Важно:** Этот проект предназначен для личного и некоммерческого использования.

- ✅ **Личное использование** - свободно
- ✅ **Некоммерческое использование** - свободно  
- ✅ **Образовательные цели** - свободно
- ❌ **Коммерческое использование** - только с разрешения автора

## 🙏 Благодарности

Этот проект основан на:
- [artesk/1copilot_MCP](https://github.com/artesk/1copilot_MCP) - оригинальный MCP сервер
- [rentgengl/copilot-1c-proxy](https://github.com/rentgengl/copilot-1c-proxy) - прокси-сервер

**Спасибо авторам за их вклад в сообщество!**

## 🆘 Поддержка

Если у вас возникли проблемы:

1. Проверьте [Issues](https://github.com/your-repo/issues)
2. Убедитесь, что токен 1С:Напарник действителен
3. Проверьте логи: `docker logs spring-mcp-1c-copilot`
4. Создайте новый Issue с подробным описанием

---

**🎉 Готово! Наслаждайтесь работой с 1С:Напарник через MCP!**
