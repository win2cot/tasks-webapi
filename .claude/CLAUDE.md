# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

タスク管理システム (Task Management System) — a Spring Boot 4.0.0 / Java 21 Web API. Currently in early scaffold phase with no domain logic yet.

- **Package root**: `xyz.dgz48.tasks.webapi`
- **Server port**: 8080
- **Actuator endpoints**: `/actuator/health`, `/actuator/info`

## Commands

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# All checks (compile + format check + tests)
./gradlew check

# Tests only
./gradlew test

# Single test class or method
./gradlew test --tests "xyz.dgz48.tasks.webapi.TasksWebapiApplicationTests"
./gradlew test --tests "xyz.dgz48.tasks.webapi.TasksWebapiApplicationTests.contextLoads"

# Coverage report (HTML + XML at build/reports/jacoco/)
./gradlew jacocoTestReport

# Apply Google Java Format
./gradlew spotlessApply

# Check formatting only
./gradlew spotlessCheck
```

## Code Quality Toolchain

| Tool | Role | Notes |
|------|------|-------|
| **Spotless** | Formatting | Google Java Format v1.24.0; run `spotlessApply` before committing |
| **NullAway** | Null-safety | Applies to `xyz.dgz48.*`; disabled for tests |
| **JSpecify** | Null annotations | All packages use `@NullMarked` at package level |
| **Error Prone** | Static analysis | Runs at compile time |
| **JaCoCo** | Coverage | 80% minimum enforced in CI on overall and changed-file coverage |

## Null Safety

All code under `xyz.dgz48.*` must be null-safe. Each package requires a `package-info.java` annotated with `@NullMarked`. Parameters, return types, and fields are non-null by default; use `@Nullable` explicitly where null is permitted.

## CI/CD

GitHub Actions (`cicd.yml`) runs `./gradlew check` on every push and PR, publishes test results and JaCoCo coverage reports. Automated Claude Code reviews run on PRs via `claude-code-review.yml`. CI environment uses `LANG=ja_JP.UTF-8` and `TZ=Asia/Tokyo`.
