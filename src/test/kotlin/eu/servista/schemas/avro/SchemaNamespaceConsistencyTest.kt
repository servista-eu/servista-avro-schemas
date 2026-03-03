package eu.servista.schemas.avro

import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class SchemaNamespaceConsistencyTest {
    private val avroDir = Path.of("src/main/avro")

    @Test
    fun `all schema namespaces match their directory name`() {
        val schemas = avroDir.toFile().walkTopDown().filter { it.extension == "avsc" }.toList()

        schemas shouldHaveAtLeastSize 3

        schemas.forEach { file ->
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            val namespace =
                json["namespace"]?.jsonPrimitive?.content
                    ?: error("Schema ${file.name} has no namespace")
            val expectedDomain = file.parentFile.name
            val expectedNamespace = "eu.servista.schemas.avro.$expectedDomain"
            namespace shouldBe expectedNamespace
        }
    }

    @Test
    fun `schema record name matches filename`() {
        val schemas = avroDir.toFile().walkTopDown().filter { it.extension == "avsc" }.toList()

        schemas.forEach { file ->
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            val name =
                json["name"]?.jsonPrimitive?.content ?: error("Schema ${file.name} has no name")
            val expectedName = file.nameWithoutExtension
            name shouldBe expectedName
        }
    }
}
