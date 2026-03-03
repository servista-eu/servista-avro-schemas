# Phase 6: Kafka Topic Provisioning - Research

**Researched:** 2026-03-03
**Domain:** Strimzi KafkaTopic/KafkaUser CRDs, Kustomize GitOps, DLQ Avro envelope, commons Kafka utilities
**Confidence:** HIGH

## Summary

Phase 6 provisions all 18 Kafka topics (9 domain + 9 DLQ) via Strimzi KafkaTopic CRDs in the iac-governance repo, defines KafkaUser CRDs with mTLS and scoped ACLs for all services, adds a DLQ envelope Avro schema to servista-avro-schemas, and builds topic creation/DLQ routing utilities in servista-commons. The work spans three target repos: iac-governance (CRDs + ArgoCD), servista-avro-schemas (DLQ envelope schema), and servista-commons (Kafka utilities).

The existing infrastructure is well-prepared. The mspcloud Kafka cluster runs Kafka 4.1.1 on Strimzi with KRaft mode, `auto.create.topics.enable: false`, Simple ACL authorization, and an external clients CA from OpenBao PKI (`clientsCa.generateCertificateAuthority: false`). The entity operator (topic operator + user operator) is already deployed. The iac-governance repo uses the app-of-apps pattern with Kustomize base/overlays and ArgoCD sync-waves. There is a critical nuance: because the cluster uses an external clients CA, KafkaUser resources must use `authentication.type: tls-external` (not `tls`), meaning Strimzi's user operator will NOT generate certificates. Services obtain their mTLS certificates from OpenBao PKI via the Vault Agent injector pattern already established by Apicurio.

The DLQ envelope schema extends the existing Avro schema infrastructure from Phase 5, adding a `DeadLetterEnvelope.avsc` that wraps the original event bytes with error metadata (error_type, error_message, original_topic, consumer_group, retry_count, timestamp, correlation context). The commons DLQ consumer wrapper (`ServistaKafkaConsumer`) auto-deserializes EventEnvelopes, restores ServistaContext, and routes failures to DLQ with the dead letter envelope -- building directly on Phase 4's EventContextHandler and EventEnvelopeSerializer/Deserializer.

**Primary recommendation:** Implement CRDs as one-file-per-topic and one-file-per-user in iac-governance under `apps/streaming/kafka-topics/`, with a dedicated ArgoCD Application using sync-wave 2 (topics) and sync-wave 3 (users). Use `tls-external` authentication for all KafkaUser resources. Build the DLQ envelope as a sibling to EventEnvelope in servista-avro-schemas. Build the commons utility with a composable, config-driven API that integrates with ServistaContext.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Strimzi KafkaTopic CRDs** for the 18 known topics, deployed via ArgoCD GitOps
- **Kafka AdminClient** for topics that need to be created dynamically by services at runtime
- CRDs live in **iac-governance repo** (`/home/sven/work/clients/hestia/projects/servista/governance/iac-governance`) under `apps/streaming/kafka-topics/`
- **Kustomize overlays** for dev + staging + prod environments
- Identical base config, override in prod overlay when multi-node scaling happens
- Dedicated **ArgoCD Application** resource for Kafka topic/user management
- ArgoCD sync-waves: wave 2 for topics, wave 3 for users (cluster is wave 1 in mspcloud)
- One YAML file per KafkaTopic CRD, one YAML file per KafkaUser CRD
- **One KafkaUser per service repo** with mTLS certificates (~15+ users)
- Naming convention: `servista-{repo-name}` (e.g., `servista-api-accounts`, `servista-svc-audit-writer`, `servista-adapter-xurrent`)
- **Full topic-service mapping** with producer/consumer ACLs derived from architecture
- Services deploy to `servista` namespace (NOT kafka namespace)
- Dynamic topic creation: services use their own KafkaUser credentials (no shared admin user). ACLs include CREATE permission on relevant prefixes where needed
- **Uniform 3 partitions** for all 9 domain topics
- **7-day retention** (168 hours, matches cluster default)
- **Delete** cleanup policy
- **Replication factor 1** in base (single-node cluster), override in prod overlay when scaling
- **min.insync.replicas = 1** (cluster default)
- **1MB max message size** (Kafka default)
- **Compression: producer decides** (no topic-level enforcement)
- **Default key: organization_id** for tenant-scoped partition ordering (documented recommendation, not enforced)
- **Reserved prefix: `servista.internal.*`** for Kafka Streams internal topics (changelog, repartition)
- **1 partition** per DLQ topic (low volume expected)
- **30-day retention** for DLQ topics
- Delete cleanup policy for DLQ, replication factor 1 in base
- **DLQ envelope Avro schema** defined in servista-avro-schemas repo -- wraps original message with error metadata
- **Centralized DLQ routing** via servista-commons consumer framework
- **Configurable max retry count** (default 3). After max retries, message marked as `permanently_failed`
- **Reprocessing via quarantine UI** (Phase 50) with operator approval step
- **Alerting hooks specced**: DLQ consumer lag > 0 triggers alert (implementation Phase 50)
- Standard Servista labels on all CRDs: `app.kubernetes.io/part-of: servista`, `servista.eu/domain: {domain}`, `servista.eu/topic-type: domain|dlq`
- **Testcontainers + embedded Kafka + Apicurio** for CI roundtrip tests
- **Recurring Tekton CI check** against actual cluster after CRD deployment
- **ACL verification**: positive and negative checks
- **Topic creation utility** in servista-commons -- flexible, composable API for dynamic topic creation
- **DLQ consumer wrapper** in servista-commons -- auto-deserializes, restores ServistaContext, routes failures to DLQ
- **Cluster validation** on initialization -- verify cluster ID matches expected config before creating topics
- **Single spec, per-repo sections** -- plans numbered sequentially with target repo tracked per plan
- Target repos: iac-governance, servista-avro-schemas, servista-commons
- **Parallel execution** where possible

