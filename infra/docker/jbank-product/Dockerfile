# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
# gradle.properties의 org.gradle.java.home은 로컬 macOS 개발 편의용 경로라
# 컨테이너 안에는 존재하지 않는다. 빌드 이미지 자체가 이미 JDK 21이므로 제거한다.
RUN sed -i '/org.gradle.java.home/d' gradle.properties
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
