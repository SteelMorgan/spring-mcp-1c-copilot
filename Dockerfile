# Используем OpenJDK 17
FROM openjdk:17-jdk-slim

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем JAR файл
COPY build/libs/spring-mcp-1c-copilot-*.jar app.jar

# Указываем порт
EXPOSE 8000

# Определяем команду для запуска
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=sse"]
