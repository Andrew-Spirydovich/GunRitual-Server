# ---------- Этап 1: сборка (если ты собираешь проект внутри контейнера)
# Если у тебя уже есть готовый jar, можешь пропустить этот блок
# FROM maven:3.9.9-eclipse-temurin-21 AS builder
# WORKDIR /app
# COPY . .
# RUN mvn clean package -DskipTests

# ---------- Этап 2: запуск
FROM eclipse-temurin:21-jdk-jammy

# Задаём рабочую директорию внутри контейнера
WORKDIR /opt/app

# Копируем jar-файл (переименуем для простоты)
COPY target/GunRitual-Server-0.0.1-SNAPSHOT.jar app.jar

# Указываем порт, на котором работает сервер (если, например, Spring Boot)
EXPOSE 8080

# Используем ENTRYPOINT, чтобы Docker корректно обрабатывал сигналы (Ctrl+C, рестарт и т.д.)
ENTRYPOINT ["java", "-jar", "app.jar"]