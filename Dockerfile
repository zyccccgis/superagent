FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 ca-certificates docker.io \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /workspace/target/*.jar /app/super-biz-agent.jar

RUN mkdir -p /app/memory /app/skills /app/uploads /app/sandbox \
    && useradd --system --create-home --home-dir /home/app app \
    && chown -R app:app /app

USER app

EXPOSE 9900

ENV SERVER_PORT=9900 \
    MEMORY_BASE_PATH=/app/memory \
    SKILLS_BASE_PATH=/app/skills \
    FILE_UPLOAD_PATH=/app/uploads \
    SANDBOX_APP_ROOT=/app/sandbox \
    SANDBOX_PYTHON_IMAGE=superbiz-agent:latest \
    SANDBOX_PYTHON_ENTRYPOINT=python3 \
    SANDBOX_MOUNT_TYPE=volume \
    SANDBOX_VOLUME_NAME=superbiz-sandbox-data \
    SANDBOX_CONTAINER_ROOT=/sandbox \
    SANDBOX_NETWORK=none

ENTRYPOINT ["java", "-jar", "/app/super-biz-agent.jar"]
