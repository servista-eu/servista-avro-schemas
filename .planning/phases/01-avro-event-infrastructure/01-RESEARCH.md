# Phase 1: Avro Event Infrastructure - Research

**Researched:** 2026-03-03
**Domain:** Avro schema repository, Apicurio Registry schema registration, Gradle codegen and registration tasks
**Confidence:** HIGH

## Summary

Phase 1 creates the `servista-avro-schemas` repository -- a dedicated, centralized Avro schema repository containing the EventEnvelope schema (moved from lib-commons) and per-domain payload schemas for all 9 Kafka topic domains. The repository publishes generated Java/Kotlin classes as a Maven artifact (`eu.servista:servista-avro-schemas`) to the Forgejo Maven registry, and provides a custom Gradle task (`registerSchemas`) that registers all schemas in Apicurio Registry with appropriate compatibility rules (FULL for envelope, BACKWARD for all domain payloads).

The technical domain is well-understood. Avro 1.12.1 codegen via custom Gradle task (avro-tools SpecificCompiler) is already proven in Phase 4's lib-commons. The main new challenge is the Apicurio Registry v3 REST API integration for schema registration and compatibility rule configuration. Apicurio Registry 3.x (latest: 3.1.7) provides a hierarchical rules system with group-level rules -- this maps naturally to the Phase 1 decision to use Apicurio groups matching Kafka topic domain names (e.g., group `servista.accounts.events` for account schemas).

The Apicurio v3 REST API base path is `/apis/registry/v3`. Key endpoints: `POST /groups/{groupId}/artifacts` creates an artifact with optional first version content; `POST /groups/{groupId}/rules` configures group-level compatibility rules; `POST /groups/{groupId}/artifacts/{artifactId}/versions` adds new versions to existing artifacts. The `dryRun=true` query parameter validates without persisting (replaces the old `/test` endpoint), enabling a compatibility-check-then-register two-step workflow.

**Primary recommendation:** Build a single-module Gradle project with Avro codegen (same pattern as lib-commons), domain-organized `.avsc` files under `src/main/avro/{domain}/`, and a custom `registerSchemas` Gradle task that uses `java.net.http.HttpClient` (JDK 21 built-in) to call the Apicurio Registry v3 REST API. Use group-level rules for compatibility enforcement. The EventEnvelope.avsc moves from lib-commons to this repo; lib-commons becomes a consumer of the published `servista-avro-schemas` artifact.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Dedicated repo:** `servista-avro-schemas` (follows `<type>-<name>` -- but like gradle-platform, unique enough to skip type prefix)
- All .avsc files live here -- envelope AND per-domain payloads
- EventEnvelope.avsc **moves from lib-commons** to this repo (lib-commons depends on servista-avro-schemas for generated envelope classes instead of owning the .avsc)
- Published to Forgejo Maven registry as `eu.servista:servista-avro-schemas`
- Applies `servista.library` + `servista.avro` + `servista.testing` convention plugins from gradle-platform
- Directory layout organized by domain, mapping 1:1 to Kafka topic domains (ADR-012):
  ```
  src/main/avro/
    envelope/
      EventEnvelope.avsc
    accounts/
      AccountCreated.avsc
    organizations/
      OrgCreated.avsc
    iam/
    authorization/
    integrations/
    usage/
    subscriptions/
    billing/
    egress/
  ```
- Empty domain directories created upfront for all 9 domains -- services populate them as their phases execute
- Generated Kotlin package namespace: `eu.servista.schemas.avro.{domain}` (e.g., `eu.servista.schemas.avro.accounts.AccountCreated`)
- Phase 1 defines 2-3 example payload schemas to validate the full pipeline (codegen, publishing, Apicurio registration, compatibility checks)
- Core fields only -- essential fields that define the event, not exhaustive field sets
- Custom Gradle task (`registerSchemas`) that calls Apicurio REST API
- Two-step: compatibility check first, then register -- task fails fast with clear error if schema is incompatible
- Usable both locally (`./gradlew registerSchemas`) and from CI
- Apicurio artifact organization: Group ID = topic name (e.g., `servista.accounts.events`), Artifact ID = event type (e.g., `AccountCreated`)
- EventEnvelope uses group `servista.envelope`
- Compatibility rules: FULL for envelope (group `servista.envelope`), BACKWARD for all domain payload groups
- Central repo, PR-based governance -- any service phase can propose schema changes via PR to servista-avro-schemas
- Schema-first workflow: new event types defined in servista-avro-schemas first, published to Forgejo Maven, then service repos depend on the new version

