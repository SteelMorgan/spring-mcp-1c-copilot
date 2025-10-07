# 🚀 Руководство по развертыванию Spring Boot MCP Server

Это руководство описывает различные способы развертывания Spring Boot MCP Server для 1С:Напарник на серверах.

## 📋 Содержание

- [Требования](#требования)
- [Подготовка](#подготовка)
- [Docker развертывание](#docker-развертывание)
- [Docker Compose развертывание](#docker-compose-развертывание)
- [Kubernetes развертывание](#kubernetes-развертывание)
- [Nginx reverse proxy](#nginx-reverse-proxy)
- [SSL/TLS настройка](#ssltls-настройка)
- [Мониторинг и логирование](#мониторинг-и-логирование)
- [Безопасность](#безопасность)
- [Резервное копирование](#резервное-копирование)

## 🔧 Требования

### Минимальные требования сервера
- **CPU**: 1 vCPU
- **RAM**: 512 MB
- **Диск**: 1 GB свободного места
- **Сеть**: Доступ к интернету для API 1С:Напарник

### Рекомендуемые требования
- **CPU**: 2 vCPU
- **RAM**: 1 GB
- **Диск**: 5 GB SSD
- **Сеть**: Стабильное соединение с низкой задержкой

### Программное обеспечение
- Docker 20.10+ (для Docker развертывания)
- Docker Compose 2.0+ (для Docker Compose)
- kubectl (для Kubernetes)
- Nginx (для reverse proxy)

## 🛠️ Подготовка

### 1. Получение токена 1С:Напарник

```bash
# Зарегистрируйтесь на https://code.1c.ai
# Получите токен в личном кабинете
export ONEC_AI_TOKEN="your_actual_token_here"
```

### 2. Клонирование репозитория

```bash
git clone <repository-url>
cd spring-mcp-1c-copilot
```

### 3. Проверка файлов

```bash
# Убедитесь, что все необходимые файлы присутствуют
ls -la
# Должны быть: Dockerfile.build, docker-compose.yml, README.md
```

## 🐳 Docker развертывание

### Простое развертывание

```bash
# 1. Сборка образа
docker build -f Dockerfile.build -t spring-mcp-1c-copilot .

# 2. Запуск контейнера
docker run -d \
  --name spring-mcp-1c-copilot \
  --restart unless-stopped \
  -p 8000:8000 \
  -e ONEC_AI_TOKEN="${ONEC_AI_TOKEN}" \
  -e ONEC_AI_BASE_URL="https://code.1c.ai" \
  -e ONEC_AI_TIMEOUT="30" \
  spring-mcp-1c-copilot

# 3. Проверка статуса
docker ps | grep spring-mcp-1c-copilot
docker logs spring-mcp-1c-copilot
```

### Развертывание с volume для логов

```bash
# Создание директории для логов
sudo mkdir -p /var/log/spring-mcp-1c-copilot

# Запуск с volume
docker run -d \
  --name spring-mcp-1c-copilot \
  --restart unless-stopped \
  -p 8000:8000 \
  -v /var/log/spring-mcp-1c-copilot:/app/logs \
  -e ONEC_AI_TOKEN="${ONEC_AI_TOKEN}" \
  -e SPRING_PROFILES_ACTIVE="prod" \
  spring-mcp-1c-copilot
```

### Обновление контейнера

```bash
# 1. Остановка старого контейнера
docker stop spring-mcp-1c-copilot
docker rm spring-mcp-1c-copilot

# 2. Сборка нового образа
docker build -f Dockerfile.build -t spring-mcp-1c-copilot .

# 3. Запуск нового контейнера
docker run -d \
  --name spring-mcp-1c-copilot \
  --restart unless-stopped \
  -p 8000:8000 \
  -e ONEC_AI_TOKEN="${ONEC_AI_TOKEN}" \
  spring-mcp-1c-copilot
```

## 🐙 Docker Compose развертывание

### Создание docker-compose.yml

```yaml
version: '3.8'

services:
  spring-mcp-1c-copilot:
    build:
      context: .
      dockerfile: Dockerfile.build
    container_name: spring-mcp-1c-copilot
    restart: unless-stopped
    ports:
      - "8000:8000"
    environment:
      - ONEC_AI_TOKEN=${ONEC_AI_TOKEN}
      - ONEC_AI_BASE_URL=https://code.1c.ai
      - ONEC_AI_TIMEOUT=30
      - SPRING_PROFILES_ACTIVE=prod
    volumes:
      - ./logs:/app/logs
    networks:
      - mcp-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  nginx:
    image: nginx:alpine
    container_name: mcp-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
    depends_on:
      - spring-mcp-1c-copilot
    networks:
      - mcp-network

networks:
  mcp-network:
    driver: bridge

volumes:
  logs:
    driver: local
```

### Запуск с Docker Compose

```bash
# 1. Создание .env файла
cat > .env << EOF
ONEC_AI_TOKEN=your_actual_token_here
EOF

# 2. Запуск сервисов
docker-compose up -d

# 3. Проверка статуса
docker-compose ps
docker-compose logs -f spring-mcp-1c-copilot
```

### Обновление с Docker Compose

```bash
# 1. Остановка сервисов
docker-compose down

# 2. Пересборка и запуск
docker-compose up -d --build

# 3. Очистка неиспользуемых образов
docker system prune -f
```

## ☸️ Kubernetes развертывание

### Создание namespace

```bash
kubectl create namespace mcp-server
```

### ConfigMap для конфигурации

```yaml
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: mcp-server-config
  namespace: mcp-server
data:
  ONEC_AI_BASE_URL: "https://code.1c.ai"
  ONEC_AI_TIMEOUT: "30"
  SPRING_PROFILES_ACTIVE: "prod"
```

### Secret для токена

```yaml
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: mcp-server-secret
  namespace: mcp-server
type: Opaque
data:
  ONEC_AI_TOKEN: <base64-encoded-token>
```

### Deployment

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-mcp-1c-copilot
  namespace: mcp-server
spec:
  replicas: 2
  selector:
    matchLabels:
      app: spring-mcp-1c-copilot
  template:
    metadata:
      labels:
        app: spring-mcp-1c-copilot
    spec:
      containers:
      - name: spring-mcp-1c-copilot
        image: spring-mcp-1c-copilot:latest
        ports:
        - containerPort: 8000
        env:
        - name: ONEC_AI_TOKEN
          valueFrom:
            secretKeyRef:
              name: mcp-server-secret
              key: ONEC_AI_TOKEN
        - name: ONEC_AI_BASE_URL
          valueFrom:
            configMapKeyRef:
              name: mcp-server-config
              key: ONEC_AI_BASE_URL
        - name: ONEC_AI_TIMEOUT
          valueFrom:
            configMapKeyRef:
              name: mcp-server-config
              key: ONEC_AI_TIMEOUT
        livenessProbe:
          httpGet:
            path: /api/health
            port: 8000
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /api/health
            port: 8000
          initialDelaySeconds: 30
          periodSeconds: 10
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

### Service

```yaml
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: spring-mcp-1c-copilot-service
  namespace: mcp-server
spec:
  selector:
    app: spring-mcp-1c-copilot
  ports:
  - port: 80
    targetPort: 8000
  type: ClusterIP
```

### Ingress

```yaml
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: mcp-server-ingress
  namespace: mcp-server
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - mcp.yourdomain.com
    secretName: mcp-server-tls
  rules:
  - host: mcp.yourdomain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: spring-mcp-1c-copilot-service
            port:
              number: 80
```

### Развертывание в Kubernetes

```bash
# 1. Применение манифестов
kubectl apply -f configmap.yaml
kubectl apply -f secret.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
kubectl apply -f ingress.yaml

# 2. Проверка статуса
kubectl get pods -n mcp-server
kubectl get services -n mcp-server
kubectl get ingress -n mcp-server

# 3. Просмотр логов
kubectl logs -f deployment/spring-mcp-1c-copilot -n mcp-server
```

## 🌐 Nginx reverse proxy

### Конфигурация Nginx

```nginx
# nginx.conf
events {
    worker_connections 1024;
}

http {
    upstream mcp_backend {
        server spring-mcp-1c-copilot:8000;
    }

    server {
        listen 80;
        server_name mcp.yourdomain.com;

        # Redirect HTTP to HTTPS
        return 301 https://$server_name$request_uri;
    }

    server {
        listen 443 ssl http2;
        server_name mcp.yourdomain.com;

        # SSL Configuration
        ssl_certificate /etc/nginx/ssl/cert.pem;
        ssl_certificate_key /etc/nginx/ssl/key.pem;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;
        ssl_prefer_server_ciphers off;

        # Security headers
        add_header X-Frame-Options DENY;
        add_header X-Content-Type-Options nosniff;
        add_header X-XSS-Protection "1; mode=block";
        add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

        # Rate limiting
        limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
        limit_req zone=api burst=20 nodelay;

        location / {
            proxy_pass http://mcp_backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            
            # Timeouts
            proxy_connect_timeout 30s;
            proxy_send_timeout 30s;
            proxy_read_timeout 30s;
        }

        # Health check endpoint
        location /api/health {
            proxy_pass http://mcp_backend/api/health;
            access_log off;
        }

        # Swagger UI
        location /swagger-ui.html {
            proxy_pass http://mcp_backend/swagger-ui.html;
        }
    }
}
```

## 🔒 SSL/TLS настройка

### Let's Encrypt с Certbot

```bash
# 1. Установка Certbot
sudo apt update
sudo apt install certbot python3-certbot-nginx

# 2. Получение сертификата
sudo certbot --nginx -d mcp.yourdomain.com

# 3. Автоматическое обновление
sudo crontab -e
# Добавить: 0 12 * * * /usr/bin/certbot renew --quiet
```

### Самоподписанный сертификат (для тестирования)

```bash
# 1. Создание директории
mkdir -p ssl

# 2. Генерация сертификата
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout ssl/key.pem \
  -out ssl/cert.pem \
  -subj "/C=RU/ST=Moscow/L=Moscow/O=Company/CN=mcp.yourdomain.com"
```

## 📊 Мониторинг и логирование

### Prometheus метрики

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'mcp-server'
    static_configs:
      - targets: ['spring-mcp-1c-copilot:8000']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
```

### Grafana дашборд

```json
{
  "dashboard": {
    "title": "MCP Server Dashboard",
    "panels": [
      {
        "title": "Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_requests_total[5m])",
            "legendFormat": "{{method}} {{uri}}"
          }
        ]
      },
      {
        "title": "Response Time",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))",
            "legendFormat": "95th percentile"
          }
        ]
      }
    ]
  }
}
```

### Логирование

```yaml
# logback-spring.xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/app/logs/mcp-server.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/app/logs/mcp-server.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>3GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

## 🔐 Безопасность

### Firewall настройка

```bash
# UFW (Ubuntu)
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable

# iptables
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT
sudo iptables -A INPUT -j DROP
```

### Docker security

```bash
# Запуск с ограниченными правами
docker run -d \
  --name spring-mcp-1c-copilot \
  --user 1000:1000 \
  --read-only \
  --tmpfs /tmp \
  --tmpfs /app/logs \
  --security-opt no-new-privileges:true \
  -p 8000:8000 \
  -e ONEC_AI_TOKEN="${ONEC_AI_TOKEN}" \
  spring-mcp-1c-copilot
```

### Переменные окружения

```bash
# Создание .env файла с ограниченными правами
chmod 600 .env
echo "ONEC_AI_TOKEN=your_token_here" > .env
```

## 💾 Резервное копирование

### Скрипт резервного копирования

```bash
#!/bin/bash
# backup.sh

BACKUP_DIR="/backup/mcp-server"
DATE=$(date +%Y%m%d_%H%M%S)

# Создание директории
mkdir -p $BACKUP_DIR

# Резервное копирование логов
docker cp spring-mcp-1c-copilot:/app/logs $BACKUP_DIR/logs_$DATE

# Резервное копирование конфигурации
cp docker-compose.yml $BACKUP_DIR/docker-compose.yml_$DATE
cp nginx.conf $BACKUP_DIR/nginx.conf_$DATE

# Сжатие архива
tar -czf $BACKUP_DIR/mcp-server-backup_$DATE.tar.gz -C $BACKUP_DIR logs_$DATE docker-compose.yml_$DATE nginx.conf_$DATE

# Удаление временных файлов
rm -rf $BACKUP_DIR/logs_$DATE $BACKUP_DIR/docker-compose.yml_$DATE $BACKUP_DIR/nginx.conf_$DATE

# Удаление старых бэкапов (старше 30 дней)
find $BACKUP_DIR -name "*.tar.gz" -mtime +30 -delete

echo "Backup completed: mcp-server-backup_$DATE.tar.gz"
```

### Автоматическое резервное копирование

```bash
# Добавление в crontab
crontab -e

# Ежедневное резервное копирование в 2:00
0 2 * * * /path/to/backup.sh
```

## 🚨 Устранение неполадок

### Проверка статуса

```bash
# Docker
docker ps | grep spring-mcp-1c-copilot
docker logs spring-mcp-1c-copilot --tail 100

# Kubernetes
kubectl get pods -n mcp-server
kubectl logs -f deployment/spring-mcp-1c-copilot -n mcp-server

# Проверка API
curl -f http://localhost:8000/api/health
```

### Частые проблемы

1. **Контейнер не запускается**
   ```bash
   # Проверка логов
   docker logs spring-mcp-1c-copilot
   
   # Проверка переменных окружения
   docker exec spring-mcp-1c-copilot env | grep ONEC_AI
   ```

2. **API недоступен**
   ```bash
   # Проверка портов
   netstat -tlnp | grep 8000
   
   # Проверка firewall
   sudo ufw status
   ```

3. **Ошибки авторизации**
   ```bash
   # Проверка токена
   echo $ONEC_AI_TOKEN
   
   # Тест API 1С:Напарник
   curl -H "Authorization: Bearer $ONEC_AI_TOKEN" https://code.1c.ai/api/health
   ```

## 📞 Поддержка

При возникновении проблем:

1. Проверьте логи: `docker logs spring-mcp-1c-copilot`
2. Убедитесь в правильности токена 1С:Напарник
3. Проверьте сетевое соединение
4. Создайте Issue в репозитории с подробным описанием

## 📄 Лицензия

**Важно:** Этот проект предназначен для личного и некоммерческого использования.

- ✅ **Личное использование** - свободно
- ✅ **Некоммерческое использование** - свободно  
- ✅ **Образовательные цели** - свободно
- ❌ **Коммерческое использование** - только с разрешения автора

Для коммерческого использования свяжитесь с автором проекта.

## 🙏 Благодарности

Этот проект основан на:
- [artesk/1copilot_MCP](https://github.com/artesk/1copilot_MCP) - оригинальный MCP сервер
- [rentgengl/copilot-1c-proxy](https://github.com/rentgengl/copilot-1c-proxy) - прокси-сервер

**Спасибо авторам за их вклад в сообщество!**

---

**🎉 Успешного развертывания!**
