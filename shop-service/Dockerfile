# ==========================================
# Dockerfile cho riêng Shop Service
# ==========================================
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy toàn bộ source code vào workspace (đã có .dockerignore loại bỏ target/)
COPY . .

# Build jar cho riêng shop-service
RUN mvn -B -pl shop-service -am -DskipTests package

# Runtime stage: Chạy bằng JRE 21 siêu nhẹ
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/shop-service/target/shop-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
