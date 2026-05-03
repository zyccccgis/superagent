FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /workspace/target/*.jar /app/super-biz-agent.jar

RUN mkdir -p /app/memory /app/skills /app/uploads \
    && useradd --system --create-home --home-dir /home/app app \
    && chown -R app:app /app

USER app

EXPOSE 9900

ENV SERVER_PORT=9900 \
    MEMORY_BASE_PATH=/app/memory \
    SKILLS_BASE_PATH=/app/skills \
    FILE_UPLOAD_PATH=/app/uploads

ENTRYPOINT ["java", "-jar", "/app/super-biz-agent.jar"]
