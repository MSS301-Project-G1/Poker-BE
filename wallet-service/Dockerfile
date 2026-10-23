# ==========================================
# Dockerfile cho riêng Wallet Service
# ==========================================
# Multi-stage build: Tự động compile và đóng gói jar
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy toàn bộ source code vào workspace (đã có .dockerignore loại bỏ target/)
COPY . .

# Build jar cho riêng wallet-service
RUN mvn -B -pl wallet-service -am -DskipTests package

# Runtime stage: Chạy bằng JRE 21 siêu nhẹ
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/wallet-service/target/wallet-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]
