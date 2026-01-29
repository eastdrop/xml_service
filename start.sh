#!/bin/bash

echo "=== XML DB Service Docker Setup ==="

# 1. Сборка проекта
echo "1. Building project..."
mvn clean package -DskipTests

# 2. Сборка Docker образа
echo "2. Building Docker image..."
docker build -t xml-db-service .

# 3. Остановка старых контейнеров
echo "3. Stopping old containers..."
docker-compose down

# 4. Запуск новых контейнеров
echo "4. Starting containers..."
docker-compose up -d

# 5. Ожидание запуска
echo "5. Waiting for services to start..."
sleep 10

# 6. Проверка
echo "6. Checking services..."
docker ps
echo ""
echo "Application URL: http://localhost:8080"
echo "Database: localhost:5432 (user: postgres, password: postgres123)"
echo "API Documentation: http://localhost:8080/swagger-ui.html"
echo ""
echo "To view logs: docker-compose logs -f"
echo "To stop: docker-compose down"