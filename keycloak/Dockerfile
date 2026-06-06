# ─── Stage 1: Build SPI JAR ────────────────────────────────────────────────
# Builds the tasks-webapi User Storage SPI JAR from the Gradle keycloak subproject.
FROM eclipse-temurin:21-jdk-jammy AS spi-builder

WORKDIR /workspace

# Copy Gradle wrapper and root build scripts first for layer caching.
# settings.gradle includes all monorepo subprojects; we create a minimal one
# that only declares the keycloak subproject so that webapi/ is not required.
COPY gradlew gradlew.bat build.gradle ./
COPY gradle/ gradle/
COPY keycloak/ keycloak/

RUN printf "rootProject.name = 'tasks-webapi-monorepo'\ninclude 'keycloak'\n" > settings.gradle && \
    chmod +x ./gradlew && \
    ./gradlew :keycloak:jar --no-daemon -x test

# ─── Stage 2: Keycloak optimized build (kc.sh build) ───────────────────────
# Installs the SPI JAR into Keycloak's providers/ directory and runs kc.sh build
# to produce an optimized Quarkus augmentation for MySQL. No DB connection is
# needed at this stage — kc.sh build only sets compile-time configuration.
FROM quay.io/keycloak/keycloak:26.2.5 AS kc-builder

COPY --from=spi-builder /workspace/keycloak/build/libs/*.jar /opt/keycloak/providers/

ENV KC_DB=mysql
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

RUN /opt/keycloak/bin/kc.sh build

# ─── Stage 3: Runtime ──────────────────────────────────────────────────────
# Final image: copies the optimized Keycloak installation from the builder stage.
# Dev image tag convention: v<major>.<minor>.<patch>-dev  (e.g. v0.1.0-dev)
# ECR push and CI/CD tagging are handled in #324 / S2Infra-5.
FROM quay.io/keycloak/keycloak:26.2.5

COPY --from=kc-builder /opt/keycloak/ /opt/keycloak/

# Runtime configuration is injected via environment variables at container start.
# Credentials must never be baked into the image.
#
# Required at runtime (prod: sourced from AWS Parameter Store / Secrets Manager):
#   KC_DB_URL               JDBC URL for Keycloak DB  (e.g. jdbc:mysql://host:3306/keycloak)
#   KC_DB_USERNAME          DB username
#   KC_DB_PASSWORD          DB password
#   KEYCLOAK_ADMIN          Admin console username     (first-boot bootstrap only)
#   KEYCLOAK_ADMIN_PASSWORD Admin console password     (first-boot bootstrap only)
#
# For local development override the command to: start-dev --import-realm
# (see docker-compose.local.yml).

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
