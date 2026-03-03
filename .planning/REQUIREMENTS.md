# Requirements: Servista Avro Schemas

**Defined:** 2026-03-03
**Core Value:** Provide a centralized, schema-first source of truth for all Avro event definitions across the Servista platform. Generated Java/Kotlin classes are published as a single Maven artifact consumed by all event-producing and event-consuming services. Includes Apicurio Registry schema registration with compatibility validation.

## v1 Requirements

Requirements extracted from governance repo for this component.

### Avro Event Infrastructure

- [x] **FOUND-04**: Avro event infrastructure — centralized schema repository with EventEnvelope and per-domain payload schemas, Avro codegen, Maven publishing, Apicurio Registry registration with FULL/BACKWARD compatibility rules

### DLQ Envelope Schema

- [x] **FOUND-05**: Kafka topic provisioning configuration for all 18 topics (9 domain + 9 DLQ) — DLQ envelope Avro schema (DeadLetterEnvelope) wrapping failed messages with error metadata for centralized DLQ routing

### Governance Mapping

| Local Req | Governance Req | Governance Phase |
|-----------|---------------|-----------------|
| FOUND-04 | FOUND-04 | 5 |
| FOUND-05 | FOUND-05 | 6 |
