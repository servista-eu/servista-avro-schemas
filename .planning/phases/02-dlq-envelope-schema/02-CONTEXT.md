# Phase 6: Kafka Topic Provisioning - Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Provision all 18 Kafka topics (9 domain + 9 DLQ) on the Strimzi-managed cluster via KafkaTopic CRDs in the Servista IaC governance repo. Define KafkaUser CRDs for all known services with mTLS and scoped ACLs. Add DLQ envelope Avro schema to servista-avro-schemas. Add topic creation utility and DLQ routing to servista-commons. Verify all topics via Testcontainers CI tests and recurring Tekton cluster checks with ACL verification.

</domain>

<decisions>
## Implementation Decisions

### Provisioning mechanism
- **Strimzi KafkaTopic CRDs** for the 18 known topics, deployed via ArgoCD GitOps
- **Kafka AdminClient** for topics that need to be created dynamically by services at runtime
- CRDs live in **iac-governance repo** (`/home/sven/work/clients/hestia/projects/servista/governance/iac-governance`) under `apps/streaming/kafka-topics/`
- **Kustomize overlays** for dev + staging + prod environments
- Identical base config, override in prod overlay when multi-node scaling happens
- Dedicated **ArgoCD Application** resource for Kafka topic/user management
- ArgoCD sync-waves: wave 2 for topics, wave 3 for users (cluster is wave 1 in mspcloud)
- One YAML file per KafkaTopic CRD, one YAML file per KafkaUser CRD

### KafkaUser per service
- **One KafkaUser per service repo** with mTLS certificates (~15+ users)
- Naming convention: `servista-{repo-name}` (e.g., `servista-api-accounts`, `servista-svc-audit-writer`, `servista-adapter-xurrent`)
- **Full topic-service mapping** with producer/consumer ACLs derived from architecture
- Services deploy to `servista` namespace (NOT kafka namespace) — credential distribution mechanism is Claude's discretion
- Dynamic topic creation: services use their own KafkaUser credentials (no shared admin user). ACLs include CREATE permission on relevant prefixes where needed
- Consumer group naming convention: Claude's discretion, enforced via ACLs

### Topic configuration
- **Uniform 3 partitions** for all 9 domain topics
- **7-day retention** (168 hours, matches cluster default)
- **Delete** cleanup policy
- **Replication factor 1** in base (single-node cluster), override in prod overlay when scaling
- **min.insync.replicas = 1** (cluster default)
- **1MB max message size** (Kafka default)
- **Compression: producer decides** (no topic-level enforcement)
- **Default key: organization_id** for tenant-scoped partition ordering. Services can use aggregate_id or domain-specific keys when entity-level ordering matters more. This is a documented recommendation, not a Kafka enforcement.
- **Reserved prefix: `servista.internal.*`** for Kafka Streams internal topics (changelog, repartition). KafkaUser ACLs allow CREATE on this prefix for services that use Kafka Streams.

### DLQ configuration
- **1 partition** per DLQ topic (low volume expected)
- **30-day retention** (longer investigation window than domain topics)
- Delete cleanup policy, replication factor 1 in base
- 30-day retention then messages are gone — ImmuDB audit trail is the permanent record. DLQ archival to object storage deferred to future if compliance requires it.

### DLQ envelope and routing
- **DLQ envelope Avro schema** defined in servista-avro-schemas repo — wraps original message with error metadata: error_type, error_message, original_topic, consumer_group, retry_count, timestamp, correlation context
- **Centralized DLQ routing** via servista-commons consumer framework — services throw exceptions, the commons consumer wrapper routes failed messages to the correct DLQ. Changes to DLQ behavior only happen in one place.
- **Configurable max retry count** (default 3). After max retries, message marked as `permanently_failed` and no longer eligible for reprocessing.
- **Reprocessing via quarantine UI** (Phase 50) with operator approval step. Audit-logged.
- **Alerting hooks specced**: DLQ consumer lag > 0 triggers an alert. Implementation in Phase 50 (Pipeline Operations) but expectation documented here.

### Kubernetes labels
- Standard Servista labels on all CRDs: `app.kubernetes.io/part-of: servista`, `servista.eu/domain: {domain}`, `servista.eu/topic-type: domain|dlq`

