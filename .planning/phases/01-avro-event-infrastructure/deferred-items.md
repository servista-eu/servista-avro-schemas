# Deferred Items - Phase 01

## Pre-existing Issues (Out of Scope)

### 1. Detekt 2.0 config property names in gradle-platform
- **Found during:** 01-01 Task 2
- **Issue:** The shared `detekt.yml` from the gradle-platform convention plugin uses property names incompatible with detekt 2.0.0-alpha.2 (e.g., `complexity>ComplexMethod`, `emptyblocks`, `potentialbugs`). These were renamed in detekt 2.0.
- **Impact:** `./gradlew build` fails at `:detekt` task; workaround is `-x detekt`
- **Fix location:** `gradle-platform/src/main/resources/detekt/detekt.yml`
- **Workaround:** Skip detekt with `-x detekt` flag

### 2. Testcontainers 2.x module coordinates changed
- **Found during:** 01-01 Task 2
- **Issue:** `servista.testing` plugin declares `org.testcontainers:postgresql:2.0.3` and `org.testcontainers:kafka:2.0.3` but these modules no longer exist at those coordinates in TC 2.x
- **Impact:** Test compilation fails unless these modules are excluded
- **Fix location:** `gradle-platform/src/main/kotlin/servista.testing.gradle.kts`
- **Workaround:** Exclude in consumer project's `configurations.testImplementation`