### Claude's Discretion
- Single-version vs per-domain module Maven artifact strategy (leaning single version for simplicity)
- Exact Gradle task implementation for Apicurio registration (HTTP client choice, error handling)
- Which 2-3 example event types to define (likely OrgCreated, AccountCreated from IAM domain as first consumers)
- How lib-commons dependency on servista-avro-schemas is structured (direct vs transitive)
- Avro codegen configuration details (field visibility, string type, custom conversions)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| FOUND-04 | Avro event schema definitions -- envelope schema (FULL compatibility) + per-domain payload schemas (BACKWARD compatibility) registered in Apicurio | Full research coverage: Avro 1.12.1 codegen via custom task, Apicurio v3 REST API for artifact creation and group-level compatibility rules, domain-organized schema directory structure, registerSchemas Gradle task implementation with java.net.http.HttpClient, and lib-commons migration strategy |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| avro | 1.12.1 | Schema definition (.avsc) and Java class generation | Platform standard per ADR-017; already in version catalog and servista.avro plugin |
| avro-tools | 1.12.1 | Avro codegen SpecificCompiler (build-time only) | Official Apache Avro code generation tool; custom Gradle task proven in Phase 4 |
| java.net.http.HttpClient | JDK 21 built-in | HTTP client for Apicurio REST API calls in registerSchemas task | Zero external dependencies; JDK 21 is the platform target; sufficient for REST API calls |
| kotlinx-serialization-json | 1.10.0 | JSON parsing/generation for Apicurio REST API request/response bodies | Already in version catalog; compile-time safe JSON handling |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| apicurio-registry-serdes-avro-serde | 3.0.0.M4 | Apicurio SerDes for runtime schema resolution (transitive from servista.avro plugin) | Not directly used in schema repo build tasks; present for codegen compatibility |
| junit5 | 5.14.2 | Test framework | Unit tests for registration task logic, schema validation |
| kotest-assertions | 6.1.4 | Assertion library | Test assertions |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom registerSchemas Gradle task | croz-ltd/apicurio-registry-gradle-plugin | Last updated Jan 2023 (3+ years stale), targets Apicurio 1.x/2.x, incompatible with Apicurio 3.x REST API changes, unknown Gradle 9 compatibility. Custom task is safer. |
| Custom registerSchemas Gradle task | Apicurio Maven plugin (3.0.x) via Maven exec | Maven-only, no Gradle integration. Would require shelling out to Maven or porting XML config. Custom Gradle task with JDK HttpClient is cleaner. |
| java.net.http.HttpClient | OkHttp or Ktor client | Adds external dependency for simple REST calls. JDK HttpClient handles all needed operations (POST, PUT, GET with JSON bodies) with zero deps. |
| Custom registerSchemas Gradle task | apicurio-registry-java-sdk (3.1.7) | SDK pulls in Vert.x and Kiota dependencies (heavy). JDK HttpClient for 3-4 REST endpoints is dramatically simpler and avoids dependency bloat in the build classpath. |
| Single-module artifact | Per-domain Gradle submodules | Per-domain modules allow granular dependencies (service only imports its domain schemas). But adds significant build complexity (multi-module Gradle project, per-module publishing, version synchronization). Single module is recommended for simplicity given all schemas are small and services already depend on Avro. |

**Recommendation for Claude's Discretion items:**
- **Single-module artifact:** Use a single `eu.servista:servista-avro-schemas` artifact. All generated classes from all domains are in one JAR. This is simple, and the marginal size of Avro generated classes (a few KB each) does not justify multi-module overhead.
- **HTTP client:** Use `java.net.http.HttpClient` (JDK 21 built-in). Zero external dependencies for the build task.
- **Example event types:** Define `AccountCreated` (in accounts/) and `OrgCreated` (in organizations/). These are the first events in the org onboarding flow (architecture diagram 06), used by IAM API and Subscriptions API as consumers. Two examples is sufficient to prove the full pipeline.
- **lib-commons dependency:** Direct dependency. lib-commons `build.gradle.kts` adds `implementation("eu.servista:servista-avro-schemas:${version}")`. The EventEnvelope generated class import changes from `eu.servista.commons.event.avro.EventEnvelope` to `eu.servista.schemas.avro.envelope.EventEnvelope`.
- **Avro codegen config:** Use default avro-tools settings (field visibility: public, string type: CharSequence). Do NOT use `setStringType(Utf8)` or other customizations -- defaults are sufficient and match Phase 4 lib-commons patterns.

**Installation (build.gradle.kts for servista-avro-schemas):**
```kotlin
plugins {
    id("servista.library")
    id("servista.avro")
    id("servista.testing")
    `maven-publish`
}

dependencies {
    // avro and apicurio-serdes already provided by servista.avro plugin
    // kotlinx-serialization for Gradle task JSON handling (buildscript classpath)
}

// Avro codegen custom task (same pattern as lib-commons)
val avroTools by configurations.creating
dependencies {
    avroTools("org.apache.avro:avro-tools:1.12.1")
}

val generateAvro by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Generate Java classes from Avro schemas"
    classpath = avroTools
    mainClass.set("org.apache.avro.tool.Main")
    args = listOf(
        "compile", "schema",
        "${projectDir}/src/main/avro",
        "${layout.buildDirectory.get().asFile}/generated-avro-java"
    )
    inputs.dir("src/main/avro")
    outputs.dir(layout.buildDirectory.dir("generated-avro-java"))
}

sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated-avro-java"))
tasks.named("compileKotlin") { dependsOn(generateAvro) }
tasks.named("compileJava") { dependsOn(generateAvro) }

// Publishing
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "eu.servista"
            artifactId = "servista-avro-schemas"
        }
    }
    repositories {
        maven {
            url = uri("https://git.hestia-ng.eu/api/packages/servista/maven")
            credentials { /* from gradle.properties or env */ }
        }
    }
}
```

## Architecture Patterns

