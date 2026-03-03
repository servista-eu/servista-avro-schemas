rootProject.name = "servista-avro-schemas"

pluginManagement { includeBuild("../gradle-platform") }

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../gradle-platform/catalog/libs.versions.toml")) }
    }
}
