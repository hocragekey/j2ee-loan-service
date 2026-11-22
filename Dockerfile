# syntax=docker/dockerfile:1.7
#
# Container runtime for the Spring Boot 3 executable WAR (Phase 3: DEL-08, PLAT-01, PLAT-08).
#   build   - Maven 3.9 + Amazon Corretto 21 compiles, tests and packages loan-service.war
#   runtime - Amazon Corretto 21 (headless) on Amazon Linux 2023 running the WAR with its
#             embedded Tomcat 10.1 on :8080, context path /loan-service, as a non-root user
#
# Base images are pinned by tag AND multi-arch index digest. The digest is identical on
# Docker Hub, mirror.gcr.io and public.ecr.aws/docker/library, so BASE_REGISTRY can point
# at a mirror or an ECR pull-through cache without loosening the pin.

ARG BASE_REGISTRY=docker.io/library

FROM ${BASE_REGISTRY}/maven:3.9.16-amazoncorretto-21-al2023@sha256:efdf660ec74911594e2a7f642e177ffba21b741c8e848223bf41a6d0fc367692 AS build

# Optional Maven Central mirror (public URL, not a secret), e.g.
#   --build-arg MAVEN_MIRROR_URL=https://maven-central.storage-download.googleapis.com/maven2/
ARG MAVEN_MIRROR_URL=""

WORKDIR /build
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
      mkdir -p /root/.m2 && \
      printf '<settings><mirrors><mirror><id>central-mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>\n' \
        "$MAVEN_MIRROR_URL" > /root/.m2/settings.xml; \
    fi

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp package


FROM ${BASE_REGISTRY}/amazoncorretto:21.0.13-al2023-headless@sha256:5085e890043da2b63af83b0fa7259b90e6687f867db2deed628f1b68c798cadb

ARG APP_UID=10001
ARG APP_GID=10001

# Base image has no useradd; register the unprivileged user directly.
RUN echo "app:x:${APP_GID}:" >> /etc/group && \
    echo "app:x:${APP_UID}:${APP_GID}:loan-service:/app:/sbin/nologin" >> /etc/passwd && \
    mkdir -p /app /app/tmp && chown -R "${APP_UID}:${APP_GID}" /app/tmp

# The WAR stays root-owned and read-only to the app user; only /app/tmp is writable
# (embedded Tomcat work dir + compiled JSPs).
COPY --from=build /build/target/loan-service.war /app/loan-service.war

# JVM sizing follows the container memory limit. Runtime configuration comes from
# environment variables (PLAT-06): SERVER_PORT, SPRING_DATASOURCE_URL, LOAN_REGION_ID, ...
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/app/tmp"

WORKDIR /app
USER ${APP_UID}:${APP_GID}
EXPOSE 8080

# No /health endpoint exists yet; probe the JSP landing page (same path as the ECS/ALB
# health check). Switch to /health/live and /health/ready when Phase 5 adds them (RES-01).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS -o /dev/null "http://127.0.0.1:${SERVER_PORT:-8080}/loan-service/" || exit 1

ENTRYPOINT ["java", "-jar", "/app/loan-service.war"]
