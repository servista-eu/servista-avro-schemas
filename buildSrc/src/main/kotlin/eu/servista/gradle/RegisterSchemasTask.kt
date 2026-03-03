package eu.servista.gradle

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

abstract class RegisterSchemasTask : DefaultTask() {
    @get:Input abstract val registryUrl: Property<String>

    @get:InputDirectory abstract val schemaDir: DirectoryProperty

    @TaskAction
    fun register() {
        val baseUrl = registryUrl.getOrElse("")
        if (baseUrl.isBlank()) {
            throw GradleException(
                "registryUrl is required. Pass -PregistryUrl=http://your-apicurio-host:8080\n" +
                    "Do NOT register against production without explicit intent."
            )
        }

        val client = HttpClient.newHttpClient()
        val schemas = discoverSchemas(schemaDir.get().asFile)

        if (schemas.isEmpty()) {
            logger.lifecycle("No schemas found in ${schemaDir.get().asFile}")
            return
        }

        val apiBase = "${baseUrl.trimEnd('/')}/apis/registry/v3"

        // Step 1: Ensure groups exist and configure compatibility rules
        val groups = schemas.map { it.group }.distinct()
        for (group in groups) {
            ensureGroupExists(client, apiBase, group)
            configureGroupRule(client, apiBase, group, compatibilityFor(group))
        }

        // Step 2: For each schema, register
        for (schema in schemas) {
            registerSchema(client, apiBase, schema)
            logger.lifecycle("Registered ${schema.group}/${schema.artifactId}")
        }

        logger.lifecycle(
            "Successfully registered ${schemas.size} schema(s) in ${groups.size} group(s)"
        )
    }

    private fun compatibilityFor(group: String): String =
        if (group == "servista.envelope") "FULL" else "BACKWARD"

    private fun ensureGroupExists(client: HttpClient, apiBase: String, groupId: String) {
        val body = buildJsonObject { put("groupId", groupId) }.toString()
        val req =
            HttpRequest.newBuilder()
                .uri(URI.create("$apiBase/groups"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        // 200/201 = created, 409 = already exists -- both are OK
        if (resp.statusCode() !in listOf(200, 201, 409)) {
            throw GradleException(
                "Failed to create group $groupId: ${resp.statusCode()} ${resp.body()}"
            )
        }
    }

    private fun configureGroupRule(
        client: HttpClient,
        apiBase: String,
        groupId: String,
        compatibility: String,
    ) {
        val body =
            buildJsonObject {
                    put("ruleType", "COMPATIBILITY")
                    put("config", compatibility)
                }
                .toString()

        // Try to create rule
        val createReq =
            HttpRequest.newBuilder()
                .uri(URI.create("$apiBase/groups/$groupId/rules"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val createResp = client.send(createReq, HttpResponse.BodyHandlers.ofString())

        when (createResp.statusCode()) {
            in 200..204 ->
                logger.lifecycle("Configured $compatibility compatibility for group $groupId")
            409 -> {
                // Rule already exists -- update it
                val updateReq =
                    HttpRequest.newBuilder()
                        .uri(URI.create("$apiBase/groups/$groupId/rules/COMPATIBILITY"))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .build()
                val updateResp = client.send(updateReq, HttpResponse.BodyHandlers.ofString())
                if (updateResp.statusCode() !in 200..204) {
                    throw GradleException(
                        "Failed to update rule for group $groupId: ${updateResp.statusCode()} ${updateResp.body()}"
                    )
                }
                logger.lifecycle("Updated $compatibility compatibility for group $groupId")
            }
            else ->
                throw GradleException(
                    "Failed to configure rule for group $groupId: ${createResp.statusCode()} ${createResp.body()}"
                )
        }
    }

    private fun registerSchema(client: HttpClient, apiBase: String, schema: AvroSchemaInfo) {
        // Check if artifact already exists
        val checkReq =
            HttpRequest.newBuilder()
                .uri(
                    URI.create("$apiBase/groups/${schema.group}/artifacts/${schema.artifactId}")
                )
                .GET()
                .build()
        val checkResp = client.send(checkReq, HttpResponse.BodyHandlers.ofString())

        if (checkResp.statusCode() == 404) {
            // Create artifact with first version -- skip dryRun for first version
            createArtifact(client, apiBase, schema)
        } else if (checkResp.statusCode() in 200..299) {
            // Artifact exists -- check compatibility via dryRun, then add new version
            checkCompatibilityAndAddVersion(client, apiBase, schema)
        } else {
            throw GradleException(
                "Failed to check artifact ${schema.group}/${schema.artifactId}: ${checkResp.statusCode()} ${checkResp.body()}"
            )
        }
    }

    private fun createArtifact(client: HttpClient, apiBase: String, schema: AvroSchemaInfo) {
        val body =
            buildJsonObject {
                    put("artifactId", schema.artifactId)
                    put("artifactType", "AVRO")
                    putJsonObject("firstVersion") {
                        put("version", "1")
                        putJsonObject("content") {
                            put("content", schema.content)
                            put("contentType", "application/json")
                        }
                    }
                }
                .toString()

        val req =
            HttpRequest.newBuilder()
                .uri(URI.create("$apiBase/groups/${schema.group}/artifacts"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw GradleException(
                "Failed to create artifact ${schema.group}/${schema.artifactId}: ${resp.statusCode()} ${resp.body()}"
            )
        }
    }

    private fun checkCompatibilityAndAddVersion(
        client: HttpClient,
        apiBase: String,
        schema: AvroSchemaInfo,
    ) {
        val versionBody =
            buildJsonObject {
                    putJsonObject("content") {
                        put("content", schema.content)
                        put("contentType", "application/json")
                    }
                }
                .toString()

        // dryRun: validate compatibility without persisting
        val dryRunReq =
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        "$apiBase/groups/${schema.group}/artifacts/${schema.artifactId}/versions?dryRun=true"
                    )
                )
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(versionBody))
                .build()
        val dryRunResp = client.send(dryRunReq, HttpResponse.BodyHandlers.ofString())

        if (dryRunResp.statusCode() == 409) {
            throw GradleException(
                "INCOMPATIBLE SCHEMA: ${schema.group}/${schema.artifactId}\n" +
                    "The new version is incompatible with the existing version.\n" +
                    "Group compatibility rule: ${compatibilityFor(schema.group)}\n" +
                    "Response: ${dryRunResp.body()}"
            )
        }
        if (dryRunResp.statusCode() !in 200..299) {
            throw GradleException(
                "dryRun failed for ${schema.group}/${schema.artifactId}: ${dryRunResp.statusCode()} ${dryRunResp.body()}"
            )
        }

        // Compatibility check passed -- register for real
        val registerReq =
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        "$apiBase/groups/${schema.group}/artifacts/${schema.artifactId}/versions"
                    )
                )
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(versionBody))
                .build()
        val registerResp = client.send(registerReq, HttpResponse.BodyHandlers.ofString())
        if (registerResp.statusCode() !in 200..299) {
            throw GradleException(
                "Failed to register version for ${schema.group}/${schema.artifactId}: ${registerResp.statusCode()} ${registerResp.body()}"
            )
        }
    }
}
