# Runtime-only image for CI: the jar is built beforehand by `./mvnw package`, so building this image
# needs no Maven and no emulation. Build context is the module's target/ directory:
#   docker build -f deploy/Dockerfile --build-arg MODULE=auth-service auth-service/target
FROM eclipse-temurin:21-jre
WORKDIR /app
ARG MODULE
COPY ${MODULE}-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
