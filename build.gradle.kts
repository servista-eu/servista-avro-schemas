plugins {
    id("servista.library")
    id("servista.avro")
    id("servista.testing")
    `maven-publish`
}

repositories {
    mavenCentral()
}

val avroTools by configurations.creating {
    exclude(group = "org.apache.avro", module = "trevni-avro")
    exclude(group = "org.apache.avro", module = "trevni-core")
}

dependencies {
    avroTools("org.apache.avro:avro-tools:1.12.1")
}

val generateAvro by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Generate Java classes from Avro schemas"
    classpath = avroTools
    mainClass.set("org.apache.avro.tool.Main")

    val avroDir = file("src/main/avro")
    val outputDir = layout.buildDirectory.dir("generated-avro-java")

    inputs.dir(avroDir)
    outputs.dir(outputDir)

    doFirst {
        val schemaFiles = avroDir.walkTopDown()
            .filter { it.extension == "avsc" }
            .map { it.absolutePath }
            .toList()
        args = listOf("compile", "schema") + schemaFiles + listOf(outputDir.get().asFile.absolutePath)
    }
}

sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated-avro-java"))
tasks.named("compileKotlin") { dependsOn(generateAvro) }
tasks.named("compileJava") { dependsOn(generateAvro) }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "eu.servista"
            artifactId = "servista-avro-schemas"
        }
    }
    repositories {
        maven {
            name = "forgejo"
            url = uri("https://git.hestia-ng.eu/api/packages/servista/maven")
            credentials {
                username = findProperty("forgejoUser")?.toString() ?: System.getenv("FORGEJO_USER") ?: ""
                password = findProperty("forgejoToken")?.toString() ?: System.getenv("FORGEJO_TOKEN") ?: ""
            }
        }
    }
}
