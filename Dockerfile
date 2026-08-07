# syntax=docker/dockerfile:1.7

ARG YT_DLP_VERSION=2026.07.04

FROM maven:3.9.11-eclipse-temurin-17 AS java-build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-noble AS runtime
ARG YT_DLP_VERSION

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl python3 python3-pip python3-venv \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/loquela
COPY exporter exporter
RUN python3 -m venv /opt/loquela/python \
    && /opt/loquela/python/bin/pip install --no-cache-dir ./exporter "yt-dlp==${YT_DLP_VERSION}" \
    && /opt/loquela/python/bin/python -c "import whatsanki_exporter" \
    && test "$(/opt/loquela/python/bin/yt-dlp --version)" = "${YT_DLP_VERSION}"

RUN groupadd --system --gid 10001 loquela \
    && useradd --system --uid 10001 --gid loquela --home-dir /opt/loquela --shell /usr/sbin/nologin loquela \
    && chown -R loquela:loquela /opt/loquela

COPY --from=java-build --chown=loquela:loquela /workspace/target/WhatsAnki-0.0.1-SNAPSHOT.jar app.jar

USER 10001:10001
ENV WHATSANKI_EXPORTER_PYTHON_EXECUTABLE=/opt/loquela/python/bin/python \
    WHATSANKI_EXPORTER_WORKING_DIRECTORY=/opt/loquela/exporter \
    WHATSANKI_YOUTUBE_EXECUTABLE=/opt/loquela/python/bin/yt-dlp \
    WHATSANKI_YOUTUBE_EXPECTED_VERSION=${YT_DLP_VERSION} \
    JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:${PORT:-8080}/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/opt/loquela/app.jar"]
