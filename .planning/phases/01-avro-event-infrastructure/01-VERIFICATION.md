---
phase: 01-avro-event-infrastructure
verified: 2026-03-03T18:00:00Z
status: passed
score: 6/6 must-haves verified
re_verification: false
---

# Phase 01: Avro Event Infrastructure Verification Report

**Phase Goal:** A `servista-avro-schemas` repository exists with the EventEnvelope schema (moved from lib-commons), example payload schemas, Avro codegen, Maven publishing, Apicurio registration task, and compatibility tests
**Verified:** 2026-03-03T18:00:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #   | Truth | Status | Evidence |
| --- | ----- | ------ | -------- |
| 1   | EventEnvelope.avsc generates a Java class at eu.servista.schemas.avro.envelope.EventEnvelope | VERIFIED | `build/generated-avro-java/eu/servista/schemas/avro/envelope/EventEnvelope.java` exists on disk |
| 2   | AccountCreated.avsc generates a Java class at eu.servista.schemas.avro.accounts.AccountCreated | VERIFIED | `build/generated-avro-java/eu/servista/schemas/avro/accounts/AccountCreated.java` exists on disk |
| 3   | OrgCreated.avsc generates a Java class at eu.servista.schemas.avro.organizations.OrgCreated | VERIFIED | `build/generated-avro-java/eu/servista/schemas/avro/organizations/OrgCreated.java` exists on disk |
| 4   | All schema namespaces match their parent directory name (enforced by test) | VERIFIED | `SchemaNamespaceConsistencyTest` passes: 2 tests, 0 failures. Each schema's `namespace` field verified against parent directory name at runtime |
| 5   | Adding a nullable field to AccountCreated is BACKWARD compatible (enforced by test) | VERIFIED | `SchemaCompatibilityTest` passes: 4 tests, 0 failures. Test `AccountCreated adding nullable field is BACKWARD compatible` explicitly asserts COMPATIBLE result |
| 6   | `./gradlew build` passes end-to-end (codegen + compilation + tests) | VERIFIED | Test result XMLs show 11 tests total (4 + 5 + 2), 0 failures, 0 errors, timestamp 2026-03-03. Compiled class files present in `build/classes/kotlin/test/` |

**Score:** 6/6 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `build.gradle.kts` | Project config with servista plugins, Avro codegen, maven-publish | VERIFIED | Applies `servista.library`, `servista.avro`, `servista.testing`, `maven-publish`; `generateAvro` JavaExec task with `walkTopDown` file discovery; `registerSchemas` task wired; Forgejo publish repository |
| `settings.gradle.kts` | Plugin resolution from gradle-platform, version catalog import | VERIFIED | `pluginManagement { includeBuild("../gradle-platform") }`; version catalog created from `../gradle-platform/catalog/libs.versions.toml` |
| `gradle.properties` | `group=eu.servista`, `version=0.1.0` | VERIFIED | Both properties present exactly as specified |
| `src/main/avro/envelope/EventEnvelope.avsc` | EventEnvelope schema, namespace `eu.servista.schemas.avro.envelope`, 9 fields | VERIFIED | Namespace correct; 9 fields present (event_id, event_type, aggregate_id, organization_id, account_id, timestamp, correlation_id, causation_id, payload) |
| `src/main/avro/accounts/AccountCreated.avsc` | AccountCreated schema, namespace `eu.servista.schemas.avro.accounts`, 5 fields | VERIFIED | Namespace correct; 5 fields present |
| `src/main/avro/organizations/OrgCreated.avsc` | OrgCreated schema, namespace `eu.servista.schemas.avro.organizations`, 5 fields | VERIFIED | Namespace correct; 5 fields present |
| `src/test/kotlin/eu/servista/schemas/avro/SchemaNamespaceConsistencyTest.kt` | Test enforcing namespace matches directory name convention | VERIFIED | 2 tests: namespace-directory match, record name-filename match. Uses `kotlinx-serialization-json` to parse .avsc files. Test result XML confirms 0 failures |
| `src/test/kotlin/eu/servista/schemas/avro/SchemaCompatibilityTest.kt` | Test verifying BACKWARD/FULL compatibility rules | VERIFIED | 4 tests: nullable field BACKWARD compatible, required-field-without-default NOT BACKWARD compatible, envelope FULL compatible, envelope removing required field NOT FULL compatible. Test result XML confirms 0 failures |
| `buildSrc/src/main/kotlin/eu/servista/gradle/RegisterSchemasTask.kt` | Custom Gradle task for Apicurio Registry v3 registration | VERIFIED | Full implementation: group creation, compatibility rule configuration (FULL/BACKWARD), artifact creation, dryRun compatibility check, version registration. Fails fast if `registryUrl` is blank |
| `buildSrc/src/main/kotlin/eu/servista/gradle/AvroSchemaInfo.kt` | Data class + `discoverSchemas()` function | VERIFIED | `AvroSchemaInfo` data class with group, artifactId, file, content. `discoverSchemas()` uses `walkTopDown`, maps `envelope/` to `servista.envelope`, `{domain}/` to `servista.{domain}.events` |
| `buildSrc/build.gradle.kts` | buildSrc build config with kotlin-dsl and kotlinx-serialization-json | VERIFIED | `kotlin-dsl` plugin applied; `kotlinx-serialization-json:1.10.0` as implementation dependency |
| `src/test/kotlin/eu/servista/schemas/avro/SchemaDiscoveryTest.kt` | Tests for schema file discovery and Apicurio group mapping logic | VERIFIED | 5 tests covering schema discovery, envelope group mapping, domain group mapping, artifactId convention, subdirectory constraint. All passing |

