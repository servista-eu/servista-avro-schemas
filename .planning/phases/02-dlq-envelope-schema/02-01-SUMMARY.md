---
phase: 02-dlq-envelope-schema
plan: 01
subsystem: infra
tags: [avro, dlq, dead-letter-queue, kafka, schema]

# Dependency graph
requires:
  - phase: 01-avro-event-infrastructure
    provides: Avro codegen pipeline, schema directory conventions, namespace consistency test, schema discovery for Apicurio registration
provides:
  - DeadLetterEnvelope.avsc schema at eu.servista.schemas.avro.dlq.DeadLetterEnvelope
  - DlqStatus enum (PENDING, PERMANENTLY_FAILED) for reprocessing eligibility
  - dlq/ directory integrated into schema discovery and Apicurio group mapping
affects: [servista-commons DLQ routing, governance Phase 6 commons plans]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Infrastructure schema directories (envelope/, dlq/) use servista.{dir} Apicurio group (no .events suffix)"

key-files:
  created:
    - src/main/avro/dlq/DeadLetterEnvelope.avsc
  modified:
    - buildSrc/src/main/kotlin/eu/servista/gradle/AvroSchemaInfo.kt
    - src/test/kotlin/eu/servista/schemas/avro/SchemaDiscoveryTest.kt

key-decisions:
  - "DLQ-01: dlq/ directory maps to servista.dlq Apicurio group (same pattern as envelope/ -> servista.envelope)"

patterns-established:
  - "Infrastructure schemas (non-domain) use servista.{dir} group without .events suffix"

requirements-completed: [FOUND-05]

# Metrics
duration: 3min
completed: 2026-03-03
---

# Phase 2 Plan 1: DLQ Envelope Schema Summary

**DeadLetterEnvelope Avro schema with 15 error-metadata fields and DlqStatus enum for centralized DLQ routing**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-03T18:46:54Z
- **Completed:** 2026-03-03T18:50:15Z
- **Tasks:** 1
- **Files modified:** 3

## Accomplishments
- DeadLetterEnvelope.avsc created with all 15 fields wrapping failed message bytes with error metadata
- DlqStatus enum with PENDING and PERMANENTLY_FAILED symbols for reprocessing eligibility tracking
- Schema discovery and Apicurio group mapping updated to handle dlq/ directory alongside envelope/
- Generated eu.servista.schemas.avro.dlq.DeadLetterEnvelope Java class passes codegen + compilation + all tests

## Task Commits

Each task was committed atomically:

1. **Task 1: Create DeadLetterEnvelope Avro schema** - `f66b93b` (feat)

## Files Created/Modified
- `src/main/avro/dlq/DeadLetterEnvelope.avsc` - DLQ envelope Avro schema with 15 fields and DlqStatus enum
- `buildSrc/src/main/kotlin/eu/servista/gradle/AvroSchemaInfo.kt` - Updated discoverSchemas to map dlq/ to servista.dlq group
- `src/test/kotlin/eu/servista/schemas/avro/SchemaDiscoveryTest.kt` - Added DeadLetterEnvelope to discovery assertions and dlq group mapping test

## Decisions Made
- **DLQ-01:** The dlq/ directory maps to Apicurio group `servista.dlq` (no `.events` suffix), following the same pattern as `envelope/` -> `servista.envelope`. Infrastructure schemas are distinguished from domain event schemas by omitting the `.events` suffix.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Schema discovery did not account for dlq/ directory**
- **Found during:** Task 1 (Create DeadLetterEnvelope Avro schema)
- **Issue:** SchemaDiscoveryTest asserted exactly 3 schemas (EventEnvelope, AccountCreated, OrgCreated) via `shouldContainExactlyInAnyOrder`, failing when DeadLetterEnvelope was added. Also, the `groupForDomain` function in both buildSrc and test only special-cased `envelope/` directory, defaulting dlq/ to `servista.dlq.events` (incorrect).
- **Fix:** Updated `AvroSchemaInfo.kt` to treat `dlq` like `envelope` (mapping to `servista.dlq` group). Updated test to expect 4 schemas and added a dedicated `maps dlq directory to servista_dlq group` test case.
- **Files modified:** `buildSrc/src/main/kotlin/eu/servista/gradle/AvroSchemaInfo.kt`, `src/test/kotlin/eu/servista/schemas/avro/SchemaDiscoveryTest.kt`
- **Verification:** `./gradlew build -x detekt` passes (all 12 tests green)
- **Committed in:** f66b93b (part of task commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Auto-fix necessary for correctness -- schema discovery must include the new schema and map its directory correctly. No scope creep.

## Issues Encountered
- Pre-existing detekt 2.0 config issue (deferred from Phase 1) causes `:detekt` task to fail. Build verified with `-x detekt` flag. Not related to this plan's changes.
- `publishToMavenLocal` fails due to filesystem permissions on `~/.m2/repository`. This is a post-phase action noted in the plan, not a task requirement. Downstream consumers will need to resolve this independently.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- DeadLetterEnvelope class available at `eu.servista.schemas.avro.dlq.DeadLetterEnvelope` for downstream servista-commons DLQ routing
- `publishToMavenLocal` must succeed before servista-commons can import the class (filesystem permissions issue needs resolution)
- Ready for governance Phase 6 servista-commons plans (ServistaKafkaConsumer wrapper, DLQ routing)

## Self-Check: PASSED

All artifacts verified:
- FOUND: src/main/avro/dlq/DeadLetterEnvelope.avsc
- FOUND: 02-01-SUMMARY.md
- FOUND: commit f66b93b
- FOUND: build/generated-avro-java/eu/servista/schemas/avro/dlq/DeadLetterEnvelope.java
- FOUND: build/generated-avro-java/eu/servista/schemas/avro/dlq/DlqStatus.java

---
*Phase: 02-dlq-envelope-schema*
*Completed: 2026-03-03*
