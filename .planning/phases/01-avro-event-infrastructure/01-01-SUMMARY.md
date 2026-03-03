---
phase: 01-avro-event-infrastructure
plan: 01
subsystem: infra
tags: [avro, codegen, schema-registry, gradle, maven-publish, kotlin, avro-tools]

# Dependency graph
requires:
  - phase: gradle-platform (governance Phase 3)
    provides: convention plugins (servista.library, servista.avro, servista.testing), version catalog
provides:
  - Avro codegen pipeline generating Java classes from .avsc schemas
  - EventEnvelope schema (eu.servista.schemas.avro.envelope) with FULL compatibility
  - AccountCreated and OrgCreated example payload schemas with BACKWARD compatibility
  - Namespace consistency test enforcing convention across all schemas
  - Compatibility test verifying BACKWARD (domain) and FULL (envelope) evolution rules
  - Maven publishing configuration for eu.servista:servista-avro-schemas artifact
  - 10 domain directories (envelope, accounts, organizations, iam, authorization, integrations, usage, subscriptions, billing, egress)
affects: [01-02-PLAN, lib-commons EventEnvelope migration, all service repos consuming event schemas]

# Tech tracking
tech-stack:
  added: [avro-tools 1.12.1 (codegen), kotlinx-serialization-json 1.10.0 (test), junit-platform-launcher]
  patterns: [avro-tools JavaExec codegen task, .avsc file discovery via walkTopDown, domain-organized schema directories]

key-files:
  created:
    - build.gradle.kts
    - settings.gradle.kts
    - gradle.properties
    - src/main/avro/envelope/EventEnvelope.avsc
    - src/main/avro/accounts/AccountCreated.avsc
    - src/main/avro/organizations/OrgCreated.avsc
    - src/test/kotlin/eu/servista/schemas/avro/SchemaNamespaceConsistencyTest.kt
    - src/test/kotlin/eu/servista/schemas/avro/SchemaCompatibilityTest.kt
  modified: []

key-decisions:
  - "Used file discovery (walkTopDown) to pass individual .avsc files to avro-tools instead of parent directory (avro-tools does not recurse into subdirectories)"
  - "Excluded trevni-avro and trevni-core from avroTools configuration (missing test jar artifacts in Maven Central for 1.12.1)"
  - "Fixed backward compatibility test: adding a required field without default is incompatible, not removing a required field (which Avro allows since reader ignores extra fields)"
  - "Excluded testcontainers postgresql/kafka modules from testImplementation (pre-existing TC 2.x coordinate issue in convention plugin)"
  - "Added JUnit Platform launcher as testRuntimeOnly (required by Gradle 9.x for JUnit 5 test execution)"

patterns-established:
  - "Avro codegen pattern: avroTools configuration with trevni exclusions, JavaExec task with doFirst file discovery, sourceSets registration"
  - "Schema naming convention: namespace eu.servista.schemas.avro.{domain} must match parent directory name, record name must match filename"
  - "Compatibility test pattern: use Avro SchemaCompatibility API for build-time compatibility validation without running Apicurio"

requirements-completed: [FOUND-04]

# Metrics
duration: 9min
completed: 2026-03-03
---

# Phase 01 Plan 01: Project Scaffold Summary

**Avro schema repository with EventEnvelope, AccountCreated, OrgCreated codegen, namespace consistency tests, and BACKWARD/FULL compatibility validation**

## Performance

- **Duration:** 9 min
- **Started:** 2026-03-03T16:39:17Z
- **Completed:** 2026-03-03T16:48:52Z
- **Tasks:** 2
- **Files modified:** 23

## Accomplishments
- Avro codegen pipeline generating Java classes for EventEnvelope, AccountCreated, and OrgCreated in correct packages (eu.servista.schemas.avro.{domain})
- SchemaNamespaceConsistencyTest enforcing namespace-directory-filename convention across all schemas
- SchemaCompatibilityTest validating BACKWARD compatibility for domain payloads and FULL compatibility for the envelope schema
- Maven publishing configured for Forgejo registry as eu.servista:servista-avro-schemas
- All 10 domain directories created (7 empty with .gitkeep, 3 with schemas)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create project scaffold with build config and Avro schemas** - `14eb368` (feat)
2. **Task 2: Schema validation and compatibility tests** - `e8fcfb2` (test)

