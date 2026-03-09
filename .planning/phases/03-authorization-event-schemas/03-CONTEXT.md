# Phase 3: Authorization Event Schemas - Context

**Gathered:** 2026-03-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Define authorization domain Avro event schemas (TupleCreated, TupleDeleted, ModelUpdated) as payload schemas that follow the Phase 1 envelope pattern. These schemas are registered in Apicurio alongside other domain event schemas and are published to the `servista.authorization.events` Kafka topic wrapped in EventEnvelope.

This phase does NOT implement the Authorization API itself or any runtime authorization enforcement. It produces only the Avro schema definitions for authorization domain events.

</domain>

<decisions>
## Implementation Decisions

### Authorization event schemas
- Phase 3 defines Avro event schemas for the authorization domain:
  - `authorization.tuple.created` -- who, which tuple, why (role creation, assignment, direct grant)
  - `authorization.tuple.deleted` -- who, which tuple, why (role update, deletion, membership removal, GC)
  - `authorization.model.updated` -- who, what changed, model version (semantic version)
- Schemas registered in Apicurio alongside other domain event schemas (Phase 1 infrastructure)
- Events emitted to `servista.authorization.events` Kafka topic
- Critical for forensic replay: "why was this access allowed at time T" requires knowing exact tuple state at time T
- These are PAYLOAD schemas wrapped by the EventEnvelope -- they do NOT duplicate envelope fields (event_id, timestamp, etc. are in the envelope)

### Avro conventions (from Phase 1)
- Namespace pattern: `eu.servista.schemas.avro.<domain>`
- Directory pattern: `src/main/avro/<domain>/<SchemaName>.avsc`
- Compatibility mode: BACKWARD for payload schemas
- The `authorization/` directory already has a `.gitkeep` from Phase 1 Plan 01

### Claude's Discretion
- Avro event schema field names and exact structure (following Phase 1 envelope pattern)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Phase 1 Avro event infrastructure**: Event envelope schema and Apicurio registry -- authorization events follow the same pattern
- **EventEnvelope.avsc**: Defines the wrapper fields (event_id, event_type, aggregate_id, organization_id, account_id, timestamp, correlation_id, causation_id, payload)

### Established Patterns
- Multi-repo with Forgejo Maven registry for artifact distribution
- Avro event schemas with BACKWARD compatibility mode registered in Apicurio
- Event envelope pattern (event_id, event_type, aggregate_id, organization_id, account_id, timestamp, correlation_id, causation_id, payload)

### Integration Points
- **Governance Phase 8 (Authorization Model)**: Defines the OpenFGA model and operational contracts that produce these events
- **Authorization API (future)**: Will emit these events when tuples are created/deleted or model is updated
- **Kafka topic**: `servista.authorization.events` (provisioned in governance Phase 6)

</code_context>

---

*Phase: 03-authorization-event-schemas*
*Context gathered: 2026-03-09*
