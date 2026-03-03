package eu.servista.gradle

import java.io.File

data class AvroSchemaInfo(
    val group: String,
    val artifactId: String,
    val file: File,
    val content: String,
)

fun discoverSchemas(avroDir: File): List<AvroSchemaInfo> {
    return avroDir
        .walkTopDown()
        .filter { it.extension == "avsc" }
        .map { file ->
            val domain = file.parentFile.name
            val group =
                if (domain == "envelope" || domain == "dlq") {
                    "servista.$domain"
                } else {
                    "servista.$domain.events"
                }
            AvroSchemaInfo(
                group = group,
                artifactId = file.nameWithoutExtension,
                file = file,
                content = file.readText(),
            )
        }
        .toList()
}