### Recommended Repository Structure
```
servista-avro-schemas/
  build.gradle.kts              # Plugins, codegen task, registerSchemas task, publishing
  settings.gradle.kts           # Project name, plugin resolution, version catalog
  gradle.properties             # group=eu.servista, version=0.1.0
  gradle/                       # Gradle wrapper (copied from gradle-platform)
  src/
    main/
      avro/                     # All .avsc schema files (source of truth)
        envelope/
          EventEnvelope.avsc    # MOVED from lib-commons
        accounts/
          AccountCreated.avsc   # Example payload schema
        organizations/
          OrgCreated.avsc       # Example payload schema
        iam/                    # Empty -- Phase 22+ populates
        authorization/          # Empty -- Phase 27+ populates
        integrations/           # Empty -- Phase 45+ populates
        usage/                  # Empty -- Phase 30+ populates
        subscriptions/          # Empty -- Phase 33+ populates
        billing/                # Empty -- Phase 54+ populates
        egress/                 # Empty -- Phase 60+ populates
      kotlin/                   # Required by servista.library
      resources/                # Required by servista.library
    test/
      kotlin/
        eu/servista/schemas/avro/
          SchemaRegistrationTest.kt   # Tests registration task logic
          SchemaCompatibilityTest.kt  # Tests schema evolution rules
  build/
    generated-avro-java/        # Avro codegen output (NOT committed)
      eu/servista/schemas/avro/
        envelope/
          EventEnvelope.java
        accounts/
          AccountCreated.java
        organizations/
          OrgCreated.java
```

### Pattern 1: Avro Schema with Domain Namespace
**What:** Each .avsc file defines a namespace matching the domain directory, producing generated classes in the correct package.
**When to use:** Every domain event payload schema.
**Example:**

```json
// src/main/avro/accounts/AccountCreated.avsc
{
  "namespace": "eu.servista.schemas.avro.accounts",
  "type": "record",
  "name": "AccountCreated",
  "doc": "Payload for account creation events. Published to servista.accounts.events topic.",
  "fields": [
    {"name": "account_id",      "type": "long",   "doc": "Snowflake ID of the created account"},
    {"name": "email",           "type": "string", "doc": "Account email address"},
    {"name": "display_name",    "type": "string", "doc": "Account display name"},
    {"name": "organization_id", "type": "long",   "doc": "Org the account belongs to"},
    {"name": "created_at",      "type": "long",   "doc": "Unix epoch millis of creation"}
  ]
}
```

```json
// src/main/avro/organizations/OrgCreated.avsc
{
  "namespace": "eu.servista.schemas.avro.organizations",
  "type": "record",
  "name": "OrgCreated",
  "doc": "Payload for organization creation events. Published to servista.organizations.events topic.",
  "fields": [
    {"name": "organization_id",  "type": "long",   "doc": "Snowflake ID of the created org"},
    {"name": "name",             "type": "string", "doc": "Organization name"},
    {"name": "vat_number",       "type": "string", "doc": "VAT number (unique identifier per ORG-02)"},
    {"name": "status",           "type": "string", "doc": "Org lifecycle status (provisioning, active, etc.)"},
    {"name": "created_at",       "type": "long",   "doc": "Unix epoch millis of creation"}
  ]
}
```

**Confidence:** HIGH -- Avro namespace-to-package mapping is a core Avro feature. Phase 4 already uses this pattern for EventEnvelope.

### Pattern 2: EventEnvelope Schema Migration
**What:** Move EventEnvelope.avsc from lib-commons to servista-avro-schemas, update its namespace, and have lib-commons depend on the published artifact.
**When to use:** One-time migration in this phase.

The EventEnvelope.avsc namespace changes from `eu.servista.commons.event.avro` (Phase 4) to `eu.servista.schemas.avro.envelope`:

```json
// src/main/avro/envelope/EventEnvelope.avsc
{
  "namespace": "eu.servista.schemas.avro.envelope",
  "type": "record",
  "name": "EventEnvelope",
  "doc": "Platform event envelope. Registered in Apicurio with FULL compatibility.",
  "fields": [
    {"name": "event_id",        "type": "long",             "doc": "Snowflake ID for this event"},
    {"name": "event_type",      "type": "string",           "doc": "Dot-delimited event type (e.g., account.created)"},
    {"name": "aggregate_id",    "type": "long",             "doc": "Snowflake ID of the aggregate root"},
    {"name": "organization_id", "type": "long",             "doc": "Tenant org Snowflake ID"},
    {"name": "account_id",      "type": ["null", "long"],   "default": null, "doc": "Actor account ID (null for system events)"},
    {"name": "timestamp",       "type": "long",             "doc": "Unix epoch millis when event was produced"},
    {"name": "correlation_id",  "type": "string",           "doc": "Request correlation ID"},
    {"name": "causation_id",    "type": ["null", "long"],   "default": null, "doc": "Snowflake ID of the causing event"},
    {"name": "payload",         "type": "bytes",            "doc": "Domain-specific payload (Avro-encoded per event_type schema)"}
  ]
}
```

**lib-commons migration:**
1. Remove `src/main/avro/EventEnvelope.avsc` from lib-commons
2. Add `implementation("eu.servista:servista-avro-schemas:0.1.0")` to lib-commons dependencies
3. Update all imports: `eu.servista.commons.event.avro.EventEnvelope` to `eu.servista.schemas.avro.envelope.EventEnvelope`
4. lib-commons no longer needs its own Avro codegen task for EventEnvelope (but may retain it if it has other .avsc files in the future)

**Confidence:** HIGH -- This is a straightforward dependency refactoring. The import path change is mechanical.

