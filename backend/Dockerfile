FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

ENV GRADLE_OPTS="-Dorg.gradle.vfs.watch=false -Xmx1024m -Dorg.gradle.daemon=false -Dkotlin.compiler.execution.strategy=in-process"

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# ffmpeg 설치 (동영상 썸네일 생성용)
RUN apk add --no-cache ffmpeg

RUN mkdir -p /data/media/images /data/media/videos /data/media/thumbnails

COPY --from=builder /app/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
