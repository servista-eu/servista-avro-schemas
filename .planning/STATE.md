---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_plan: Not started
status: completed
stopped_at: Completed 02-01-PLAN.md (Phase 2 complete)
last_updated: "2026-03-03T18:54:43.571Z"
progress:
  total_phases: 3
  completed_phases: 3
  total_plans: 4
  completed_plans: 4
---

# State: Servista Avro Schemas

## Current Phase
Phase 3: Authorization Event Schemas (governance Phase 8)

## Current Position
- **Current Plan:** Not started
- **Status:** Milestone complete

## Status
COMPLETE

## Decisions

### GOV-01: Governance Relationship
- **Decision**: Plans governed by servista/governance/mgmt-platform repo
- **Rationale**: Multi-repo governance pattern -- governance repo creates and manages roadmap/plans, component repos execute them
- **Date**: 2026-03-03

### AVRO-01: Avro-tools file discovery pattern
- **Decision**: Use walkTopDown() to collect individual .avsc files and pass to avro-tools instead of parent directory
- **Rationale**: avro-tools compile schema does not recurse into subdirectories
- **Date**: 2026-03-03

### AVRO-02: Backward compatibility test semantics
- **Decision**: Test "adding required field without default" for backward incompatibility, not "removing required field"
- **Rationale**: In Avro, removing a field from the reader IS backward compatible (reader ignores extra writer fields)
- **Date**: 2026-03-03

### AVRO-03: Convention plugin workarounds
- **Decision**: Exclude testcontainers postgresql/kafka modules and add junit-platform-launcher in consumer project
- **Rationale**: Pre-existing issues in servista.testing plugin (TC 2.x coordinate changes, missing launcher for Gradle 9.x)
- **Date**: 2026-03-03

### AVRO-04: buildSrc serialization approach
- **Decision**: Use kotlinx-serialization-json without compiler plugin in buildSrc
- **Rationale**: Only JSON DSL functions (buildJsonObject, put) needed for Apicurio REST API calls; compiler plugin not required
- **Date**: 2026-03-03

### AVRO-05: lib-commons EventEnvelope migration
- **Decision**: Remove avro-tools codegen entirely from servista-commons, consume EventEnvelope from servista-avro-schemas
- **Rationale**: EventEnvelope.avsc was the only schema in servista-commons; single source of truth in servista-avro-schemas
- **Date**: 2026-03-03

### DLQ-01: DLQ Apicurio group naming
- **Decision**: dlq/ directory maps to servista.dlq Apicurio group (same pattern as envelope/ -> servista.envelope)
- **Rationale**: Infrastructure schemas (non-domain) use servista.{dir} group without .events suffix
- **Date**: 2026-03-03

## Performance Metrics

| Phase | Plan | Duration | Tasks | Files |
|-------|------|----------|-------|-------|
| 01    | 01   | 9min     | 2     | 23    |
| 01    | 02   | 9min     | 2     | 14    |
| 02    | 01   | 3min     | 1     | 3     |

## Context
- Plans copied from governance Phase 5 (avro-event-infrastructure)
- Governance Phase 5 -> Component Phase 1
- Plans copied from governance Phase 6 (kafka-topic-provisioning, DLQ schema portion)
- Governance Phase 6 -> Component Phase 2
- Plans copied from governance Phase 8 (authorization-model, Avro schema portion)
- Governance Phase 8 -> Component Phase 3
- Depends on: Gradle Platform (governance Phase 3, complete)

## Last Session
- **Timestamp:** 2026-03-03T18:50:15Z
- **Stopped At:** Completed 02-01-PLAN.md (Phase 2 complete)
- Phase 3 plans copied from governance Phase 8 (2026-03-09)