### Pattern 3: Custom Gradle Task for Apicurio Registration
**What:** A Gradle task that discovers all .avsc files, maps them to Apicurio groups and artifact IDs, configures group-level compatibility rules, checks compatibility via dryRun, then registers.
**When to use:** Local development (`./gradlew registerSchemas`) and CI pipeline (Phase 9).

```kotlin
// Gradle task pseudocode structure
abstract class RegisterSchemasTask : DefaultTask() {
    @get:Input
    abstract val registryUrl: Property<String>

    @get:InputDirectory
    abstract val schemaDir: DirectoryProperty

    @TaskAction
    fun register() {
        val client = HttpClient.newHttpClient()
        val schemas = discoverSchemas(schemaDir.get().asFile)

        // Step 1: Ensure groups exist and configure compatibility rules
        for (group in schemas.map { it.group }.distinct()) {
            ensureGroupExists(client, group)
            configureGroupRule(client, group, compatibilityFor(group))
        }

        // Step 2: For each schema, check compatibility then register
        for (schema in schemas) {
            // dryRun=true validates without persisting
            val compatible = checkCompatibility(client, schema)
            if (!compatible) {
                throw GradleException(
                    "Schema ${schema.artifactId} is incompatible with " +
                    "existing version in group ${schema.group}"
                )
            }
            registerSchema(client, schema)
            logger.lifecycle("Registered ${schema.group}/${schema.artifactId}")
        }
    }

    private fun compatibilityFor(group: String): String =
        if (group == "servista.envelope") "FULL" else "BACKWARD"
}
```

**Confidence:** MEDIUM -- The Gradle task pattern is straightforward, but Apicurio v3 REST API specifics (exact request body format, group creation idempotency, error codes) need validation against a running instance.

### Pattern 4: Apicurio Registry v3 REST API Endpoints
**What:** The REST API calls needed for schema registration.
**When to use:** Implementation of the registerSchemas Gradle task.

**Base URL:** `{registryUrl}/apis/registry/v3`

**Create artifact with first version:**
```
POST /groups/{groupId}/artifacts
Content-Type: application/json

{
  "artifactId": "AccountCreated",
  "artifactType": "AVRO",
  "firstVersion": {
    "version": "1",
    "content": {
      "content": "<escaped .avsc JSON string>",
      "contentType": "application/json"
    }
  }
}
```

**Add new version to existing artifact:**
```
POST /groups/{groupId}/artifacts/{artifactId}/versions
Content-Type: application/json

{
  "version": "2",
  "content": {
    "content": "<escaped .avsc JSON string>",
    "contentType": "application/json"
  }
}
```

**Compatibility check via dryRun (no side effects):**
```
POST /groups/{groupId}/artifacts/{artifactId}/versions?dryRun=true
Content-Type: application/json

{
  "content": {
    "content": "<escaped .avsc JSON string>",
    "contentType": "application/json"
  }
}
```
Returns 200 if compatible, 409 if incompatible (when COMPATIBILITY rule is configured).

**Configure group-level compatibility rule:**
```
POST /groups/{groupId}/rules
Content-Type: application/json

{
  "ruleType": "COMPATIBILITY",
  "config": "FULL"
}
```
(Use "BACKWARD" for domain payload groups)

**Create group (if not exists):**
```
POST /groups
Content-Type: application/json

{
  "groupId": "servista.accounts.events"
}
```

**Confidence:** HIGH for endpoint paths (verified from official Apicurio v3 blog post and documentation). MEDIUM for exact request body format (the `ruleType`/`config` field names are extrapolated from v2 patterns and Apicurio documentation; should be validated against a running instance).

### Pattern 5: Schema File Discovery and Mapping
**What:** Map directory structure to Apicurio group/artifact organization.
**When to use:** registerSchemas task schema discovery.

```
Directory                    Apicurio Group               Artifact ID
────────────────────────     ────────────────────────     ───────────────
envelope/EventEnvelope.avsc  servista.envelope            EventEnvelope
accounts/AccountCreated.avsc servista.accounts.events     AccountCreated
organizations/OrgCreated.avsc servista.organizations.events OrgCreated
iam/{future}.avsc            servista.iam.events          {schema name}
```

Mapping rules:
- `envelope/` directory maps to group `servista.envelope`
- All other directories map to group `servista.{dirname}.events` (matching ADR-012 topic names)
- Artifact ID = schema file name without `.avsc` extension (matches the Avro record name)
- Empty directories are skipped (no schemas to register)

**Confidence:** HIGH -- Direct mapping from CONTEXT.md decision and ADR-012 topic names.