## Files Created/Modified
- `build.gradle.kts` - Project build configuration with servista plugins, Avro codegen task, maven-publish
- `settings.gradle.kts` - Plugin resolution via gradle-platform includeBuild, version catalog import
- `gradle.properties` - Group (eu.servista) and version (0.1.0)
- `.gitignore` - Excludes build/, .gradle/, IDE files
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*` - Gradle 9.3.1 wrapper (copied from gradle-platform)
- `src/main/avro/envelope/EventEnvelope.avsc` - Platform event envelope schema (9 fields, FULL compatibility)
- `src/main/avro/accounts/AccountCreated.avsc` - Account creation event payload (5 fields)
- `src/main/avro/organizations/OrgCreated.avsc` - Organization creation event payload (5 fields)
- `src/main/avro/{iam,authorization,integrations,usage,subscriptions,billing,egress}/.gitkeep` - Empty domain directories
- `src/main/kotlin/.gitkeep`, `src/main/resources/.gitkeep` - Required by servista.library plugin
- `src/test/kotlin/eu/servista/schemas/avro/SchemaNamespaceConsistencyTest.kt` - Namespace convention enforcement
- `src/test/kotlin/eu/servista/schemas/avro/SchemaCompatibilityTest.kt` - Schema evolution compatibility rules

## Decisions Made
- **Avro-tools file discovery:** avro-tools `compile schema` does not recurse into subdirectories. Changed the generateAvro task to use `walkTopDown()` to collect individual .avsc files and pass them as separate arguments.
- **Trevni exclusion:** avro-tools 1.12.1 declares transitive dependencies on trevni-avro/trevni-core test JARs that are not published to Maven Central. Excluded from avroTools configuration.
- **Backward compatibility semantics correction:** The plan specified testing "removing a required field is NOT backward compatible" but in Avro semantics, removing a field IS backward compatible (reader ignores extra data). Changed to "adding a required field without default" which correctly demonstrates backward incompatibility.
- **Testcontainers workaround:** The servista.testing convention plugin declares TC 2.0.3 postgresql/kafka modules that don't exist at those coordinates in TC 2.x. Excluded from testImplementation as they're not needed for this project.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added repositories block for dependency resolution**
- **Found during:** Task 1 (build configuration)
- **Issue:** Convention plugins don't declare repositories; avroTools configuration couldn't resolve org.apache.avro:avro-tools
- **Fix:** Added `repositories { mavenCentral() }` to build.gradle.kts
- **Files modified:** build.gradle.kts
- **Verification:** Dependency resolved, codegen task runs
- **Committed in:** 14eb368 (Task 1 commit)

**2. [Rule 3 - Blocking] Excluded trevni test JARs from avroTools**
- **Found during:** Task 1 (build configuration)
- **Issue:** avro-tools 1.12.1 depends on trevni-avro-1.12.1-tests.jar and trevni-core-1.12.1-tests.jar which are not published to Maven Central
- **Fix:** Added exclude rules for trevni-avro and trevni-core on the avroTools configuration
- **Files modified:** build.gradle.kts
- **Verification:** avroTools classpath resolves, codegen runs successfully
- **Committed in:** 14eb368 (Task 1 commit)

**3. [Rule 3 - Blocking] Changed avro-tools invocation to pass individual .avsc files**
- **Found during:** Task 1 (build configuration)
- **Issue:** avro-tools `compile schema` with a parent directory containing only subdirectories reports "No input files found" (does not recurse)
- **Fix:** Changed generateAvro task to use `walkTopDown()` file discovery in `doFirst {}` block, passing individual .avsc file paths as arguments
- **Files modified:** build.gradle.kts
- **Verification:** Codegen generates all 3 Java classes correctly
- **Committed in:** 14eb368 (Task 1 commit)

**4. [Rule 1 - Bug] Fixed backward compatibility test assertion**
- **Found during:** Task 2 (compatibility tests)
- **Issue:** Plan specified testing "removing a required field is NOT backward compatible" but in Avro, removing a field from the reader IS backward compatible (reader ignores extra writer fields). Test was asserting INCOMPATIBLE but result was COMPATIBLE.
- **Fix:** Changed test to "adding a required field without default is NOT backward compatible" -- new reader (v2) with required `phone_number` cannot read old data (v1) that lacks it
- **Files modified:** SchemaCompatibilityTest.kt
- **Verification:** All 6 tests pass
- **Committed in:** e8fcfb2 (Task 2 commit)

**5. [Rule 3 - Blocking] Excluded testcontainers modules with invalid TC 2.x coordinates**
- **Found during:** Task 2 (test compilation)
- **Issue:** servista.testing plugin declares `org.testcontainers:postgresql:2.0.3` and `org.testcontainers:kafka:2.0.3` but these modules no longer exist at those coordinates in Testcontainers 2.x
- **Fix:** Added exclude rules on configurations.testImplementation for postgresql and kafka modules
- **Files modified:** build.gradle.kts
- **Verification:** Test compilation succeeds
- **Committed in:** e8fcfb2 (Task 2 commit)

**6. [Rule 3 - Blocking] Added JUnit Platform launcher for Gradle 9.x**
- **Found during:** Task 2 (test execution)
- **Issue:** Gradle 9.x requires junit-platform-launcher on the test runtime classpath, which servista.testing plugin does not declare
- **Fix:** Added `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` to dependencies
- **Files modified:** build.gradle.kts
- **Verification:** Tests execute successfully
- **Committed in:** e8fcfb2 (Task 2 commit)

---

**Total deviations:** 6 auto-fixed (1 bug fix, 5 blocking issues)
**Impact on plan:** All auto-fixes were necessary for build correctness. Two issues (testcontainers coordinates, detekt 2.0 config) are pre-existing convention plugin problems logged in deferred-items.md. No scope creep.

## Issues Encountered
- Detekt 2.0.0-alpha.2 configuration in gradle-platform uses property names incompatible with detekt 2.0 (e.g., `complexity>ComplexMethod`, `emptyblocks`). This is a pre-existing issue in the convention plugin. Build passes with `-x detekt`. Logged as deferred item.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Project scaffold complete with working codegen pipeline, ready for Plan 02 (RegisterSchemasTask for Apicurio)
- All 10 domain directories exist, ready for future phases to add domain-specific schemas
- Two pre-existing convention plugin issues (detekt config, testcontainers coordinates) should be fixed in gradle-platform before widespread adoption

---
*Phase: 01-avro-event-infrastructure*
*Completed: 2026-03-03*
