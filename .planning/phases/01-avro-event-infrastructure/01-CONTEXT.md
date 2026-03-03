# Phase 1: Avro Event Infrastructure - Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Create a dedicated `servista-avro-schemas` repository containing all Avro schema definitions (event envelope + per-domain payload schemas), with generated Kotlin classes published to Forgejo Maven registry. Includes a Gradle task for registering schemas in Apicurio with compatibility validation. Phase 1 delivers the envelope schema (moved from lib-commons), the repo structure with domain directories for all 9 domains, 2-3 example payload schemas to validate the pipeline end-to-end, and the Apicurio registration mechanism. Individual services add their specific event type schemas in their own phases using this repo as the central schema-first source of truth.

</domain>

<decisions>
## Implementation Decisions

### Schema repository
- **Dedicated repo:** `servista-avro-schemas` (follows `<type>-<name>` — but like gradle-platform, unique enough to skip type prefix)
- All .avsc files live here — envelope AND per-domain payloads
- EventEnvelope.avsc **moves from lib-commons** to this repo (lib-commons depends on servista-avro-schemas for generated envelope classes instead of owning the .avsc)
- Published to Forgejo Maven registry as `eu.servista:servista-avro-schemas`
- Applies `servista.library` + `servista.avro` + `servista.testing` convention plugins from gradle-platform

### Directory layout
- Organized by domain, mapping 1:1 to Kafka topic domains (ADR-012)
- Structure:
  ```
  src/main/avro/
    envelope/
      EventEnvelope.avsc
    accounts/
      AccountCreated.avsc
      ...
    organizations/
      OrgCreated.avsc
      ...
    iam/
    authorization/
    integrations/
    usage/
    subscriptions/
    billing/
    egress/
  ```
- Empty domain directories created upfront for all 9 domains — services populate them as their phases execute

### Kotlin package namespace
- Generated classes use `eu.servista.schemas.avro.{domain}` package
- Examples: `eu.servista.schemas.avro.accounts.AccountCreated`, `eu.servista.schemas.avro.envelope.EventEnvelope`

### Payload schema scope
- Phase 1 defines 2-3 example payload schemas to validate the full pipeline (codegen, publishing, Apicurio registration, compatibility checks)
- Core fields only — essential fields that define the event, not exhaustive field sets
- Services add their specific event type schemas in their own phases via schema-first workflow
- This is not speculative upfront design — individual phases know best which events they need

### Apicurio registration
- Custom Gradle task (`registerSchemas`) that calls Apicurio REST API
- Two-step: compatibility check first, then register — task fails fast with clear error if schema is incompatible
- Usable both locally (`./gradlew registerSchemas`) and from CI (Phase 9 will call the same task)
- Apicurio artifact organization: Group ID = topic name (e.g., `servista.accounts.events`), Artifact ID = event type (e.g., `AccountCreated`)
- EventEnvelope uses group `servista.envelope`
- Compatibility rules: FULL for envelope (group `servista.envelope`), BACKWARD for all domain payload groups

### Schema ownership and evolution
- Central repo, PR-based governance — any service phase can propose schema changes via PR to servista-avro-schemas
- Schema-first workflow: new event types defined in servista-avro-schemas first, published to Forgejo Maven, then service repos depend on the new version
- Schema definition is an explicit step in each downstream phase's plan
- Apicurio compatibility checks (FULL/BACKWARD) are the automated safety net against breaking changes

### Claude's Discretion
- Single-version vs per-domain module Maven artifact strategy (leaning single version for simplicity)
- Exact Gradle task implementation for Apicurio registration (HTTP client choice, error handling)
- Which 2-3 example event types to define (likely OrgCreated, AccountCreated from IAM domain as first consumers)
- How lib-commons dependency on servista-avro-schemas is structured (direct vs transitive)
- Avro codegen configuration details (field visibility, string type, custom conversions)

</decisions>

<specifics>
## Specific Ideas

- User explicitly chose `eu.servista.schemas.avro.{domain}` namespace — leaves room for non-Avro schemas in the future (e.g., JSON Schema for API validation under `eu.servista.schemas.json`)
- User wants schema-first contract approach — no local schema development that drifts from canonical definitions
- Example payload schemas should use core fields only, with the expectation that services evolve them via BACKWARD-compatible additions
- User considers it "quite impossible at this stage to guess which events will be needed for the entire platform" — Phase 1 establishes the infrastructure and pattern, not the complete event catalog

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `servista.avro` convention plugin (Phase 3): Provides Avro 1.12.1 + Apicurio SerDes 3.0.0.M4 dependencies and codegen configuration
- `servista.library` convention plugin: Kotlin 2.3 + JVM 21 + detekt + ktfmt baseline
- EventEnvelope field spec (architecture diagram 06): event_id, event_type, aggregate_id, organization_id, account_id, timestamp, correlation_id, causation_id, payload (bytes)
- Phase 4 lib-commons: Currently owns EventEnvelope.avsc — this moves to servista-avro-schemas, lib-commons becomes a consumer
- ADR-012: All 9 domain topic names defined (servista.{domain}.events)

### Established Patterns
- Multi-repo with Forgejo Maven registry for artifact distribution (Phase 3 decision)
- Convention plugins provide dependencies + codegen; runtime behavior lives in consuming code
- Avro codegen via custom Gradle task using avro-tools SpecificCompiler (Phase 4 research)
- Version catalog in gradle-platform controls all dependency versions

### Integration Points
- lib-commons depends on servista-avro-schemas for EventEnvelope generated classes (replaces local .avsc ownership)
- All service repos producing/consuming events depend on servista-avro-schemas for generated event classes
- Phase 6 (Kafka Topic Provisioning) uses the same 9 domain names for topic configuration
- Phase 9 (CI Pipeline) will invoke the `registerSchemas` Gradle task as a CI step
- Apicurio schema registry (already deployed on Kubernetes) is the runtime schema store

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-avro-event-infrastructure*
*Context gathered: 2026-03-03*