### Anti-Patterns to Avoid
- **Registering schemas without group rules configured first:** If compatibility rules are not set before the first version is registered, subsequent versions will be accepted without compatibility checking. Always configure group rules BEFORE registering the first version.
- **Using the Apicurio Java SDK for build tasks:** The SDK (`io.apicurio:apicurio-registry-java-sdk:3.1.7`) pulls in Vert.x and Microsoft Kiota HTTP client -- heavy dependencies inappropriate for a Gradle build task. Use JDK HttpClient instead.
- **Committing generated Avro Java sources:** Generated classes go to `build/generated-avro-java/` and must NOT be committed. The `.gitignore` should exclude `build/`.
- **Embedding schema content directly in Gradle task:** Read .avsc files at task execution time from the `src/main/avro/` directory. Do not hardcode schema content in the build script.
- **Registering envelope schemas with BACKWARD compatibility:** The EventEnvelope is infrastructure -- it must use FULL compatibility (both forward and backward) to ensure all producers and consumers can always interoperate. Only domain payloads use BACKWARD.
- **Including avro-tools on the main classpath:** avro-tools is large (40MB+) and includes many transitive dependencies. It must be on a separate `avroTools` configuration, used only by the codegen task, NOT on `implementation` or `runtimeOnly`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Avro Java class generation | Custom codegen from schema AST | avro-tools SpecificCompiler via JavaExec Gradle task | Official Apache tool handles all Avro types, unions, defaults, logical types |
| Schema compatibility checking | Custom diff/comparison logic | Apicurio Registry compatibility rules (group-level FULL/BACKWARD) | Apicurio handles all compatibility dimensions (field additions, removals, renames, type changes) per Avro spec |
| JSON serialization for REST API | Manual string concatenation | kotlinx-serialization-json for request body construction | Correct escaping of .avsc content within JSON request bodies is error-prone |
| HTTP client for REST API | OkHttp, Apache HttpClient, Ktor client | java.net.http.HttpClient (JDK 21) | Zero external dependencies for build-time HTTP calls |
| Schema file discovery | Hardcoded list of schemas in build.gradle.kts | File tree walk of `src/main/avro/` at task execution | Automatically discovers new schemas as domain phases add them; no build script maintenance |

**Key insight:** The schema repository is intentionally simple -- it is a codegen + registration pipeline, not an application. The complexity is in the workflow (schema-first development across repos) not in the code. Keep the Gradle build minimal: codegen via avro-tools, registration via REST API, publishing via maven-publish.

## Common Pitfalls

### Pitfall 1: Avro Namespace Mismatch Between Schema and Directory
**What goes wrong:** Generated class package does not match expected import path. For example, a schema in `accounts/AccountCreated.avsc` with namespace `eu.servista.schemas.avro.account` (singular) generates into the wrong package.
**Why it happens:** Avro namespace comes from the .avsc `namespace` field, NOT from the filesystem directory name. The directory is only used by avro-tools to find the file.
**How to avoid:** Enforce a naming convention: the namespace `eu.servista.schemas.avro.{domain}` must match the directory name exactly. Add a test that reads all .avsc files and verifies namespace matches the parent directory name.
**Warning signs:** `import eu.servista.schemas.avro.accounts.AccountCreated` fails with "unresolved reference" but the schema file exists.

### Pitfall 2: Avro Codegen Order Dependencies
**What goes wrong:** If schema A references a type defined in schema B, and avro-tools processes A before B, codegen fails with "undefined name" error.
**Why it happens:** avro-tools SpecificCompiler processes schemas in alphabetical order by default. Cross-schema references require the referenced schema to be processed first.
**How to avoid:** For Phase 1, no cross-schema references exist (envelope payload is bytes, not a named type). If cross-references are needed in the future, use avro-tools `compile schema` with multiple schema files in dependency order, or use Avro's schema import/include mechanism. Document this constraint for future phases.
**Warning signs:** Codegen fails with "Type not found" or "Undefined name" errors.

### Pitfall 3: Registering Schemas Against Wrong Apicurio Instance
**What goes wrong:** The registerSchemas task modifies the production schema registry during local development, potentially breaking compatibility rules or registering draft schemas.
**Why it happens:** The Apicurio registry URL is configured once and the task does not distinguish environments.
**How to avoid:** Make registryUrl a configurable property with NO default. Require explicit `-PregistryUrl=http://...` parameter. For local development, use a local Apicurio instance (e.g., via Docker Compose or Testcontainers). CI passes the production URL. Fail the task if registryUrl is not provided.
**Warning signs:** Unexpected schema versions appearing in production registry.

### Pitfall 4: First Registration Without Group Rules
**What goes wrong:** The first schema version is registered in a group that has no compatibility rules configured. When a second version is later added, compatibility checking is applied for the first time, and it may fail unexpectedly because the first version was registered "without rules."
**Why it happens:** Apicurio applies rules at registration time. If no group rule exists, the first version is accepted unconditionally. The rule is then configured. The second version is checked against the first -- this is correct behavior, but developers may be surprised that the first version "set the baseline" without validation.
**How to avoid:** The registerSchemas task must configure group rules BEFORE registering ANY versions. The two-step workflow (rules first, then versions) prevents this confusion.
**Warning signs:** Second schema registration fails with compatibility errors that seem incorrect because the first version was never validated.

### Pitfall 5: Avro `bytes` Type and Payload Schema Confusion
**What goes wrong:** Developers try to define the payload field as a union of all possible event types instead of using `bytes`. This creates a massive, tightly-coupled envelope schema that breaks FULL compatibility on every new event type.
**Why it happens:** It seems "cleaner" to have a typed union than opaque bytes. But the envelope is infrastructure -- it must not change when domains evolve.
**How to avoid:** The envelope `payload` field is `bytes` per architecture decision. Domain-specific deserialization is done by the consumer based on `event_type`. The consumer knows which payload schema to use and deserializes the bytes accordingly. This is the standard Kafka envelope pattern.
**Warning signs:** Proposals to add new union members to EventEnvelope.avsc.

