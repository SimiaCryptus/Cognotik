rootProject.name = "cognotik-intellij"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
}

include(":jo-penai")
include(":antlr")
include(":core")
include(":groovy")
include(":kotlin")
include(":webui")

project(":jo-penai").projectDir = file("../jo-penai")
project(":antlr").projectDir = file("../antlr")
project(":core").projectDir = file("../core")
project(":groovy").projectDir = file("../groovy")
project(":kotlin").projectDir = file("../kotlin")
project(":webui").projectDir = file("../webui")
