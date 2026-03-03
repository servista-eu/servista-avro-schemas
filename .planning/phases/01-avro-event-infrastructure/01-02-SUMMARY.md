---
phase: 01-avro-event-infrastructure
plan: 02
subsystem: infra
tags: [avro, apicurio, schema-registry, gradle, buildSrc, kotlin, rest-api, migration]

# Dependency graph
requires:
  - phase: 01-avro-event-infrastructure (plan 01)
    provides: Avro codegen pipeline, EventEnvelope/AccountCreated/OrgCreated schemas, domain directory structure
provides:
  - RegisterSchemasTask custom Gradle task for Apicurio Registry v3 schema registration
  - discoverSchemas() function mapping .avsc files to Apicurio groups (servista.envelope, servista.{domain}.events)
  - AvroSchemaInfo data class for schema-to-registry metadata
  - Group-level compatibility rule configuration (FULL for envelope, BACKWARD for domain events)
  - Compatibility-check-then-register workflow via Apicurio v3 dryRun API
  - lib-commons migration from local EventEnvelope.avsc to servista-avro-schemas dependency
affects: [lib-commons, all service repos producing/consuming events, CI pipeline (Phase 9)]

# Tech tracking
tech-stack:
  added: [kotlinx-serialization-json 1.10.0 (buildSrc), java.net.http.HttpClient (JDK 21)]
  patterns: [buildSrc custom Gradle task with Apicurio v3 REST API, schema directory-to-group mapping convention, includeBuild for local dependency resolution]

key-files:
  created:
    - buildSrc/build.gradle.kts
    - buildSrc/src/main/kotlin/eu/servista/gradle/AvroSchemaInfo.kt
    - buildSrc/src/main/kotlin/eu/servista/gradle/RegisterSchemasTask.kt
    - src/test/kotlin/eu/servista/schemas/avro/SchemaDiscoveryTest.kt
  modified:
    - build.gradle.kts
    - ../servista-commons/build.gradle.kts
    - ../servista-commons/settings.gradle.kts
    - ../servista-commons/src/main/kotlin/eu/servista/commons/event/EventEnvelopeSerializer.kt
    - ../servista-commons/src/main/kotlin/eu/servista/commons/event/EventEnvelopeDeserializer.kt
    - ../servista-commons/src/main/kotlin/eu/servista/commons/event/EventBuilder.kt
    - ../servista-commons/src/main/kotlin/eu/servista/commons/event/EventContextHandler.kt
    - ../servista-commons/src/test/kotlin/eu/servista/commons/event/EventBuilderTest.kt
    - ../servista-commons/src/test/kotlin/eu/servista/commons/event/EventEnvelopeSerDesTest.kt

key-decisions:
  - "Used kotlinx-serialization-json for JSON construction in buildSrc without the compiler plugin (only DSL functions like buildJsonObject needed)"
  - "Kotlin serialization plugin version omitted from buildSrc (Gradle 9.3.1 bundles Kotlin 2.2.21, serialization-json library works standalone for JSON DSL)"
  - "lib-commons EventEnvelope migration executed in-place since Phase 4 already completed (servista-commons exists on disk)"
  - "Removed avro-tools codegen entirely from servista-commons (EventEnvelope.avsc was the only schema, now consumed from servista-avro-schemas)"

patterns-established:
  - "Schema registration pattern: discoverSchemas() maps directories to Apicurio groups, RegisterSchemasTask handles group creation + rule config + artifact registration"
  - "Cross-repo dependency pattern: includeBuild('../servista-avro-schemas') in settings.gradle.kts enables local development while publishing resolves via Forgejo Maven"
  - "Apicurio v3 REST API pattern: /apis/registry/v3 base path, groups/{groupId}/artifacts for CRUD, dryRun=true for compatibility validation"

requirements-completed: [FOUND-04]

# Metrics
duration: 9min
completed: 2026-03-03
---

# Phase 01 Plan 02: RegisterSchemasTask + EventEnvelope Migration Summary

**Apicurio v3 schema registration Gradle task with directory-to-group mapping and lib-commons EventEnvelope migration to servista-avro-schemas**

## Performance

- **Duration:** 9 min
- **Started:** 2026-03-03T16:52:55Z
- **Completed:** 2026-03-03T17:02:10Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments
- RegisterSchemasTask in buildSrc implementing the full Apicurio v3 registration workflow: group creation, compatibility rule configuration (FULL/BACKWARD), artifact creation with first version, compatibility-check-then-register for existing artifacts
- Schema discovery function mapping envelope/ to servista.envelope group and {domain}/ to servista.{domain}.events groups, skipping empty directories
- lib-commons successfully migrated from owning EventEnvelope.avsc to consuming it from servista-avro-schemas via `implementation("eu.servista:servista-avro-schemas:0.1.0")`
- All imports in 6 source files (4 main + 2 test) updated from eu.servista.commons.event.avro to eu.servista.schemas.avro.envelope

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement RegisterSchemasTask in buildSrc with schema discovery and Apicurio REST API integration** - `6b9d674` (feat) [servista-avro-schemas]
2. **Task 2: Migrate lib-commons EventEnvelope ownership to servista-avro-schemas** - `cda6d4d` (refactor) [servista-commons]