### Pitfall 6: Schema Content Escaping in REST API JSON Body
**What goes wrong:** The .avsc file content (which is itself JSON) must be embedded as a string inside the Apicurio REST API JSON request body. Incorrect escaping causes malformed JSON.
**Why it happens:** The `content` field in the Apicurio API expects the schema as a JSON string, not as a nested JSON object. So the schema JSON must be string-escaped (quotes escaped, etc.).
**How to avoid:** Use kotlinx-serialization to properly construct the request JSON. Read the .avsc file content as a string, then embed it in the serialized JSON body using the serialization library's escaping. Never use manual string concatenation.
**Warning signs:** Apicurio returns 400 Bad Request with "invalid JSON" or "unable to parse content" errors.

## Code Examples

### Gradle Task: Schema Discovery
```kotlin
// Source: Gradle API + project convention
data class AvroSchemaInfo(
    val group: String,         // e.g., "servista.accounts.events"
    val artifactId: String,    // e.g., "AccountCreated"
    val file: File,            // e.g., src/main/avro/accounts/AccountCreated.avsc
    val content: String,       // Raw .avsc JSON content
)

fun discoverSchemas(avroDir: File): List<AvroSchemaInfo> {
    return avroDir.walkTopDown()
        .filter { it.extension == "avsc" }
        .map { file ->
            val domain = file.parentFile.name // "accounts", "envelope", etc.
            val group = if (domain == "envelope") {
                "servista.envelope"
            } else {
                "servista.$domain.events"
            }
            AvroSchemaInfo(
                group = group,
                artifactId = file.nameWithoutExtension,
                file = file,
                content = file.readText(),
            )
        }
        .toList()
}
```

### Gradle Task: Register Schema via Apicurio v3 REST API
```kotlin
// Source: Apicurio Registry v3 REST API documentation
// https://www.apicur.io/blog/2025/03/24/rest-api-changes
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun createOrUpdateArtifact(
    client: HttpClient,
    baseUrl: String,
    schema: AvroSchemaInfo,
) {
    val apiBase = "$baseUrl/apis/registry/v3"

    // Check if artifact already exists
    val checkReq = HttpRequest.newBuilder()
        .uri(URI.create("$apiBase/groups/${schema.group}/artifacts/${schema.artifactId}"))
        .GET()
        .build()
    val checkResp = client.send(checkReq, HttpResponse.BodyHandlers.ofString())

    if (checkResp.statusCode() == 404) {
        // Create artifact with first version
        val body = buildJsonObject {
            put("artifactId", schema.artifactId)
            put("artifactType", "AVRO")
            putJsonObject("firstVersion") {
                put("version", "1")
                putJsonObject("content") {
                    put("content", schema.content)
                    put("contentType", "application/json")
                }
            }
        }.toString()

        val createReq = HttpRequest.newBuilder()
            .uri(URI.create("$apiBase/groups/${schema.group}/artifacts"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val createResp = client.send(createReq, HttpResponse.BodyHandlers.ofString())
        require(createResp.statusCode() in 200..299) {
            "Failed to create artifact ${schema.artifactId}: ${createResp.statusCode()} ${createResp.body()}"
        }
    } else {
        // Add new version (compatibility will be checked by Apicurio group rules)
        val body = buildJsonObject {
            putJsonObject("content") {
                put("content", schema.content)
                put("contentType", "application/json")
            }
        }.toString()

        val versionReq = HttpRequest.newBuilder()
            .uri(URI.create("$apiBase/groups/${schema.group}/artifacts/${schema.artifactId}/versions"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val versionResp = client.send(versionReq, HttpResponse.BodyHandlers.ofString())
        require(versionResp.statusCode() in 200..299) {
            "Failed to add version for ${schema.artifactId}: ${versionResp.statusCode()} ${versionResp.body()}"
        }
    }
}
```

### Gradle Task: Configure Group-Level Compatibility Rule
```kotlin
// Source: Apicurio Registry v3 REST API - group rules
// https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-intro-to-registry-rules.html
fun configureGroupCompatibility(
    client: HttpClient,
    baseUrl: String,
    groupId: String,
    compatibility: String, // "FULL" or "BACKWARD"
) {
    val apiBase = "$baseUrl/apis/registry/v3"

    // Create group if not exists
    val groupBody = """{"groupId": "$groupId"}"""
    val groupReq = HttpRequest.newBuilder()
        .uri(URI.create("$apiBase/groups"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(groupBody))
        .build()
    val groupResp = client.send(groupReq, HttpResponse.BodyHandlers.ofString())
    // 200 = created, 409 = already exists (both OK)

    // Configure compatibility rule
    val ruleBody = """{"ruleType": "COMPATIBILITY", "config": "$compatibility"}"""
    val ruleReq = HttpRequest.newBuilder()
        .uri(URI.create("$apiBase/groups/$groupId/rules"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(ruleBody))
        .build()
    val ruleResp = client.send(ruleReq, HttpResponse.BodyHandlers.ofString())
    // 204 = created, 409 = already exists -- if exists, update via PUT
    if (ruleResp.statusCode() == 409) {
        val updateReq = HttpRequest.newBuilder()
            .uri(URI.create("$apiBase/groups/$groupId/rules/COMPATIBILITY"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("""{"ruleType": "COMPATIBILITY", "config": "$compatibility"}"""))
            .build()
        client.send(updateReq, HttpResponse.BodyHandlers.ofString())
    }
}
```