### Claude's Discretion
- Credential distribution mechanism (cross-namespace Secret copy from kafka to servista namespace)
- Complete topic-service mapping matrix with producer/consumer assignments per service
- Consumer group naming convention and ACL enforcement
- KafkaUser ACL structure supporting dynamic topic creation on relevant prefixes
- Topic directory vs user directory structure in iac-governance (same vs separate)
- Commons utility API design (convention-enforcing vs thin wrapper vs config-driven)
- ServistaContext integration level in DLQ routing (full vs optional)
- Whether to include producer wrapper alongside consumer wrapper in commons
- Cross-reference of topic-to-Apicurio schema mapping in spec
- Connection config boundary between Phase 6 (provisioning) and Phase 7 (service scaffold)
- APISIX, Token Broker, and adapter KafkaUser Kafka access patterns
- Pipeline service (landing zone, silver, gold) topic access mapping

### Deferred Ideas (OUT OF SCOPE)
- **DLQ archival to object storage** -- Move old DLQ messages to MinIO/S3 before retention expires. Not needed now (ImmuDB has permanent audit record), but may become important for compliance.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| FOUND-05 | Kafka topic provisioning configuration for all 18 topics (9 domain + 9 DLQ) | Full research coverage: Strimzi KafkaTopic CRD v1 spec verified, KafkaUser with tls-external auth for external CA clusters, Kustomize base/overlay pattern matching existing iac-governance structure, ArgoCD sync-wave ordering, DLQ envelope Avro schema design, commons topic creation utility and DLQ consumer wrapper patterns, Testcontainers Kafka module for CI verification |
</phase_requirements>

## Standard Stack

### Core
| Library/Tool | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Strimzi KafkaTopic CRD | kafka.strimzi.io/v1 | Declarative topic provisioning via Kubernetes | Already deployed on cluster; entity operator manages topic lifecycle |
| Strimzi KafkaUser CRD | kafka.strimzi.io/v1 | Declarative per-service ACL management | Entity operator already running; Simple ACL authorization enabled |
| Kustomize | v1beta1 | Base/overlay structure for environment-specific config | Established pattern in iac-governance repo |
| ArgoCD Application | argoproj.io/v1alpha1 | GitOps deployment of CRDs | App-of-apps pattern already in place |
| Avro | 1.12.1 | DLQ envelope schema definition | Platform standard; existing EventEnvelope.avsc pattern |
| kafka-clients | 4.1.1 (from version catalog) | AdminClient for dynamic topic creation utility | Matches cluster version; already in servista.kafka-producer plugin |
| Testcontainers Kafka | 2.0.3 | CI tests with embedded Kafka | Already in version catalog; established testing pattern |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx-coroutines-core | 1.10.2 | Coroutine-based consumer wrapper | Existing dependency in servista-commons |
| kotest-assertions | 6.1.4 | Test assertions | Existing test dependency pattern |
| JUnit 5 | 5.14.2 | Test framework | Platform standard |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Strimzi KafkaTopic CRD | Terraform Kafka provider | Extra toolchain; Strimzi CRDs are native to the K8s-managed cluster |
| KafkaUser tls-external | KafkaUser tls (auto-cert) | Not possible: cluster uses external CA from OpenBao PKI |
| Strimzi Access Operator | Manual Secret copy/sync | Access Operator is cleaner but only v0.2.0 (immature); manual copy via init script or Kubernetes Replicator is simpler and proven |
| Per-file KafkaTopic CRDs | Single combined YAML | Per-file enables granular PR review, clear git blame, easier diff |
| kafka-clients AdminClient | Confluent Admin wrapper | No added value; AdminClient API is sufficient for topic creation |

## Architecture Patterns

### Recommended IaC Directory Structure

```
apps/streaming/kafka-topics/
  application.yaml                    # ArgoCD Application
  base/
    kustomization.yaml                # Lists all topic + user YAMLs
    topics/
      servista.accounts.events.yaml
      servista.accounts.events.dlq.yaml
      servista.authorization.events.yaml
      servista.authorization.events.dlq.yaml
      servista.billing.events.yaml
      servista.billing.events.dlq.yaml
      servista.egress.events.yaml
      servista.egress.events.dlq.yaml
      servista.iam.events.yaml
      servista.iam.events.dlq.yaml
      servista.integrations.events.yaml
      servista.integrations.events.dlq.yaml
      servista.organizations.events.yaml
      servista.organizations.events.dlq.yaml
      servista.subscriptions.events.yaml
      servista.subscriptions.events.dlq.yaml
      servista.usage.events.yaml
      servista.usage.events.dlq.yaml
    users/
      servista-api-accounts.yaml
      servista-api-organizations.yaml
      servista-api-authorization.yaml
      servista-api-iam.yaml
      servista-api-subscriptions.yaml
      servista-api-billing.yaml
      servista-api-audit-query.yaml
      servista-api-canonical-data.yaml
      servista-api-credentials.yaml
      servista-svc-token-broker.yaml
      servista-svc-audit-writer.yaml
      servista-svc-rmp-consumer.yaml
      servista-adapter-xurrent.yaml
      servista-adapter-salesforce.yaml
      servista-adapter-lansweeper.yaml
      servista-pipeline-silver.yaml
      servista-pipeline-landing-zone.yaml
      servista-pipeline-gold.yaml
      servista-apisix.yaml
  overlays/
    dev/
      kustomization.yaml              # patches: none (identical to base)
    staging/
      kustomization.yaml              # patches: none (identical to base)
    prod/
      kustomization.yaml              # patches: replication factor, min.insync.replicas
```

