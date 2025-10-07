# Скрипт для развертывания Spring Boot MCP Server
# Использование: .\deploy.ps1 [dev|prod]

param(
    [Parameter(Position=0)]
    [ValidateSet("dev", "prod")]
    [string]$Environment = "dev"
)

# Функции для логирования
function Write-Log {
    param([string]$Message)
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $Message" -ForegroundColor Blue
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

# Проверка зависимостей
function Test-Dependencies {
    Write-Log "Проверяем зависимости..."
    
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Error "Docker не установлен"
        exit 1
    }
    
    if (-not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
        Write-Error "Docker Compose не установлен"
        exit 1
    }
    
    Write-Success "Все зависимости установлены"
}

# Проверка переменных окружения
function Test-Environment {
    Write-Log "Проверяем переменные окружения..."
    
    if (-not $env:ONEC_AI_TOKEN) {
        Write-Error "Переменная ONEC_AI_TOKEN не установлена"
        Write-Log "Установите токен: `$env:ONEC_AI_TOKEN = 'your_token_here'"
        exit 1
    }
    
    Write-Success "Переменные окружения настроены"
}

# Создание необходимых директорий
function New-Directories {
    Write-Log "Создаем необходимые директории..."
    
    $directories = @(
        "logs",
        "backup", 
        "nginx\ssl",
        "monitoring\grafana\dashboards",
        "monitoring\grafana\datasources"
    )
    
    foreach ($dir in $directories) {
        if (-not (Test-Path $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
        }
    }
    
    Write-Success "Директории созданы"
}

# Создание конфигурационных файлов
function New-Configs {
    Write-Log "Создаем конфигурационные файлы..."
    
    # Nginx конфигурация
    if (-not (Test-Path "nginx\nginx.conf")) {
        $nginxConfig = @"
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
            proxy_set_header Host `$host;
            proxy_set_header X-Real-IP `$remote_addr;
            proxy_set_header X-Forwarded-For `$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto `$scheme;
        }
    }
}
"@
        $nginxConfig | Out-File -FilePath "nginx\nginx.conf" -Encoding UTF8
        Write-Success "Nginx конфигурация создана"
    }
    
    # Prometheus конфигурация
    if (-not (Test-Path "monitoring\prometheus.yml")) {
        $prometheusConfig = @"
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'mcp-server'
    static_configs:
      - targets: ['spring-mcp-1c-copilot:8000']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
"@
        $prometheusConfig | Out-File -FilePath "monitoring\prometheus.yml" -Encoding UTF8
        Write-Success "Prometheus конфигурация создана"
    }
    
    # Grafana datasource
    if (-not (Test-Path "monitoring\grafana\datasources\prometheus.yml")) {
        $grafanaConfig = @"
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
"@
        $grafanaConfig | Out-File -FilePath "monitoring\grafana\datasources\prometheus.yml" -Encoding UTF8
        Write-Success "Grafana datasource создан"
    }
}

# Сборка и запуск
function Start-Deployment {
    Write-Log "Собираем и запускаем сервисы..."
    
    if ($Environment -eq "prod") {
        docker-compose -f docker-compose.prod.yml up -d --build
    } else {
        docker-compose up -d --build
    }
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Сервисы запущены"
    } else {
        Write-Error "Ошибка при запуске сервисов"
        exit 1
    }
}

# Проверка здоровья сервисов
function Test-Health {
    Write-Log "Проверяем здоровье сервисов..."
    
    # Ждем запуска сервисов
    Start-Sleep -Seconds 30
    
    # Проверяем основной сервис
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8000/api/health" -TimeoutSec 10
        if ($response.StatusCode -eq 200) {
            Write-Success "MCP сервер работает"
        } else {
            Write-Error "MCP сервер недоступен"
            docker logs spring-mcp-1c-copilot
            exit 1
        }
    } catch {
        Write-Error "MCP сервер недоступен: $($_.Exception.Message)"
        docker logs spring-mcp-1c-copilot
        exit 1
    }
    
    # Проверяем Nginx (если в prod режиме)
    if ($Environment -eq "prod") {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost/" -TimeoutSec 10
            if ($response.StatusCode -eq 200) {
                Write-Success "Nginx работает"
            } else {
                Write-Warning "Nginx недоступен"
            }
        } catch {
            Write-Warning "Nginx недоступен: $($_.Exception.Message)"
        }
    }
}

# Показать информацию о развертывании
function Show-Info {
    Write-Log "Информация о развертывании:"
    Write-Host ""
    Write-Host "🌐 MCP Server: http://localhost:8000" -ForegroundColor Cyan
    Write-Host "📚 Swagger UI: http://localhost:8000/swagger-ui.html" -ForegroundColor Cyan
    Write-Host "❤️  Health Check: http://localhost:8000/api/health" -ForegroundColor Cyan
    
    if ($Environment -eq "prod") {
        Write-Host ""
        Write-Host "📊 Мониторинг:" -ForegroundColor Cyan
        Write-Host "   Prometheus: http://localhost:9090" -ForegroundColor Cyan
        Write-Host "   Grafana: http://localhost:3000 (admin/admin)" -ForegroundColor Cyan
        Write-Host "   Loki: http://localhost:3100" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "🌐 Nginx: http://localhost" -ForegroundColor Cyan
    }
    
    Write-Host ""
    Write-Host "📋 Полезные команды:" -ForegroundColor Cyan
    Write-Host "   Просмотр логов: docker logs spring-mcp-1c-copilot -f" -ForegroundColor White
    Write-Host "   Остановка: docker-compose down" -ForegroundColor White
    Write-Host "   Перезапуск: docker-compose restart" -ForegroundColor White
    Write-Host ""
}

# Очистка
function Clear-Unused {
    Write-Log "Очищаем неиспользуемые образы..."
    docker system prune -f
    Write-Success "Очистка завершена"
}

# Основная функция
function Main {
    Write-Log "🚀 Развертывание Spring Boot MCP Server"
    Write-Host ""
    
    Test-Dependencies
    Test-Environment
    New-Directories
    New-Configs
    Start-Deployment
    Test-Health
    Show-Info
    Clear-Unused
    
    Write-Success "🎉 Развертывание завершено успешно!"
}

# Обработка ошибок
$ErrorActionPreference = "Stop"

try {
    Main
} catch {
    Write-Error "Развертывание прервано: $($_.Exception.Message)"
    exit 1
}
