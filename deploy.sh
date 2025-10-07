#!/bin/bash

# Скрипт для развертывания Spring Boot MCP Server
# Использование: ./deploy.sh [dev|prod]

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функция для логирования
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Проверка аргументов
ENVIRONMENT=${1:-dev}

if [[ "$ENVIRONMENT" != "dev" && "$ENVIRONMENT" != "prod" ]]; then
    error "Неверный аргумент. Используйте: dev или prod"
    exit 1
fi

log "Начинаем развертывание в режиме: $ENVIRONMENT"

# Проверка зависимостей
check_dependencies() {
    log "Проверяем зависимости..."
    
    if ! command -v docker &> /dev/null; then
        error "Docker не установлен"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose не установлен"
        exit 1
    fi
    
    success "Все зависимости установлены"
}

# Проверка переменных окружения
check_environment() {
    log "Проверяем переменные окружения..."
    
    if [[ -z "$ONEC_AI_TOKEN" ]]; then
        error "Переменная ONEC_AI_TOKEN не установлена"
        log "Установите токен: export ONEC_AI_TOKEN='your_token_here'"
        exit 1
    fi
    
    success "Переменные окружения настроены"
}

# Создание необходимых директорий
create_directories() {
    log "Создаем необходимые директории..."
    
    mkdir -p logs
    mkdir -p backup
    mkdir -p nginx/ssl
    mkdir -p monitoring/grafana/dashboards
    mkdir -p monitoring/grafana/datasources
    
    success "Директории созданы"
}

# Создание конфигурационных файлов
create_configs() {
    log "Создаем конфигурационные файлы..."
    
    # Nginx конфигурация
    if [[ ! -f "nginx/nginx.conf" ]]; then
        cat > nginx/nginx.conf << 'EOF'
events {
    worker_connections 1024;
}

http {
    upstream mcp_backend {
        server spring-mcp-1c-copilot:8000;
    }

    server {
        listen 80;
        server_name _;

        location / {
            proxy_pass http://mcp_backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
EOF
        success "Nginx конфигурация создана"
    fi
    
    # Prometheus конфигурация
    if [[ ! -f "monitoring/prometheus.yml" ]]; then
        cat > monitoring/prometheus.yml << 'EOF'
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'mcp-server'
    static_configs:
      - targets: ['spring-mcp-1c-copilot:8000']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
EOF
        success "Prometheus конфигурация создана"
    fi
    
    # Grafana datasource
    if [[ ! -f "monitoring/grafana/datasources/prometheus.yml" ]]; then
        cat > monitoring/grafana/datasources/prometheus.yml << 'EOF'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
EOF
        success "Grafana datasource создан"
    fi
}

# Сборка и запуск
deploy() {
    log "Собираем и запускаем сервисы..."
    
    if [[ "$ENVIRONMENT" == "prod" ]]; then
        docker-compose -f docker-compose.prod.yml up -d --build
    else
        docker-compose up -d --build
    fi
    
    success "Сервисы запущены"
}

# Проверка здоровья сервисов
health_check() {
    log "Проверяем здоровье сервисов..."
    
    # Ждем запуска сервисов
    sleep 30
    
    # Проверяем основной сервис
    if curl -f http://localhost:8000/api/health > /dev/null 2>&1; then
        success "MCP сервер работает"
    else
        error "MCP сервер недоступен"
        docker logs spring-mcp-1c-copilot
        exit 1
    fi
    
    # Проверяем Nginx (если в prod режиме)
    if [[ "$ENVIRONMENT" == "prod" ]]; then
        if curl -f http://localhost/ > /dev/null 2>&1; then
            success "Nginx работает"
        else
            warning "Nginx недоступен"
        fi
    fi
}

# Показать информацию о развертывании
show_info() {
    log "Информация о развертывании:"
    echo ""
    echo "🌐 MCP Server: http://localhost:8000"
    echo "📚 Swagger UI: http://localhost:8000/swagger-ui.html"
    echo "❤️  Health Check: http://localhost:8000/api/health"
    
    if [[ "$ENVIRONMENT" == "prod" ]]; then
        echo ""
        echo "📊 Мониторинг:"
        echo "   Prometheus: http://localhost:9090"
        echo "   Grafana: http://localhost:3000 (admin/admin)"
        echo "   Loki: http://localhost:3100"
        echo ""
        echo "🌐 Nginx: http://localhost"
    fi
    
    echo ""
    echo "📋 Полезные команды:"
    echo "   Просмотр логов: docker logs spring-mcp-1c-copilot -f"
    echo "   Остановка: docker-compose down"
    echo "   Перезапуск: docker-compose restart"
    echo ""
}

# Очистка
cleanup() {
    log "Очищаем неиспользуемые образы..."
    docker system prune -f
    success "Очистка завершена"
}

# Основная функция
main() {
    log "🚀 Развертывание Spring Boot MCP Server"
    echo ""
    
    check_dependencies
    check_environment
    create_directories
    create_configs
    deploy
    health_check
    show_info
    cleanup
    
    success "🎉 Развертывание завершено успешно!"
}

# Обработка сигналов
trap 'error "Развертывание прервано"; exit 1' INT TERM

# Запуск
main "$@"