**Empty domain directories:** All 7 required `.gitkeep` directories exist under `src/main/avro/` (iam, authorization, integrations, usage, subscriptions, billing, egress). Plus `src/main/kotlin/.gitkeep` and `src/main/resources/.gitkeep`.

---

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `build.gradle.kts` | `../gradle-platform` convention plugins | `plugins { id("servista.library"); id("servista.avro"); id("servista.testing") }` | WIRED | All three plugin IDs present. `settings.gradle.kts` resolves via `pluginManagement { includeBuild("../gradle-platform") }` |
| `settings.gradle.kts` | `../gradle-platform/catalog/libs.versions.toml` | `dependencyResolutionManagement versionCatalogs` | WIRED | `from(files("../gradle-platform/catalog/libs.versions.toml"))` present |
| `generateAvro` task | `src/main/avro/**/*.avsc` | `walkTopDown().filter { it.extension == "avsc" }` in `doFirst` block | WIRED | File discovery inside `doFirst` passes individual `.avsc` files to `avro-tools compile schema`. Generated Java classes confirmed present in `build/generated-avro-java/` |
| `RegisterSchemasTask` | Apicurio Registry v3 REST API | `java.net.http.HttpClient` calling `/apis/registry/v3` endpoints | WIRED | `HttpClient.newHttpClient()` used; `$apiBase/apis/registry/v3` path construction; POST/PUT/GET requests to all required endpoints |
| `RegisterSchemasTask` | `src/main/avro/**/*.avsc` | `discoverSchemas(schemaDir.get().asFile)` via `walkTopDown` | WIRED | `schemaDir.set(file("src/main/avro"))` in task registration; `discoverSchemas()` called in `@TaskAction` |
| `../servista-commons/build.gradle.kts` | `servista-avro-schemas` | `implementation("eu.servista:servista-avro-schemas:0.1.0")` | WIRED | Dependency present at line 43; `includeBuild("../servista-avro-schemas")` in servista-commons `settings.gradle.kts` |
| `lib-commons EventEnvelopeSerializer` | `eu.servista.schemas.avro.envelope.EventEnvelope` | `import eu.servista.schemas.avro.envelope.EventEnvelope` | WIRED | All 4 main source files + 2 test files in servista-commons use the new import path. Old `eu.servista.commons.event.avro` namespace is absent. `src/main/avro/EventEnvelope.avsc` deleted from servista-commons |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| FOUND-04 | 01-01-PLAN, 01-02-PLAN | Avro event infrastructure — centralized schema repository with EventEnvelope and per-domain payload schemas, Avro codegen, Maven publishing, Apicurio Registry registration with FULL/BACKWARD compatibility rules | SATISFIED | Schemas exist with correct namespaces; codegen generates Java classes; maven-publish configured for Forgejo; `registerSchemas` task implements full Apicurio v3 workflow with FULL/BACKWARD group-level rules; compatibility tests enforced at build time |