### Verification
- **Testcontainers + embedded Kafka + Apicurio** for CI: full Avro roundtrip (register schema, produce, consume, deserialize) — covers all 18 topics
- **Recurring Tekton CI check** against actual mspcloud-kafka cluster after CRD deployment
- **ACL verification**: positive (service CAN access own topics) and negative (service CANNOT access other service's topics) checks
- Cluster connection details available in mspcloud repo at `/home/sven/work/clients/hestia/projects/mspcloud`

### Commons topic utility
- **Topic creation utility** in servista-commons — flexible, composable API for dynamic topic creation
- **DLQ consumer wrapper** in servista-commons — ServistaKafkaConsumer that auto-deserializes, restores ServistaContext, routes failures to DLQ with envelope
- **Cluster validation** on initialization — verify cluster ID matches expected config before creating topics
- Integrates with Phase 4's ServistaContext for correlation tracking in DLQ envelopes

### Governance cascade
- **Single spec, per-repo sections** — one governance Phase 6 spec with deliverables per target repo
- Plans numbered sequentially (06-01, 06-02, etc.) with target repo tracked per plan
- Target repos: iac-governance, servista-avro-schemas, servista-commons
- Plans copied to target repos with local phase numbering (iac-governance Phase 9, servista-avro-schemas and servista-commons use next sequential)
- **Parallel execution** where possible — IaC CRDs and DLQ schema/commons utility proceed independently. Only cluster verification depends on CRDs being deployed.

### Claude's Discretion
- Credential distribution mechanism (cross-namespace Secret copy from kafka to servista namespace)
- Complete topic-service mapping matrix with producer/consumer assignments per service
- Consumer group naming convention and ACL enforcement
- KafkaUser ACL structure supporting dynamic topic creation on relevant prefixes
- Topic directory vs user directory structure in iac-governance (same vs separate)
- Commons utility API design (convention-enforcing vs thin wrapper vs config-driven — design for flexibility/composability)
- ServistaContext integration level in DLQ routing (full vs optional)
- Whether to include producer wrapper alongside consumer wrapper in commons
- Cross-reference of topic-to-Apicurio schema mapping in spec
- Connection config boundary between Phase 6 (provisioning) and Phase 7 (service scaffold)
- APISIX, Token Broker, and adapter KafkaUser Kafka access patterns
- Pipeline service (landing zone, silver, gold) topic access mapping

</decisions>

<specifics>
## Specific Ideas

- User explicitly chose per-service KafkaUsers over domain grouping or shared credentials — tight ACLs, clear ownership per repo
- User wants full topic-service mapping now, not incremental — KafkaUser ACLs should be derived from a complete producer/consumer matrix
- Centralized DLQ routing was chosen specifically because "it prevents us from having to modify service code whenever something about the DLQ setup changes"
- DLQ reprocessing must go through the quarantine investigation UI (Phase 50) with operator approval — no direct replay commands
- Cluster validation before topic creation was specifically requested as a safety mechanism
- User noted DLQ archival to object storage "might become important in the future" — captured for later but not in scope now

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Kafka cluster config** (mspcloud repo): `mspcloud-kafka` cluster in `kafka` namespace, Kafka 4.1.1, KRaft mode, TLS+mTLS, `auto.create.topics.enable: false`, entity operator enabled, default 3 partitions, replication factor 1
- **ADR-012**: Complete list of 9 domain topics with naming convention `servista.{domain}.events` + DLQ `servista.{domain}.events.dlq`
- **Phase 4 servista-commons**: EventEnvelope serializer/deserializer, ServistaContext with correlation propagation, Kafka consumer wrapper restoring context from envelope
- **Phase 5 servista-avro-schemas**: EventEnvelope.avsc + per-domain payload schemas, Apicurio registration Gradle task, domain directory structure mapping 1:1 to Kafka topics
- **`servista.kafka-producer` and `servista.kafka-consumer` convention plugins** (Phase 3): Provide Kafka client dependencies

### Established Patterns
- **IaC governance repo** uses `apps/{category}/{component}/` with Kustomize base + overlays (dev, staging, prod) and ArgoCD Applications with sync-waves
- **Strimzi KafkaUser** generates mTLS certificates as Kubernetes Secrets in the kafka namespace
- **Multi-repo governance cascade**: plans created in governance repo, copied to target repos with local phase numbering
- **Phase 5 Apicurio organization**: Group ID = topic name, Artifact ID = event type (cross-references topic names)

### Integration Points
- **iac-governance repo** (`/home/sven/work/clients/hestia/projects/servista/governance/iac-governance`): Already has .planning/ with 8 completed phases. Kafka topics become Phase 9 locally.
- **servista-avro-schemas repo**: DLQ envelope schema added alongside existing domain payload schemas
- **servista-commons repo**: Topic creation utility and DLQ consumer wrapper added to existing library
- **Phase 7 (Service Scaffold)**: Will wire commons Kafka utilities into Ktor service template
- **Phase 9 (CI Pipeline)**: Tekton recurring cluster verification job
- **Phase 50 (Pipeline Operations)**: Quarantine investigation UI consumes DLQ messages, implements reprocessing with approval

</code_context>

<deferred>
## Deferred Ideas

- **DLQ archival to object storage** — Move old DLQ messages to MinIO/S3 before retention expires. Not needed now (ImmuDB has permanent audit record), but may become important for compliance.

</deferred>

---

*Phase: 06-kafka-topic-provisioning*
*Context gathered: 2026-03-03*