**Rationale for separate `topics/` and `users/` directories:** Clear separation of concerns in git history. Topic CRDs change rarely (new domains). User CRDs change more often (new services, ACL adjustments). ArgoCD sync-waves differentiate them (wave 2 for topics, wave 3 for users).

### Pattern 1: KafkaTopic CRD (Domain Topic)
**What:** Declarative Kafka topic with Strimzi entity operator
**When to use:** All 9 domain topics
**Example:**
```yaml
# Source: Strimzi docs https://strimzi.io/docs/operators/latest/configuring.html
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata:
  name: servista.accounts.events
  namespace: kafka
  labels:
    strimzi.io/cluster: mspcloud-kafka
    app.kubernetes.io/part-of: servista
    servista.eu/domain: accounts
    servista.eu/topic-type: domain
  annotations:
    argocd.argoproj.io/sync-wave: "2"
spec:
  partitions: 3
  replicas: 1
  config:
    retention.ms: "604800000"     # 7 days (168 hours)
    cleanup.policy: "delete"
    min.insync.replicas: "1"
```

### Pattern 2: KafkaTopic CRD (DLQ Topic)
**What:** Dead letter queue topic with longer retention
**When to use:** All 9 DLQ topics
**Example:**
```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata:
  name: servista.accounts.events.dlq
  namespace: kafka
  labels:
    strimzi.io/cluster: mspcloud-kafka
    app.kubernetes.io/part-of: servista
    servista.eu/domain: accounts
    servista.eu/topic-type: dlq
  annotations:
    argocd.argoproj.io/sync-wave: "2"
spec:
  partitions: 1
  replicas: 1
  config:
    retention.ms: "2592000000"    # 30 days
    cleanup.policy: "delete"
    min.insync.replicas: "1"
```

### Pattern 3: KafkaUser CRD with tls-external and Scoped ACLs
**What:** Per-service mTLS identity with least-privilege ACLs
**When to use:** Every service that produces or consumes Kafka events
**Critical:** Must use `tls-external` (NOT `tls`) because cluster has `clientsCa.generateCertificateAuthority: false`
**Example:**
```yaml
# Source: Strimzi docs https://strimzi.io/docs/operators/latest/configuring.html
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata:
  name: servista-api-accounts
  namespace: kafka
  labels:
    strimzi.io/cluster: mspcloud-kafka
    app.kubernetes.io/part-of: servista
    servista.eu/service: api-accounts
  annotations:
    argocd.argoproj.io/sync-wave: "3"
spec:
  authentication:
    type: tls-external
  authorization:
    type: simple
    acls:
      # Produce to accounts domain topic
      - resource:
          type: topic
          name: servista.accounts.events
          patternType: literal
        operations:
          - Write
          - Describe
      # Produce to accounts DLQ
      - resource:
          type: topic
          name: servista.accounts.events.dlq
          patternType: literal
        operations:
          - Write
          - Describe
      # Consumer group
      - resource:
          type: group
          name: servista-api-accounts
          patternType: prefix
        operations:
          - Read
          - Describe
```

### Pattern 4: Consumer Group Naming Convention
**Recommendation:** `servista-{repo-name}` as consumer group prefix, enforced via ACL with `patternType: prefix`. This allows services to have sub-groups (e.g., `servista-api-accounts-retry`, `servista-api-accounts-dlq-reprocessor`) while keeping ACLs tight.

### Pattern 5: Credential Distribution (OpenBao PKI)
**What:** Services obtain mTLS certificates from OpenBao PKI via Vault Agent injector
**Why:** The cluster uses an external CA from OpenBao (`clientsCa.generateCertificateAuthority: false`). Strimzi's user operator does NOT generate certificates for `tls-external` users. Certificates must be signed by the same CA chain that `setup-clients-ca.sh` exported to Strimzi.

**Mechanism:** Each service's Kubernetes Deployment gets OpenBao Agent annotations that issue a certificate from the `kafka-client-cert` PKI role with `common_name=servista-{repo-name}`. The CN in the certificate must match the KafkaUser name for ACL authorization to work.

This is the same pattern already used by the Apicurio deployment (see `apps/streaming/kafka/overlays/prod/apicurio-deployment.yaml` in mspcloud), adapted for Kafka client authentication rather than PostgreSQL client authentication. A `kafka-client-cert` PKI role should already exist or needs to be created in OpenBao, allowing CN values matching `servista-*`.