### Test: Schema Namespace Validation
```kotlin
// Source: Project convention for schema-directory consistency
@Test
fun `all schema namespaces match their directory name`() {
    val avroDir = Path.of("src/main/avro")
    val schemas = avroDir.toFile().walkTopDown()
        .filter { it.extension == "avsc" }
        .toList()

    schemas.forEach { file ->
        val json = Json.parseToJsonElement(file.readText()).jsonObject
        val namespace = json["namespace"]?.jsonPrimitive?.content
            ?: fail("Schema ${file.name} has no namespace")
        val expectedDomain = file.parentFile.name
        val expectedNamespace = "eu.servista.schemas.avro.$expectedDomain"
        namespace shouldBe expectedNamespace
    }
}
```

### Test: Schema Backward Compatibility (unit test without Apicurio)
```kotlin
// Source: Apache Avro SchemaCompatibility API
import org.apache.avro.SchemaCompatibility
import org.apache.avro.Schema

@Test
fun `AccountCreated v2 is backward compatible with v1`() {
    val v1 = Schema.Parser().parse("""
        {"type":"record","name":"AccountCreated",
         "namespace":"eu.servista.schemas.avro.accounts",
         "fields":[
           {"name":"account_id","type":"long"},
           {"name":"email","type":"string"}
         ]}
    """.trimIndent())

    val v2 = Schema.Parser().parse("""
        {"type":"record","name":"AccountCreated",
         "namespace":"eu.servista.schemas.avro.accounts",
         "fields":[
           {"name":"account_id","type":"long"},
           {"name":"email","type":"string"},
           {"name":"display_name","type":["null","string"],"default":null}
         ]}
    """.trimIndent())

    // BACKWARD: new reader (v2) can read old data (v1)
    val result = SchemaCompatibility.checkReaderWriterCompatibility(v2, v1)
    result.type shouldBe SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Apicurio v2 REST API (`/apis/registry/v2`) | Apicurio v3 REST API (`/apis/registry/v3`) | Apicurio 3.0.0 (2025) | New endpoint paths, artifact/version separation, group rules, dryRun parameter. v2 API still available but deprecated. |
| Artifact-level compatibility rules only | Group-level + artifact-level + global rules hierarchy | Apicurio 3.0.0 | Group rules enable domain-wide compatibility enforcement without per-artifact configuration |
| Apicurio SerDes 2.x | Apicurio SerDes 3.0.0.M4 | 2025 | Maven coordinates changed to `io.apicurio:apicurio-registry-serdes-avro-serde`; latest 3.x is M4 milestone |
| gradle-avro-plugin (davidmc24, archived) | Custom Gradle task with avro-tools SpecificCompiler | Dec 2023 (archived) | No maintained third-party Gradle plugin for Avro codegen with Gradle 9.x; custom task is standard approach |
| croz-ltd/apicurio-registry-gradle-plugin | Custom Gradle task with JDK HttpClient | Plugin stale since Jan 2023 | Plugin targets Apicurio 1.x/2.x; incompatible with 3.x API changes. Custom task is required. |
| Confluent Schema Registry | Apicurio Registry | Platform decision | Apicurio is open-source, self-hosted on Kubernetes, supports Avro + more types, compatible with Kafka ecosystem |

**Deprecated/outdated:**
- `croz-ltd/apicurio-registry-gradle-plugin`: Last updated Jan 2023, targets Apicurio 1.x/2.x API. Do NOT use.
- `gradle-avro-plugin` (davidmc24): Archived Dec 2023. Do NOT use with Gradle 9.3.1.
- Apicurio v2 REST API (`/apis/registry/v2`): Still available in Apicurio 3.x for backwards compatibility but deprecated. All new code should use v3 API.

## Open Questions

1. **Exact Apicurio v3 group creation idempotency behavior**
   - What we know: `POST /groups` creates a group. The expected behavior when the group already exists is to return 409 Conflict.
   - What's unclear: Whether the exact response code is 409 or 200/204. The blog post and docs do not show this edge case explicitly.
   - Recommendation: Implement with a try-create approach: attempt POST, if 409 treat as success (group exists). Validate against a running Apicurio instance during implementation.

2. **Apicurio group rule creation request body format**
   - What we know: `POST /groups/{groupId}/rules` configures a rule. The rule types are COMPATIBILITY, VALIDITY, INTEGRITY.
   - What's unclear: The exact JSON field names for the request body (is it `ruleType`/`config` or `type`/`configuration`?).
   - Recommendation: Validate against the OpenAPI spec at `/apis/registry/v3/openapi.json` on a running Apicurio instance. The code examples above use `ruleType`/`config` based on Apicurio 2.x patterns; v3 may differ.

3. **dryRun parameter behavior for first version**
   - What we know: `dryRun=true` on version creation validates without persisting. There is a known bug (#6670) where dryRun is ignored with `ifExists=FIND_OR_CREATE_VERSION`.
   - What's unclear: Whether dryRun works correctly for the first version of a new artifact when compatibility rules are set at the group level.
   - Recommendation: For the first version, skip the dryRun check (there is nothing to check compatibility against). Only use dryRun for subsequent versions. The registerSchemas task should detect whether an artifact already exists before deciding whether to dryRun.

4. **lib-commons EventEnvelope import migration scope**
   - What we know: EventEnvelope namespace changes from `eu.servista.commons.event.avro` to `eu.servista.schemas.avro.envelope`. lib-commons and all downstream references must update imports.
   - What's unclear: Whether Phase 1 or Phase 4 execution handles the lib-commons migration (since Phase 4 may not be executed yet).
   - Recommendation: Phase 1 plans should include the lib-commons migration as a task. If Phase 4 has already been executed, update the lib-commons code. If Phase 4 has not yet been executed, the Phase 4 plans should reference the servista-avro-schemas artifact from the start (but this creates a circular dependency on execution order). Practically: Phase 1 creates servista-avro-schemas and publishes it, then updates lib-commons to consume it.

5. **Apicurio SerDes version alignment**
   - What we know: The version catalog has Apicurio SerDes at 3.0.0.M4. The Apicurio Registry itself is at 3.1.7.
   - What's unclear: Whether SerDes 3.0.0.M4 is compatible with Registry 3.1.7.
   - Recommendation: SerDes and Registry are separate artifacts with separate versioning. SerDes 3.0.0.M4 should work with Registry 3.1.x (the REST API is stable). Verify by running the registerSchemas task against a 3.1.x instance during implementation. If issues arise, check if newer SerDes versions are available on Maven Central.

## Sources

### Primary (HIGH confidence)
- [Apicurio Registry v3 REST API changes blog post](https://www.apicur.io/blog/2025/03/24/rest-api-changes) -- v3 API base path, endpoint reorganization, group rules, dryRun parameter
- [Apicurio Registry content rules documentation](https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-intro-to-registry-rules.html) -- Rule types, hierarchy (global > group > artifact), configuration methods
- [Apicurio Registry rule reference](https://www.apicur.io/registry/docs/apicurio-registry/3.1.x/getting-started/assembly-rule-reference.html) -- FULL, FULL_TRANSITIVE, BACKWARD, BACKWARD_TRANSITIVE compatibility levels; full Avro support confirmed
- [Apicurio Registry managing artifacts via REST API](https://www.apicur.io/registry/docs/apicurio-registry/3.1.x/getting-started/assembly-managing-registry-artifacts-api.html) -- Curl examples for artifact creation, Content-Type requirements, versioning
- [Apicurio Registry artifact reference](https://www.apicur.io/registry/docs/apicurio-registry/3.1.x/getting-started/assembly-artifact-reference.html) -- Groups, artifact types, version organization
- ADR-012 (`architecture/decisions/012-domain-level-topics.md`) -- All 9 domain topic names
- Architecture diagram 06 (`architecture/diagrams/06-event-messaging-flow.md`) -- Event envelope field specification, FULL/BACKWARD compatibility rules
- Phase 4 Research (`04-RESEARCH.md`) -- Avro codegen pattern, custom Gradle task approach, EventEnvelope.avsc schema
- Phase 4 Plan 01 (`04-01-PLAN.md`) -- lib-commons Avro codegen task implementation, settings.gradle.kts includeBuild pattern
- Phase 3 Summary 02 (`03-02-SUMMARY.md`) -- servista.avro convention plugin provides Avro 1.12.1 + Apicurio SerDes 3.0.0.M4
- [Apicurio Maven plugin register goal](https://www.apicur.io/registry/maven-plugin/3.0.6/register-mojo.html) -- Maven plugin parameter reference (groupId, artifactId, artifactType, file)
- [Apicurio Maven plugin usage](https://www.apicur.io/registry/maven-plugin/3.0.6/usage.html) -- XML configuration example for schema registration

### Secondary (MEDIUM confidence)
- [Apicurio Registry SDK documentation](https://www.apicur.io/registry/docs/apicurio-registry/3.1.x/getting-started/assembly-using-the-registry-client.html) -- Java SDK maven coordinates (io.apicurio:apicurio-registry-java-sdk:3.1.7), Vert.x-based client
- [croz-ltd/apicurio-registry-gradle-plugin](https://github.com/croz-ltd/apicurio-registry-gradle-plugin) -- Gradle plugin for Apicurio (stale, last update Jan 2023, Apicurio 1.x/2.x only)
- [Maven Central apicurio-registry-java-sdk](https://central.sonatype.com/artifact/io.apicurio/apicurio-registry-java-sdk) -- Latest version 3.1.7
- [Apicurio Registry GitHub](https://github.com/Apicurio/apicurio-registry) -- Latest release 3.1.7 (Jan 28, 2026)
- [Avro 2025 Guide](https://cloudurable.com/blog/avro-2025/) -- Schema-as-code best practices, evolution patterns

### Tertiary (LOW confidence)
- Apicurio v3 group creation idempotency (409 vs 200 behavior) -- inferred from v2 patterns, not explicitly verified in v3 docs
- Exact JSON request body field names for group rule creation -- extrapolated from v2 documentation and blog post references

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- All versions pinned in existing version catalog; Avro codegen pattern proven in Phase 4
- Architecture: HIGH -- CONTEXT.md provides complete directory layout, namespace convention, and Apicurio organization decisions
- Apicurio REST API integration: MEDIUM -- Endpoint paths verified from official blog/docs; exact request body format for group rules needs validation against running instance
- Pitfalls: HIGH -- Well-known failure modes for Avro codegen, namespace management, and schema registry interaction

**Research date:** 2026-03-03
**Valid until:** 2026-04-03 (stable domain, pinned versions; Apicurio REST API is versioned and stable)
