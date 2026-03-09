# Roadmap: Servista Avro Schemas

## Overview

This roadmap delivers the Servista centralized Avro schema repository (`servista-avro-schemas`). The repository contains all Avro schema definitions (EventEnvelope + per-domain payload schemas) with generated Kotlin/Java classes published to Forgejo Maven registry. Includes a Gradle task for registering schemas in Apicurio Registry with compatibility validation. Plans are governed by the servista/governance/mgmt-platform repo.

## Governance Mapping

| Servista-Avro-Schemas Phase | Governance Phase | Name |
|-----------------------------|-----------------|------|
| 1 | 5 | Avro Event Infrastructure |
| 2 | 6 | DLQ Envelope Schema |
| 3 | 8 | Authorization Event Schemas |

## Phases

### Phase 1: Avro Event Infrastructure
**Goal**: A `servista-avro-schemas` repository exists with the EventEnvelope schema (moved from lib-commons), example payload schemas, Avro codegen, Maven publishing, Apicurio registration task, and compatibility tests
**Depends on**: Gradle Platform (governance Phase 3, complete)
**Requirements**: FOUND-04
**Success Criteria** (what must be TRUE):
  1. EventEnvelope.avsc generates a Java class at eu.servista.schemas.avro.envelope.EventEnvelope
  2. AccountCreated.avsc generates a Java class at eu.servista.schemas.avro.accounts.AccountCreated
  3. OrgCreated.avsc generates a Java class at eu.servista.schemas.avro.organizations.OrgCreated
  4. All schema namespaces match their parent directory name (enforced by test)
  5. Adding a nullable field to AccountCreated is BACKWARD compatible (enforced by test)
  6. `./gradlew build` passes end-to-end (codegen + compilation + tests)
  7. RegisterSchemasTask discovers .avsc files and maps directories to Apicurio groups
  8. RegisterSchemasTask requires explicit -PregistryUrl (no accidental production writes)
  9. lib-commons depends on servista-avro-schemas for EventEnvelope (or migration documented if Phase 4 not yet executed)
Plans:
- [x] 01-01-PLAN.md -- Project scaffold, build configuration, Avro schemas, namespace and compatibility tests
- [x] 01-02-PLAN.md -- RegisterSchemasTask for Apicurio, schema discovery, lib-commons EventEnvelope migration

### Phase 2: DLQ Envelope Schema
**Goal**: DeadLetterEnvelope Avro schema exists in `src/main/avro/dlq/` with all error metadata fields and DlqStatus enum, generating a Java class consumed by servista-commons for centralized DLQ routing
**Depends on**: Phase 1 (Avro Event Infrastructure, complete)
**Requirements**: FOUND-05
**Success Criteria** (what must be TRUE):
  1. DeadLetterEnvelope.avsc generates a Java class at eu.servista.schemas.avro.dlq.DeadLetterEnvelope
  2. All 15 fields present: dlq_event_id, original_topic, original_partition, original_offset, original_key, original_value, error_type, error_message, consumer_group, retry_count, max_retries, status, correlation_id, organization_id, timestamp
  3. DlqStatus enum has PENDING and PERMANENTLY_FAILED symbols
  4. Namespace eu.servista.schemas.avro.dlq matches dlq/ directory (enforced by SchemaNamespaceConsistencyTest)
  5. `./gradlew build` passes end-to-end (codegen + compilation + tests)
Plans:
- [x] 02-01-PLAN.md -- Create DeadLetterEnvelope Avro schema

### Phase 3: Authorization Event Schemas
**Goal**: Authorization domain Avro event schemas exist for tuple lifecycle and model update events
**Depends on**: Phase 1 (Avro Event Infrastructure, complete)
**Requirements**: FOUND-07
**Success Criteria** (what must be TRUE):
  1. TupleCreated.avsc, TupleDeleted.avsc, ModelUpdated.avsc exist in src/main/avro/authorization/
  2. All schemas use namespace eu.servista.schemas.avro.authorization
  3. Schemas are valid Avro and follow Phase 1 envelope pattern conventions
Plans:
- [x] 03-01-PLAN.md -- Authorization domain Avro event schemas

## Progress

| Phase | Status | Plans | Progress |
|-------|--------|-------|----------|
| 1     | Complete | 2/2  | 100%     |
| 2     | Complete | 1/1  | 100%     |
| 3     | Complete | 1/1  | 100%     |