**Connection config boundary:** Phase 6 defines the KafkaUser CRDs and documents the credential distribution pattern. Phase 7 (Service Scaffold) implements the actual OpenBao Agent annotations and Kafka client configuration in the service template.

### Pattern 6: DLQ Envelope Avro Schema
**What:** Wrapper schema for failed messages routed to DLQ
**Example:**
```json
{
  "namespace": "eu.servista.schemas.avro.dlq",
  "type": "record",
  "name": "DeadLetterEnvelope",
  "doc": "Wraps a failed message with error metadata for DLQ routing. Registered in Apicurio with FULL compatibility.",
  "fields": [
    {"name": "dlq_event_id",    "type": "long",   "doc": "Snowflake ID for this DLQ entry"},
    {"name": "original_topic",  "type": "string",  "doc": "Source topic the message was consumed from"},
    {"name": "original_partition", "type": "int",   "doc": "Partition number in source topic"},
    {"name": "original_offset", "type": "long",    "doc": "Offset in source topic partition"},
    {"name": "original_key",    "type": ["null", "bytes"], "default": null, "doc": "Original message key bytes"},
    {"name": "original_value",  "type": "bytes",   "doc": "Original message value bytes (full EventEnvelope)"},
    {"name": "error_type",      "type": "string",  "doc": "Exception class name or error category"},
    {"name": "error_message",   "type": "string",  "doc": "Human-readable error description"},
    {"name": "consumer_group",  "type": "string",  "doc": "Consumer group that failed to process"},
    {"name": "retry_count",     "type": "int",     "doc": "Number of processing attempts before DLQ routing"},
    {"name": "max_retries",     "type": "int",     "doc": "Configured max retry limit"},
    {"name": "status",          "type": {"type": "enum", "name": "DlqStatus", "symbols": ["PENDING", "PERMANENTLY_FAILED"]}, "doc": "Whether message is eligible for reprocessing"},
    {"name": "correlation_id",  "type": ["null", "string"], "default": null, "doc": "Correlation ID from original EventEnvelope if extractable"},
    {"name": "organization_id", "type": ["null", "long"],   "default": null, "doc": "Organization ID from original EventEnvelope if extractable"},
    {"name": "timestamp",       "type": "long",    "doc": "Unix epoch millis when DLQ entry was created"}
  ]
}
```

### Pattern 7: Commons ServistaKafkaConsumer Wrapper
**What:** Consumer framework that handles deserialization, context restoration, and DLQ routing
**Design approach:** Config-driven with composable components

```kotlin
// Conceptual API (Claude's discretion on exact design)
class ServistaKafkaConsumer<T>(
    private val config: ServistaConsumerConfig,
    private val handler: suspend (EventEnvelope) -> Unit
) {
    // Auto-deserializes EventEnvelope
    // Restores ServistaContext via withEventContext()
    // On exception: retry up to config.maxRetries
    // After max retries: wrap in DeadLetterEnvelope, produce to DLQ topic
    // DLQ topic derived from source topic: "{sourceTopic}.dlq"
}

data class ServistaConsumerConfig(
    val bootstrapServers: String,
    val groupId: String,
    val topics: List<String>,
    val maxRetries: Int = 3,
    // ... additional config
)
```

### Pattern 8: Commons Topic Creation Utility
**What:** Programmatic topic creation via AdminClient for dynamic topics
**When to use:** Kafka Streams internal topics, any runtime-created topics

```kotlin
// Conceptual API
class ServistaTopicManager(
    private val adminClient: AdminClient,
    private val expectedClusterId: String? = null
) {
    // Validates cluster ID before operations (safety mechanism)
    // Creates topics with platform defaults (retention, partitions, etc.)
    // Idempotent: no-op if topic already exists
    suspend fun ensureTopics(topics: List<TopicSpec>): Unit
}
```

