ARG JAR_SOURCE=build
FROM maven:3.9-eclipse-temurin-17 AS build
# O contexto é somente leitura; o cache de dependências não entra na imagem.
# Uma camada de build evita duplicar todo o JDK em drivers sem copy-on-write.
RUN --mount=type=bind,target=/source,readonly \
    --mount=type=cache,target=/root/.m2 \
    mkdir -p /tmp/build \
    && cp /source/pom.xml /tmp/build/pom.xml \
    && cp -R /source/src /tmp/build/src \
    && mvn --batch-mode --no-transfer-progress -f /tmp/build/pom.xml verify

FROM scratch AS prebuilt
COPY target/hydroguard-citizen-1.0.0.jar /tmp/build/target/app.jar

FROM ${JAR_SOURCE} AS artifact

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --user-group appuser \
    && mkdir -p /app/uploads \
    && chown appuser:appuser /app/uploads
WORKDIR /app
COPY --from=artifact --chown=appuser:appuser /tmp/build/target/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail --silent --output /dev/null http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
