# syntax=docker/dockerfile:1.7-labs
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY api-gateway/pom.xml .
RUN mvn -B dependency:go-offline

COPY api-gateway/src ./src
RUN mvn -B -DskipTests clean package && \
    mv target/*.jar target/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /workspace/target/app.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
