//enableFeaturePreview("VERSION_CATALOGS")

dependencyResolutionManagement {
    versionCatalogs {
        // Use register instead of create to avoid conflicts
        register("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}