### Anti-Patterns to Avoid
- **Shared KafkaUser across services:** Violates least-privilege; ACL blast radius is too wide. One KafkaUser per service repo.
- **Using `authentication.type: tls` with external CA cluster:** Strimzi will NOT generate certificates. Must use `tls-external`. This is the most critical anti-pattern to avoid.
- **Wildcard ACLs (`name: "*"`):** Never grant access to all topics. Use literal names for known topics and prefix patterns only for Kafka Streams internal topics.
- **Topic auto-creation (`auto.create.topics.enable: true`):** Already disabled on the cluster. Never re-enable. All topics must be explicitly provisioned.
- **Putting KafkaTopic CRDs in the mspcloud repo:** They belong in iac-governance (Servista's own IaC), not the shared cluster repo.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Topic provisioning | Custom scripts calling `kafka-topics.sh` | Strimzi KafkaTopic CRD | Entity operator handles lifecycle, drift detection, and reconciliation |
| ACL management | AdminClient `createAcls()` calls in code | Strimzi KafkaUser CRD ACLs | GitOps-managed, auditable, declarative |
| Certificate generation | Custom PKI scripts | OpenBao PKI + Vault Agent injector | Established pattern in mspcloud; handles rotation, renewal |
| Secret cross-namespace sync | Manual `kubectl cp` scripts | OpenBao Agent Injector (per-service annotations) | Certificates injected directly into service pods; no cross-namespace Secret copying needed |
| DLQ routing logic | Per-service DLQ handling | servista-commons ServistaKafkaConsumer wrapper | Centralized, consistent error handling; changes in one place |
| Consumer group naming | Ad-hoc names per service | Convention-enforced `servista-{repo-name}` prefix | Consistent ACL enforcement, easy monitoring |

**Key insight:** With `tls-external` authentication and OpenBao PKI, there are no Strimzi-managed secrets to copy across namespaces. Each service gets its certificate directly from OpenBao via the Vault Agent injector pattern. This eliminates the cross-namespace Secret problem entirely.

## Common Pitfalls

### Pitfall 1: Using `tls` Instead of `tls-external` Authentication
**What goes wrong:** KafkaUser with `authentication.type: tls` expects Strimzi's user operator to sign a certificate using the clients CA private key. When `clientsCa.generateCertificateAuthority: false`, the operator doesn't have the private key and cannot generate certificates. The KafkaUser resource enters a failed state.
**Why it happens:** Default Strimzi tutorials show `type: tls`. The `tls-external` variant is less documented.
**How to avoid:** Always use `tls-external` for this cluster. The certificate CN must match the KafkaUser name.
**Warning signs:** KafkaUser status shows `NotReady` with certificate generation errors.

### Pitfall 2: Missing `strimzi.io/cluster` Label
**What goes wrong:** KafkaTopic or KafkaUser CRD is created but the entity operator ignores it. Topic/user never appears on the cluster.
**Why it happens:** The entity operator only watches resources with the correct cluster label.
**How to avoid:** Every CRD must have `labels: strimzi.io/cluster: mspcloud-kafka`.
**Warning signs:** `kubectl get kafkatopic` shows the resource but topic doesn't exist in Kafka.

### Pitfall 3: Sync-Wave Ordering with User Operator
**What goes wrong:** KafkaUser CRDs are applied at the same sync-wave as KafkaTopic CRDs. Users reference topics that don't exist yet. While this doesn't cause Strimzi errors (ACLs are independent of topic existence), it's operationally confusing.
**Why it happens:** Default assumption is all CRDs can sync simultaneously.
**How to avoid:** Topics at sync-wave 2, users at sync-wave 3. Cluster is wave 1 in mspcloud.

### Pitfall 4: DLQ Topic Naming Mismatch
**What goes wrong:** The DLQ consumer wrapper computes the DLQ topic name differently from what the KafkaTopic CRD defines. Messages fail to produce to DLQ.
**Why it happens:** Convention not enforced. Someone uses `{topic}-dlq` instead of `{topic}.dlq`.
**How to avoid:** Single source of truth for topic naming in the commons library. The naming convention is `servista.{domain}.events.dlq` (dot separator, not hyphen). Enforce via a `TopicNames` constants object in servista-commons.

### Pitfall 5: Certificate CN Mismatch
**What goes wrong:** Service connects to Kafka but all operations are denied. The mTLS handshake succeeds (certificate is valid) but ACL checks fail because the KafkaUser ACL is associated with `CN=servista-api-accounts` but the certificate was issued with a different CN.
**Why it happens:** OpenBao PKI role issues a certificate with CN that doesn't match the KafkaUser name.
**How to avoid:** Document and enforce: OpenBao PKI `common_name` parameter MUST match the KafkaUser `metadata.name`. Phase 7 template should hard-code this.
**Warning signs:** `ClusterAuthorizationException` in service logs despite successful TLS connection.

### Pitfall 6: Kafka Streams Internal Topics and ACLs
**What goes wrong:** Service using Kafka Streams fails to create internal changelog/repartition topics.
**Why it happens:** Service's KafkaUser ACLs only grant access to explicit domain topics, not the `servista.internal.*` prefix used by Kafka Streams.
**How to avoid:** For services that use Kafka Streams (pipeline-silver is the primary one), include ACL rules with `patternType: prefix` on `name: servista.internal.{application-id}` granting Create, Read, Write, Delete, Describe operations.

### Pitfall 7: Testcontainers Module Name Change in TC 2.x
**What goes wrong:** Build fails with dependency resolution errors for `org.testcontainers:kafka`.
**Why it happens:** Testcontainers 2.x renamed `org.testcontainers:kafka` to `org.testcontainers:testcontainers-kafka`.
**How to avoid:** Use `org.testcontainers:testcontainers-kafka:2.0.3`. The existing servista-commons build.gradle.kts already has the exclusion pattern for the old module name.

## Code Examples

### KafkaTopic CRD - Complete Domain Topic
```yaml
# Source: Strimzi CRD spec https://strimzi.io/docs/operators/latest/configuring.html
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata:
  name: servista.iam.events
  namespace: kafka
  labels:
    strimzi.io/cluster: mspcloud-kafka
    app.kubernetes.io/part-of: servista
    servista.eu/domain: iam
    servista.eu/topic-type: domain
  annotations:
    argocd.argoproj.io/sync-wave: "2"
spec:
  partitions: 3
  replicas: 1
  config:
    retention.ms: "604800000"
    cleanup.policy: "delete"
    min.insync.replicas: "1"
```

### KafkaUser CRD - Service with Producer + Consumer + DLQ
```yaml
# Source: Strimzi KafkaUser spec https://strimzi.io/docs/operators/latest/configuring.html
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata:
  name: servista-api-accounts
  namespace: kafka
  labels:
    strimzi.io/cluster: mspcloud-kafka
    app.kubernetes.io/part-of: servista
    servista.eu/service: api-accounts
  annotations:
    argocd.argoproj.io/sync-wave: "3"
spec:
  authentication:
    type: tls-external
  authorization:
    type: simple
    acls:
      # Produce to own domain topic
      - resource:
          type: topic
          name: servista.accounts.events
          patternType: literal
        operations:
          - Write
          - Describe
      # Produce to own DLQ (for DLQ routing)
      - resource:
          type: topic
          name: servista.accounts.events.dlq
          patternType: literal
        operations:
          - Write
          - Describe
      # Read from own domain topic (for local consumers if needed)
      - resource:
          type: topic
          name: servista.accounts.events
          patternType: literal
        operations:
          - Read
          - Describe
      # Consumer group (prefix allows sub-groups)
      - resource:
          type: group
          name: servista-api-accounts
          patternType: prefix
        operations:
          - Read
          - Describe
```

### KafkaUser CRD - Audit Writer (Consumes ALL Domain Topics)
```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata:
  name: servista-svc-audit-writer
  namespace: kafka
  labels:
    strimzi.io/cluster: mspcloud-kafka
    app.kubernetes.io/part-of: servista
    servista.eu/service: svc-audit-writer
  annotations:
    argocd.argoproj.io/sync-wave: "3"
spec:
  authentication:
    type: tls-external
  authorization:
    type: simple
    acls:
      # Read from ALL domain topics (uses prefix pattern)
      - resource:
          type: topic
          name: servista.
          patternType: prefix
        operations:
          - Read
          - Describe
      # Consumer group
      - resource:
          type: group
          name: servista-svc-audit-writer
          patternType: prefix
        operations:
          - Read
          - Describe
```

### KafkaUser CRD - Kafka Streams Service (Silver Pipeline)
```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata:
  name: servista-pipeline-silver
  namespace: kafka
  labels:
    strimzi.io/cluster: mspcloud-kafka
    app.kubernetes.io/part-of: servista
    servista.eu/service: pipeline-silver
  annotations:
    argocd.argoproj.io/sync-wave: "3"
spec:
  authentication:
    type: tls-external
  authorization:
    type: simple
    acls:
      # Read from integrations topic (input)
      - resource:
          type: topic
          name: servista.integrations.events
          patternType: literal
        operations:
          - Read
          - Describe
      # Kafka Streams internal topics (changelog, repartition)
      - resource:
          type: topic
          name: servista.internal.pipeline-silver
          patternType: prefix
        operations:
          - Create
          - Read
          - Write
          - Delete
          - Describe
          - AlterConfigs
          - DescribeConfigs
      # Consumer group
      - resource:
          type: group
          name: servista-pipeline-silver
          patternType: prefix
        operations:
          - Read
          - Describe
```

### ArgoCD Application for Kafka Topics/Users
```yaml
# Follows existing pattern from iac-governance apps/databases/cnpg/application.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: servista-kafka-topics
  namespace: argocd
  annotations:
    argocd.argoproj.io/sync-wave: "4"
spec:
  project: servista
  source:
    repoURL: https://github.com/servista-eu/servista-governance.git
    targetRevision: main
    path: apps/streaming/kafka-topics/overlays/prod
  destination:
    server: https://kubernetes.default.svc
    namespace: kafka
  syncPolicy:
    automated:
      selfHeal: true
      prune: true
    syncOptions:
      - ServerSideApply=true
```

### Kustomize Base kustomization.yaml
```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: kafka

resources:
  # Domain topics
  - topics/servista.accounts.events.yaml
  - topics/servista.accounts.events.dlq.yaml
  - topics/servista.authorization.events.yaml
  - topics/servista.authorization.events.dlq.yaml
  - topics/servista.billing.events.yaml
  - topics/servista.billing.events.dlq.yaml
  - topics/servista.egress.events.yaml
  - topics/servista.egress.events.dlq.yaml
  - topics/servista.iam.events.yaml
  - topics/servista.iam.events.dlq.yaml
  - topics/servista.integrations.events.yaml
  - topics/servista.integrations.events.dlq.yaml
  - topics/servista.organizations.events.yaml
  - topics/servista.organizations.events.dlq.yaml
  - topics/servista.subscriptions.events.yaml
  - topics/servista.subscriptions.events.dlq.yaml
  - topics/servista.usage.events.yaml
  - topics/servista.usage.events.dlq.yaml
  # KafkaUser resources
  - users/servista-api-accounts.yaml
  - users/servista-api-organizations.yaml
  - users/servista-api-authorization.yaml
  - users/servista-api-iam.yaml
  - users/servista-api-subscriptions.yaml
  - users/servista-api-billing.yaml
  - users/servista-api-audit-query.yaml
  - users/servista-api-canonical-data.yaml
  - users/servista-api-credentials.yaml
  - users/servista-svc-token-broker.yaml
  - users/servista-svc-audit-writer.yaml
  - users/servista-svc-rmp-consumer.yaml
  - users/servista-adapter-xurrent.yaml
  - users/servista-adapter-salesforce.yaml
  - users/servista-adapter-lansweeper.yaml
  - users/servista-pipeline-silver.yaml
  - users/servista-pipeline-landing-zone.yaml
  - users/servista-pipeline-gold.yaml
  - users/servista-apisix.yaml
```

### Prod Overlay kustomization.yaml (Future Multi-Node)
```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../../base

# When scaling to multi-node cluster, uncomment to override replication:
# patches:
#   - target:
#       group: kafka.strimzi.io
#       version: v1
#       kind: KafkaTopic
#     patch: |
#       - op: replace
#         path: /spec/replicas
#         value: 3
#       - op: replace
#         path: /spec/config/min.insync.replicas
#         value: "2"
```

## Topic-Service Mapping Matrix

Derived from ARCHITECTURE.md service responsibilities and ADR-012 topic list.

### Producer Mapping
| Service (KafkaUser) | Produces To |
|---------------------|-------------|
| servista-api-accounts | servista.accounts.events |
| servista-api-organizations | servista.organizations.events |
| servista-api-iam | servista.iam.events |
| servista-api-authorization | servista.authorization.events |
| servista-api-subscriptions | servista.subscriptions.events |
| servista-api-billing | servista.billing.events |
| servista-api-credentials | (no domain topic -- credentials are secrets, not events) |
| servista-svc-token-broker | servista.iam.events (TRL changes, token version events) |
| servista-apisix | servista.usage.events |
| servista-adapter-xurrent | servista.integrations.events |
| servista-adapter-salesforce | servista.integrations.events |
| servista-adapter-lansweeper | servista.integrations.events |
| servista-pipeline-silver | (outputs to Silver compacted topic -- separate from domain topics) |
| servista-pipeline-gold | servista.billing.events (billing candidates from Gold batch) |

### Consumer Mapping
| Service (KafkaUser) | Consumes From |
|---------------------|---------------|
| servista-svc-audit-writer | ALL servista.*.events topics (prefix ACL) |
| servista-svc-rmp-consumer | Silver output topic (not a domain topic) |
| servista-pipeline-landing-zone | servista.integrations.events |
| servista-pipeline-silver | servista.integrations.events (via Landing Zone output or directly) |
| servista-pipeline-gold | Silver output topic |
| servista-api-billing | servista.subscriptions.events (usage summaries for cost pass-through) |
| servista-svc-token-broker | servista.iam.events (TRL subscription -- may also use Kafka for TRL distribution) |
| servista-apisix | servista.iam.events (TRL Kafka subscription + polling fallback) |
| servista-api-iam | servista.accounts.events (account lifecycle triggers IAM cleanup) |
| servista-api-authorization | servista.iam.events, servista.organizations.events (OpenFGA tuple updates on role/org changes) |

### DLQ Write Access
Every service that consumes from a domain topic also gets Write access to the corresponding DLQ topic. The centralized DLQ routing in servista-commons handles this automatically.

### Notes on Specific Services
- **servista-api-credentials:** Primarily interacts with OpenBao, not Kafka. May produce audit events. If it does, they would go to a domain topic (TBD -- could use `servista.iam.events` or a dedicated topic). For now, provision a user with minimal ACLs.
- **servista-api-audit-query:** Read-only API querying ImmuDB. Does NOT consume from Kafka directly (that's svc-audit-writer's job). Needs no Kafka ACLs -- include user for completeness but with minimal permissions.
- **servista-api-canonical-data:** Queries PostgreSQL read model. Does NOT consume from Kafka directly (that's svc-rmp-consumer's job). Same as audit-query.
- **APISIX:** Produces usage events and consumes TRL events. The APISIX KafkaUser needs special consideration -- APISIX runs in the `ingress` namespace, not `servista`.
- **Silver pipeline topics:** The Silver streaming app produces to its own output compacted topic (for RMP and Gold to consume). This is a separate concern from the 9 domain topics. The Silver output topic may need its own KafkaTopic CRD, but it's architecturally distinct from the domain event topics. Consider whether to include it in Phase 6 or defer to Phase 43 (Silver Streaming Core).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| KafkaTopic v1beta2 API | kafka.strimzi.io/v1 API | Strimzi 0.49.0 | Use v1 for all new CRDs; v1beta2 deprecated |
| KafkaUser `acls[].operation` (singular) | `acls[].operations` (plural, array) | Strimzi 0.49.0 | Must use plural form; singular is deprecated |
| Testcontainers `org.testcontainers:kafka` | `org.testcontainers:testcontainers-kafka` | TC 2.0.0 | Module renamed; old coordinates no longer resolve |
| KafkaContainer from containers package | KafkaContainer from kafka package | TC 2.0.0 | `org.testcontainers.kafka.KafkaContainer` replaces `org.testcontainers.containers.KafkaContainer` |

**Deprecated/outdated:**
- Strimzi v1alpha1/v1beta1/v1beta2 API versions: Still supported until Strimzi 1.0.0 / 0.52.0 but deprecated. Use v1.
- KafkaUser `acls[].operation` (singular): Deprecated in Strimzi 0.49.0. Use `operations` (plural array).
- Strimzi Access Operator v0.2.0: Exists for cross-namespace secret distribution but is immature. Not needed here because OpenBao Agent injector handles credential injection directly.

## Open Questions

1. **Silver Output Topic Provisioning**
   - What we know: Silver streaming app produces canonical events to a compacted output topic. RMP and Gold consume from it.
   - What's unclear: Should this topic be provisioned in Phase 6 alongside domain topics, or deferred to Phase 43 (Silver Streaming Core)?
   - Recommendation: Defer to Phase 43. The Silver output topic has different characteristics (compacted, different retention) and is architecturally separate from the domain event topics.

2. **APISIX Namespace for KafkaUser**
   - What we know: APISIX runs in the `ingress` namespace (not `servista`). It needs to produce to `servista.usage.events` and consume TRL events from `servista.iam.events`.
   - What's unclear: How APISIX gets its mTLS certificate. It's managed by the mspcloud repo, not iac-governance. The KafkaUser CRD can live in iac-governance (it's just an ACL definition in the kafka namespace), but the actual certificate injection happens in APISIX's deployment (mspcloud territory).
   - Recommendation: Create the KafkaUser CRD in iac-governance. Document that APISIX's deployment (Phase 30) must configure OpenBao Agent annotations with `common_name=servista-apisix`.

3. **OpenBao PKI Role for Kafka Clients**
   - What we know: The `kafka-client-cert` PKI role exists in mspcloud for issuing Kafka mTLS certificates. The CA chain is already exported to Strimzi via `setup-clients-ca.sh`.
   - What's unclear: Whether the existing PKI role allows CN values matching `servista-*` or needs configuration updates.
   - Recommendation: Verify the PKI role's `allowed_domains` and `allow_subdomains` settings. If it only allows specific CNs, it needs to be updated to allow the `servista-*` pattern. This may require a change in the mspcloud repo's OpenBao configuration.

4. **api-credentials and api-audit-query Kafka Access**
   - What we know: These services primarily interact with OpenBao/ImmuDB respectively, not Kafka directly.
   - What's unclear: Whether they produce any domain events at all.
   - Recommendation: Provision minimal KafkaUser resources for them. They may produce events in future phases. If not, the CRDs can be removed.

## Sources

### Primary (HIGH confidence)
- mspcloud Kafka cluster config: `/home/sven/work/clients/hestia/projects/mspcloud/apps/streaming/kafka/overlays/prod/kafka-cluster.yaml` - Verified cluster version 4.1.1, KRaft, mTLS, simple ACL, external clients CA
- mspcloud setup-clients-ca.sh: `/home/sven/work/clients/hestia/projects/mspcloud/apps/streaming/kafka/overlays/prod/post-deploy/setup-clients-ca.sh` - Verified OpenBao PKI CA export pattern
- iac-governance repo structure: `/home/sven/work/clients/hestia/projects/servista/governance/iac-governance/` - Verified app-of-apps pattern, Kustomize layout, ArgoCD Application structure
- servista-commons source: `/home/sven/work/clients/hestia/projects/servista/infrastructure/servista-commons/src/` - Verified EventEnvelopeSerializer/Deserializer, ServistaContext, EventContextHandler, EventBuilder
- servista-avro-schemas: `/home/sven/work/clients/hestia/projects/servista/infrastructure/servista-avro-schemas/src/main/avro/` - Verified EventEnvelope.avsc and domain directory structure
- ADR-012: `/home/sven/work/clients/hestia/projects/servista/governance/mgmt-platform/architecture/decisions/012-domain-level-topics.md` - All 9 domain topics and DLQ naming convention
- [Strimzi Configuring Documentation (0.50.1)](https://strimzi.io/docs/operators/latest/configuring.html) - KafkaTopic and KafkaUser CRD v1 spec
- [Strimzi kafka-versions.yaml](https://github.com/strimzi/strimzi-kafka-operator/blob/main/kafka-versions.yaml) - Kafka 4.1.0 and 4.1.1 supported

### Secondary (MEDIUM confidence)
- [Strimzi KafkaUser CRD GitHub](https://github.com/strimzi/strimzi-kafka-operator/blob/main/install/user-operator/04-Crd-kafkauser.yaml) - ACL operations plural form
- [Strimzi Access Operator](https://github.com/strimzi/kafka-access-operator) - Cross-namespace option (v0.2.0, immature)
- [Strimzi KafkaUser issue #2141](https://github.com/strimzi/strimzi-kafka-operator/issues/2141) - CREATE ACL with prefix pattern for dynamic topics
- [Kafka Streams Security Guide](https://kafka.apache.org/28/streams/developer-guide/security/) - Internal topic ACL requirements (Read, Write, Create, Delete, Describe with prefix pattern)
- [Testcontainers Kafka Module](https://java.testcontainers.org/modules/kafka/) - TC 2.x KafkaContainer API

### Tertiary (LOW confidence)
- [Strimzi clientsCa discussion #7845](https://github.com/strimzi/strimzi-kafka-operator/issues/7845) - External CA behavior with user operator
- [Strimzi tls-external discussion #6515](https://github.com/orgs/strimzi/discussions/6515) - tls-external authentication pattern

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All tools already deployed and verified (Strimzi, ArgoCD, Kustomize, Avro, kafka-clients, Testcontainers)
- Architecture: HIGH - IaC patterns proven in iac-governance; Strimzi CRD v1 spec well-documented; existing EventEnvelope code provides clear integration points
- Pitfalls: HIGH - Critical `tls-external` finding verified against actual cluster config; TC 2.x module rename already handled in codebase
- Topic-service mapping: MEDIUM - Derived from ARCHITECTURE.md; some services (credentials, audit-query) have unclear Kafka access patterns

**Research date:** 2026-03-03
**Valid until:** 2026-04-03 (stable domain -- Strimzi CRD spec, Kafka 4.1.x, and IaC patterns are mature)
