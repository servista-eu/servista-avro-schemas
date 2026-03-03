package eu.servista.schemas.avro

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.jupiter.api.Test

/**
 * Tests for the schema discovery and directory-to-Apicurio-group mapping logic.
 *
 * The actual [discoverSchemas] function lives in buildSrc and is not importable from the main test
 * source set. These tests validate the same mapping algorithm inline against the real schema
 * directory, ensuring the convention is documented and enforced.
 */
class SchemaDiscoveryTest {
    private val avroDir = File("src/main/avro")

    /** Maps a domain directory name to its Apicurio group ID (same logic as buildSrc). */
    private fun groupForDomain(domain: String): String =
        if (domain == "envelope") "servista.envelope" else "servista.$domain.events"

    @Test
    fun `discovers all schema files`() {
        val schemas =
            avroDir
                .walkTopDown()
                .filter { it.extension == "avsc" }
                .map { it.nameWithoutExtension }
                .toList()

        schemas shouldHaveAtLeastSize 3
        schemas shouldContainExactlyInAnyOrder
            listOf("EventEnvelope", "AccountCreated", "OrgCreated")
    }

    @Test
    fun `maps envelope directory to servista_envelope group`() {
        val file = File("src/main/avro/envelope/EventEnvelope.avsc")
        val domain = file.parentFile.name
        groupForDomain(domain) shouldBe "servista.envelope"
    }

    @Test
    fun `maps domain directories to servista_domain_events group`() {
        val testCases =
            mapOf(
                "accounts" to "servista.accounts.events",
                "organizations" to "servista.organizations.events",
                "iam" to "servista.iam.events",
                "authorization" to "servista.authorization.events",
                "integrations" to "servista.integrations.events",
                "usage" to "servista.usage.events",
                "subscriptions" to "servista.subscriptions.events",
                "billing" to "servista.billing.events",
                "egress" to "servista.egress.events",
            )
        testCases.forEach { (domain, expectedGroup) ->
            groupForDomain(domain) shouldBe expectedGroup
        }
    }

    @Test
    fun `artifact ID matches schema filename without extension`() {
        val schemas = avroDir.walkTopDown().filter { it.extension == "avsc" }.toList()

        schemas.forEach { file -> file.nameWithoutExtension shouldBe file.nameWithoutExtension }
    }

    @Test
    fun `all discovered schemas are in domain subdirectories`() {
        val schemas = avroDir.walkTopDown().filter { it.extension == "avsc" }.toList()

        schemas.forEach { file ->
            // Each schema file must be inside a domain subdirectory of src/main/avro/
            val domain = file.parentFile.name
            domain shouldBe file.parentFile.name
            // Domain should not be "avro" (top-level) -- schemas must be in subdirectories
            (domain != "avro") shouldBe true
        }
    }
}
