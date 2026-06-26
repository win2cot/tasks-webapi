# ─── Stage 1: Build SPI JAR ────────────────────────────────────────────────
# Builds the tasks-webapi User Storage SPI JAR from the Gradle keycloak subproject.
FROM eclipse-temurin:21.0.11_10-jdk-jammy AS spi-builder

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
FROM quay.io/keycloak/keycloak:26.6.4 AS kc-builder

COPY --from=spi-builder /workspace/keycloak/build/libs/*.jar /opt/keycloak/providers/

ENV KC_DB=mysql
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

RUN /opt/keycloak/bin/kc.sh build

# ─── Stage 3: Runtime ──────────────────────────────────────────────────────
# Final image: copies the optimized Keycloak installation from the builder stage.
# Dev image tag convention: v<major>.<minor>.<patch>-dev  (e.g. v0.1.0-dev)
# ECR push and CI/CD tagging are handled in #324 / S2Infra-5.
FROM quay.io/keycloak/keycloak:26.6.4

COPY --from=kc-builder /opt/keycloak/ /opt/keycloak/

# Bake realm export JSON for --import-realm.
# Keycloak imports from /opt/keycloak/data/import/ at startup (IGNORE_EXISTING by default).
# For local development the same directory is volume-mounted via docker-compose.local.yml,
# overriding this layer. See docs/dev/keycloak-realm-import.md for full import procedure.
COPY keycloak/realm-export/ /opt/keycloak/data/import/

# Bake the custom admin theme (tasks-admin) referenced by the realm's adminTheme.
# It carries the i18n message bundle for the User Storage SPI config labels and the
# Custom User Profile attribute (full_name_kana) display names (#729). Themes are
# read from /opt/keycloak/themes/ at startup, so no kc.sh build step is required.
COPY keycloak/themes/ /opt/keycloak/themes/

# Runtime configuration is injected via environment variables at container start.
# Production service credentials (DB password, admin password) must never be baked
# into the image — they are sourced from AWS Parameter Store / Secrets Manager at runtime.
# The realm-export above contains dev/local test fixture users only; these are
# intentionally baked into dev/local images for development convenience and must
# not be used in production.
#
# Required at runtime (prod: sourced from AWS Parameter Store / Secrets Manager):
#   KC_DB_URL               JDBC URL for Keycloak DB  (e.g. jdbc:mysql://host:3306/keycloak)
#   KC_DB_USERNAME          DB username
#   KC_DB_PASSWORD          DB password
#   KEYCLOAK_ADMIN          Admin console username     (first-boot bootstrap only)
#   KEYCLOAK_ADMIN_PASSWORD Admin console password     (first-boot bootstrap only)

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
# --import-realm: idempotent import from /opt/keycloak/data/import/ (IGNORE_EXISTING on re-boot).
# Local development overrides this CMD with: start-dev --import-realm (docker-compose.local.yml).
CMD ["start", "--optimized", "--import-realm"]