## Files Created/Modified
- `buildSrc/build.gradle.kts` - buildSrc build config with kotlin-dsl and kotlinx-serialization-json
- `buildSrc/src/main/kotlin/eu/servista/gradle/AvroSchemaInfo.kt` - Data class + discoverSchemas() function
- `buildSrc/src/main/kotlin/eu/servista/gradle/RegisterSchemasTask.kt` - Custom Gradle task for Apicurio v3 registration
- `build.gradle.kts` - Added registerSchemas task registration with registryUrl and schemaDir properties
- `src/test/kotlin/eu/servista/schemas/avro/SchemaDiscoveryTest.kt` - Tests for directory-to-group mapping logic
- `../servista-commons/settings.gradle.kts` - Added includeBuild("../servista-avro-schemas")
- `../servista-commons/build.gradle.kts` - Removed avro-tools codegen, added servista-avro-schemas dependency
- `../servista-commons/src/main/avro/EventEnvelope.avsc` - DELETED (moved to servista-avro-schemas)
- `../servista-commons/src/main/kotlin/eu/servista/commons/event/EventEnvelopeSerializer.kt` - Import path updated
- `../servista-commons/src/main/kotlin/eu/servista/commons/event/EventEnvelopeDeserializer.kt` - Import path updated
- `../servista-commons/src/main/kotlin/eu/servista/commons/event/EventBuilder.kt` - Import path updated
- `../servista-commons/src/main/kotlin/eu/servista/commons/event/EventContextHandler.kt` - Import path updated
- `../servista-commons/src/test/kotlin/eu/servista/commons/event/EventBuilderTest.kt` - FQ references updated
- `../servista-commons/src/test/kotlin/eu/servista/commons/event/EventEnvelopeSerDesTest.kt` - Import path updated

## Decisions Made
- **buildSrc serialization approach:** Used kotlinx-serialization-json library without the compiler plugin. The `buildJsonObject`/`put`/`putJsonObject` DSL functions work standalone. Gradle 9.3.1 bundles Kotlin 2.2.21, so the serialization plugin version would need to match that specific version -- unnecessary complexity since the compiler plugin is not needed.
- **Full codegen removal from servista-commons:** Since EventEnvelope.avsc was the only schema in servista-commons, removed the entire avro-tools configuration and generateAvro task rather than leaving an empty codegen pipeline. The servista.avro plugin remains applied for its runtime dependencies (avro + apicurio-serdes).
- **Cross-repo includeBuild:** Added `includeBuild("../servista-avro-schemas")` to servista-commons settings.gradle.kts, matching the existing `includeBuild("../gradle-platform")` pattern for local development builds.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] buildSrc test classes not importable from main test source set**
- **Found during:** Task 1 (SchemaDiscoveryTest)
- **Issue:** Plan imports `eu.servista.gradle.AvroSchemaInfo` and `discoverSchemas` from buildSrc, but buildSrc classes are only on the build script classpath, not the project compilation classpath
- **Fix:** Rewrote SchemaDiscoveryTest to inline the mapping logic (same algorithm) and test it against the actual schema directory, as the plan's own note suggests
- **Files modified:** src/test/kotlin/eu/servista/schemas/avro/SchemaDiscoveryTest.kt
- **Verification:** All 5 tests pass
- **Committed in:** 6b9d674 (Task 1 commit)

**2. [Rule 1 - Bug] Fixed kotest shouldBeGreaterThanOrEqualTo import**
- **Found during:** Task 1 (SchemaDiscoveryTest compilation)
- **Issue:** `io.kotest.matchers.ints.shouldBeGreaterThanOrEqualTo` does not resolve in Kotest 6.x
- **Fix:** Replaced with `shouldHaveAtLeastSize` from `io.kotest.matchers.collections`
- **Files modified:** src/test/kotlin/eu/servista/schemas/avro/SchemaDiscoveryTest.kt
- **Verification:** Test compiles and passes
- **Committed in:** 6b9d674 (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both auto-fixes address compilation issues in the test file. No scope change or architectural impact.

## Issues Encountered
- Pre-existing detekt 2.0 configuration issue continues (same as Plan 01). Build runs with `-x detekt`. Not related to this plan's changes.
- Two integration tests in servista-commons (ValkeyWorkerLeaseIntegrationTest, RlsSessionManagerIntegrationTest) fail due to missing infrastructure (Redis/PostgreSQL containers). Pre-existing, unrelated to EventEnvelope migration. All event-related tests pass.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 1 complete: servista-avro-schemas project has working codegen pipeline, schema discovery tests, namespace/compatibility validation, and Apicurio registration task
- lib-commons now consumes EventEnvelope from servista-avro-schemas (single source of truth)
- `./gradlew registerSchemas -PregistryUrl=http://...` ready for CI integration (Phase 9)
- Domain directories ready for future service phases to add event schemas

## Self-Check: PASSED

- All 5 created files verified on disk
- servista-commons EventEnvelope.avsc confirmed deleted
- Commit 6b9d674 (Task 1) found in servista-avro-schemas
- Commit cda6d4d (Task 2) found in servista-commons

---
*Phase: 01-avro-event-infrastructure*
*Completed: 2026-03-03*
