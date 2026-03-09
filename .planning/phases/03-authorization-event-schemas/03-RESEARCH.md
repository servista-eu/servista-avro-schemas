# Phase 3: Authorization Event Schemas - Research

**Researched:** 2026-03-09
**Domain:** Avro event schemas for authorization domain events
**Confidence:** HIGH

## Summary

Phase 3 defines Avro event payload schemas for the authorization domain: TupleCreated, TupleDeleted, and ModelUpdated. These schemas follow the Phase 1 envelope pattern established in the servista-avro-schemas repository -- they are PAYLOAD schemas wrapped by the EventEnvelope and do NOT duplicate envelope fields.

The schemas use namespace `eu.servista.schemas.avro.authorization` and are placed in `src/main/avro/authorization/` following the established directory convention. Compatibility mode is BACKWARD for payload schemas. The authorization directory already has a `.gitkeep` placeholder from Phase 1.

**Primary recommendation:** Create 3 `.avsc` files following the established Phase 1 conventions. Use enum types for reason/change_type fields to provide type safety. Include model_version in tuple schemas for forensic replay capability.

<user_constraints>
## User Constraints (from governance Phase 8 CONTEXT.md)

### Locked Decisions
- Avro event schemas: authorization.tuple.created, authorization.tuple.deleted, authorization.model.updated
- Schemas registered in Apicurio alongside other domain event schemas
- Events emitted to `servista.authorization.events` Kafka topic
- Critical for forensic replay: model_version must be included
- These are PAYLOAD schemas wrapped by the EventEnvelope

### Claude's Discretion
- Avro event schema field names and exact structure (following Phase 1 envelope pattern)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| FOUND-07 | OpenFGA authorization model definition file (testable `.fga`) with all types, relations, and conditions | Avro event schemas follow Phase 1 envelope pattern. Authorization domain schemas define the audit event payloads for tuple lifecycle and model updates. |
</phase_requirements>

## Standard Stack

### Core
| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| Apache Avro | 1.12.1 | Event schema definition | Established in Phase 1 for all Servista event schemas |
| Apicurio Registry | 3.x | Schema registration | For publishing authorization event schemas |

## Architecture Patterns

### Phase 1 Avro Conventions
```
src/main/avro/
  envelope/
    EventEnvelope.avsc      # Wrapper schema (Phase 1)
  accounts/
    AccountCreated.avsc     # Example payload schema (Phase 1)
  organizations/
    OrgCreated.avsc         # Example payload schema (Phase 1)
  authorization/
    TupleCreated.avsc       # Phase 3 - NEW
    TupleDeleted.avsc       # Phase 3 - NEW
    ModelUpdated.avsc        # Phase 3 - NEW
  dlq/
    DeadLetterEnvelope.avsc # DLQ schema (Phase 2)
```

### Avro Event Schema Pattern (Authorization Domain)
```json
// File: src/main/avro/authorization/TupleCreated.avsc
{
  "namespace": "eu.servista.schemas.avro.authorization",
  "type": "record",
  "name": "TupleCreated",
  "doc": "Payload for authorization tuple creation events. Published to servista.authorization.events topic wrapped in EventEnvelope.",
  "fields": [
    {"name": "tuple_user", "type": "string", "doc": "Tuple user field (e.g., user:123)"},
    {"name": "tuple_relation", "type": "string", "doc": "Tuple relation (e.g., viewer, assignee)"},
    {"name": "tuple_object", "type": "string", "doc": "Tuple object field (e.g., resource:456)"},
    {"name": "condition_name", "type": ["null", "string"], "default": null, "doc": "Condition name if conditional tuple"},
    {"name": "reason", "type": {"type": "enum", "name": "TupleCreationReason", "symbols": [...]}, "doc": "Why the tuple was created"},
    {"name": "model_version", "type": "string", "doc": "Model version for forensic replay"}
  ]
}
```

## Sources

### Primary (HIGH confidence)
- Phase 1 Avro Event Infrastructure (this repo, Phase 1) - Event envelope pattern, Avro schema conventions, Apicurio registration
- Governance Phase 8 CONTEXT.md - Authorization event schema requirements, forensic replay requirements
- Governance Phase 8 RESEARCH.md - Avro event schema pattern code example

## Metadata

**Confidence breakdown:**
- Avro conventions: HIGH - Phase 1 established all patterns, this phase follows them exactly
- Schema structure: HIGH - Field definitions specified in governance Phase 8 CONTEXT.md

**Research date:** 2026-03-09
**Valid until:** 2026-04-09 (stable domain, following established patterns)
