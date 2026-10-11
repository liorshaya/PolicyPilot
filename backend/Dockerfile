# PolicyPilot backend image (Document 5, Supply Chain: multi-stage, pinned images referenced by digest,
# non-root runtime user, no tools beyond the base image). Built once in CI stage 6, scanned by Trivy.

# ---- build stage -------------------------------------------------------------------------------
# maven:3.9.16-eclipse-temurin-21-noble, digest checked on 2026-09-18
FROM maven:3.9.16-eclipse-temurin-21-noble@sha256:c2a2c58516d160f43b50f12baa427ca86989e0bc942609e04aff61da5d9a7d74 AS build
WORKDIR /workspace

# Resolve dependencies first so that source changes do not invalidate the dependency layer. The layer itself is
# the cache: no BuildKit cache mount, because Railway accepts one only with a service-specific id, and the same
# Dockerfile has to build unchanged on Railway, in CI and locally.
COPY pom.xml ./
RUN mvn -B -ntp -q dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -q -DskipTests package \
    && java -Djarmode=tools -jar target/application.jar extract --layers --destination extracted

# ---- runtime stage -----------------------------------------------------------------------------
# eclipse-temurin:21-jre-noble, digest checked on 2026-09-18
FROM eclipse-temurin:21-jre-noble@sha256:000fd431958bc81a24abe1e8e5f0f0fd3ae365a594bd50aadb20696805f9408c AS runtime

# Ubuntu's fix for CVE-2026-84782 (openssl 3.0.13-0ubuntu3.16, 2026-09-29), which the base image above predates and
# stage 6's scan refuses: the two packages move to their fixed version and nothing else is added. Remove once a
# 21-jre-noble digest carries it (CLAUDE.md, Open values and known tensions).
RUN apt-get update \
    && apt-get install -y --no-install-recommends --only-upgrade libssl3t64 openssl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app

COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app
EXPOSE 8080
# JVM options go on the command line rather than in JAVA_TOOL_OPTIONS, whose "Picked up" notice would be the only
# non-JSON line in the cloud log (Document 5, Logs). JAVA_TOOL_OPTIONS stays free for a one-off override.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "application.jar"]
