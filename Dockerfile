# syntax=docker/dockerfile:1.7
#
# Interim container runtime for the current WAR (Phase 7: DEL-08, PLAT-01, PLAT-08).
#   build   - Maven 3.9 + Amazon Corretto 8 compiles, tests and packages loan-service.war
#   tomcat  - downloads Apache Tomcat 9 and verifies its SHA-512 before unpacking
#   runtime - Amazon Corretto 8 JRE on Amazon Linux 2023 + Tomcat 9, WAR at /loan-service,
#             runs as a non-root user
#
# Base images are pinned by tag AND multi-arch index digest. The digest is identical on
# Docker Hub, mirror.gcr.io and public.ecr.aws/docker/library, so BASE_REGISTRY can point
# at a mirror or an ECR pull-through cache without loosening the pin.

ARG BASE_REGISTRY=docker.io/library

FROM ${BASE_REGISTRY}/maven:3.9.16-amazoncorretto-8-al2023@sha256:52fd13f93704912f90017cfa4c6a082ce649bdd56051b1b150eeef01281be6c7 AS build

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


FROM ${BASE_REGISTRY}/maven:3.9.16-amazoncorretto-8-al2023@sha256:52fd13f93704912f90017cfa4c6a082ce649bdd56051b1b150eeef01281be6c7 AS tomcat

ARG TOMCAT_VERSION=9.0.124
ARG TOMCAT_SHA512=925e4fefe25b2ecccd032acd0b4cb1b506455037ed0f8b0e51824bed839c5e92bf5e58982b990d2fdcbc790bcda1a533db7faffb325f8a7baee939fc8ae871c1
ARG APP_UID=10001
ARG APP_GID=10001

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# - Verify the tarball against the pinned SHA-512 (published by Apache next to the release).
# - Drop Tomcat's sample apps (ROOT, manager, host-manager, docs, examples).
# - Disable the shutdown port; the container is stopped with SIGTERM.
# - Only webapps/, work/, temp/, logs/ and conf/Catalina/ are writable by the app user;
#   bin/, lib/ and the rest of conf/ stay root-owned and read-only to it (the tarball
#   ships conf/ as 0600 and bin/*.sh as 0750, so open read/execute for the app user).
WORKDIR /opt
RUN curl -fsSL -o tomcat.tar.gz \
      "https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz" && \
    echo "${TOMCAT_SHA512}  tomcat.tar.gz" | sha512sum -c - && \
    mkdir tomcat && tar -xzf tomcat.tar.gz -C tomcat --strip-components=1 && rm tomcat.tar.gz && \
    rm -rf tomcat/webapps/* tomcat/bin/*.bat && \
    sed -i 's/<Server port="8005"/<Server port="-1"/' tomcat/conf/server.xml && \
    mkdir -p tomcat/conf/Catalina/localhost && \
    chmod -R a+rX,go-w tomcat && \
    chown -R "${APP_UID}:${APP_GID}" tomcat/webapps tomcat/work tomcat/temp tomcat/logs tomcat/conf/Catalina


FROM ${BASE_REGISTRY}/amazoncorretto:8u514-al2023-jre@sha256:e5df5bb4a1b9906b32f0f166c2f8730fe1d0481165616922815b856abc63afb5

ARG APP_UID=10001
ARG APP_GID=10001

ENV CATALINA_HOME=/usr/local/tomcat
ENV PATH="${CATALINA_HOME}/bin:${PATH}"

# Base image has no useradd; register the unprivileged user directly.
RUN echo "tomcat:x:${APP_GID}:" >> /etc/group && \
    echo "tomcat:x:${APP_UID}:${APP_GID}:Tomcat:${CATALINA_HOME}:/sbin/nologin" >> /etc/passwd

COPY --from=tomcat /opt/tomcat ${CATALINA_HOME}
COPY --from=build --chown=${APP_UID}:${APP_GID} /build/target/loan-service.war ${CATALINA_HOME}/webapps/loan-service.war

# JVM sizing follows the container memory limit (Corretto 8u191+ container support).
# Override at run time with -e CATALINA_OPTS=... if needed.
ENV CATALINA_OPTS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

WORKDIR ${CATALINA_HOME}
USER ${APP_UID}:${APP_GID}
EXPOSE 8080

# No /health endpoint exists yet; probe the JSP landing page. Switch to /health/live
# (container) and /health/ready (ALB target group) when Phase 5 adds them (RES-01).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS -o /dev/null http://127.0.0.1:8080/loan-service/ || exit 1

CMD ["catalina.sh", "run"]
