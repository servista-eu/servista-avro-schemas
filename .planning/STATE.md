# State: Servista Avro Schemas

## Current Phase
Phase 1: Avro Event Infrastructure (governance Phase 5)

## Current Position
- **Current Plan:** 2 of 2 in Phase 1
- **Status:** IN PROGRESS -- Plan 01 complete, Plan 02 pending

## Status
IN PROGRESS

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

## Performance Metrics

| Phase | Plan | Duration | Tasks | Files |
|-------|------|----------|-------|-------|
| 01    | 01   | 9min     | 2     | 23    |

## Context
- Plans copied from governance Phase 5 (avro-event-infrastructure)
- Governance Phase 5 -> Component Phase 1
- Depends on: Gradle Platform (governance Phase 3, complete)

## Last Session
- **Timestamp:** 2026-03-03T16:48:52Z
- **Stopped At:** Completed 01-01-PLAN.md
