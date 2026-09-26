FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG MODULE
COPY . .
RUN mvn -B -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG MODULE
COPY --from=build /workspace/${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