No orphaned requirements — FOUND-04 is the only requirement mapped to Phase 1 in REQUIREMENTS.md and it is claimed by both plans.

---

### Anti-Patterns Found

No anti-patterns found. Scan of `src/`, `buildSrc/`, and `build.gradle.kts` returned zero results for TODO, FIXME, XXX, HACK, PLACEHOLDER, placeholder, empty implementations, or console.log-only handlers.

---

### Notable Deviations from Plan (Documented, Not Blocking)

These were auto-fixed by the executor and documented in the summaries. None affect goal achievement:

1. **Avro-tools directory recursion:** Plan specified passing the parent `src/main/avro` directory to `avro-tools compile schema`. avro-tools does not recurse into subdirectories. Fix: `doFirst` block with `walkTopDown()` collecting individual `.avsc` files. Confirmed working — generated classes present.

2. **Compatibility test semantics correction:** Plan specified testing "removing a required field is NOT backward compatible." In Avro semantics, removing a field IS backward compatible (reader ignores extra writer data). The test was changed to "adding a required field without default is NOT backward compatible." Success criterion SC-5 ("Adding a nullable field is BACKWARD compatible") is still correctly tested by the first test case. The second test case tests a related but corrected incompatibility scenario.

3. **Testcontainers 2.x coordinate exclusion:** `servista.testing` convention plugin declares postgresql/kafka TC modules that do not exist at TC 2.x coordinates. Excluded from `configurations.testImplementation`. No functional impact.

4. **JUnit Platform launcher:** Gradle 9.x requires `junit-platform-launcher` on the test runtime classpath. Added as `testRuntimeOnly`. No functional impact.

5. **Trevni exclusion:** `avro-tools:1.12.1` declares transitive dependencies on unpublished test JARs. Excluded from `avroTools` configuration. No functional impact.

---

### Human Verification Required

#### 1. `./gradlew registerSchemas` fails with clear error (no Apicurio running)

**Test:** Run `./gradlew registerSchemas --no-daemon` without `-PregistryUrl`
**Expected:** Build fails with message containing "registryUrl is required" and "Do NOT register against production without explicit intent."
**Why human:** Cannot run Gradle in this verification context, but source code at `RegisterSchemasTask.kt:27-31` shows the exact check: `if (baseUrl.isBlank()) throw GradleException(...)`. Code is substantive, not a stub.

#### 2. `./gradlew registerSchemas -PregistryUrl=http://localhost:8080` with a running Apicurio instance

**Test:** Start Apicurio Registry locally and run the full registration workflow
**Expected:** Schemas registered in groups `servista.envelope` (FULL) and `servista.accounts.events`, `servista.organizations.events` (BACKWARD). Subsequent runs with identical schemas succeed (idempotent). Submitting an incompatible schema version should fail with "INCOMPATIBLE SCHEMA" error.
**Why human:** Requires a running Apicurio Registry v3 instance. This is an integration test that cannot be verified statically.

---

### Summary

All 6 success criteria are fully achieved. The codebase exactly matches the plan specification with 5 documented auto-fixes for real build issues. The phase delivers:

- Three Avro schemas with correct namespaces generating Java classes at the expected package paths
- `generateAvro` Gradle task using `walkTopDown` file discovery (necessary workaround for avro-tools)
- Namespace consistency and backward/full compatibility tests (11 tests, 0 failures)
- `RegisterSchemasTask` in buildSrc with complete Apicurio v3 REST API integration
- `maven-publish` configured for Forgejo Maven registry
- lib-commons (servista-commons) migrated off local `EventEnvelope.avsc` — all 6 source files use the new `eu.servista.schemas.avro.envelope.EventEnvelope` import path
- Three key commits verified in git history: `14eb368`, `e8fcfb2`, `6b9d674` (servista-avro-schemas) and `cda6d4d` (servista-commons)

---

_Verified: 2026-03-03T18:00:00Z_
_Verifier: Claude (gsd-verifier)_
