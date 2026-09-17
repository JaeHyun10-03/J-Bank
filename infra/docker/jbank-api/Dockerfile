# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

COPY src ./src

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S jbank && adduser -S jbank -G jbank
COPY --from=build /workspace/build/libs/*.jar app.jar
USER jbank

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
