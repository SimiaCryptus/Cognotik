import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("cognotik.common-conventions")
    `java-library`
    `maven-publish`
    id("signing")
    alias(libs.plugins.shadow)
    war
    application
}

application {
    mainClass.set("com.simiacryptus.cognotik.DaemonClient")
}

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

repositories {
    mavenCentral {
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.apache.xmlgraphics:batik-transcoder:1.14")
    implementation("org.apache.xmlgraphics:batik-codec:1.14")

    implementation("org.openjfx:javafx-swing:17")
    implementation("org.openjfx:javafx-graphics:17")
    implementation("org.openjfx:javafx-base:17")

    implementation(libs.commons.text)

    implementation(project(":jo-penai"))
    implementation(project(":core"))
    implementation(project(":groovy"))
    implementation(project(":kotlin"))
    implementation(project(":webui"))

    implementation(libs.aws.sdk)
    implementation("org.jsoup:jsoup:1.19.1")

    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)

    implementation(libs.guava)
    implementation(libs.jetty.server)
    implementation(libs.jetty.webapp)
    implementation(libs.jetty.websocket.server)
    implementation(group = "org.apache.httpcomponents.client5", name = "httpclient5-fluent", version = "5.2.3")
    implementation(libs.gson)
    implementation(group = "com.h2database", name = "h2", version = "2.2.224")

    implementation(libs.kotlinx.coroutines)
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable", version = "0.3.8")
    implementation(kotlin("stdlib"))
    implementation(kotlin("scripting-jsr223"))
    implementation(kotlin("scripting-jvm"))
    implementation(kotlin("scripting-jvm-host"))
    implementation(kotlin("script-runtime"))
    implementation(kotlin("scripting-compiler-embeddable"))
    implementation(kotlin("compiler-embeddable"))

    implementation(group = "org.scala-lang", name = "scala-library", version = "2.13.9")
    implementation(group = "org.scala-lang", name = "scala-compiler", version = "2.13.9")
    implementation(group = "org.scala-lang", name = "scala-reflect", version = "2.13.9")

    implementation(libs.commons.io)
    implementation(group = "com.vladsch.flexmark", name = "flexmark-all", version = "0.64.8")
    implementation(platform("software.amazon.awssdk:bom:2.27.23"))
    implementation(libs.aws.sdk)
    implementation(group = "software.amazon.awssdk", name = "sso", version = "2.21.29")

    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)

    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.10.1")
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params", version = "5.10.1")
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.10.1")
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("surefire.useManifestOnlyJar", "false")
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        jvmArgs(
            "--add-opens",
            "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens",
            "java.base/java.util=ALL-UNNAMED",
            "--add-opens",
            "java.base/java.lang=ALL-UNNAMED",
            "--add-opens",
            "java.base/sun.nio.ch=ALL-UNNAMED"
        )

        systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    }
}

tasks.war {
    archiveClassifier.set("")
    from(sourceSets.main.get().output)
    from(project.configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isZip64 = true
    manifest {
        attributes(
            "Main-Class" to "com.simiacryptus.cognotik.DaemonClient"
        )
    }
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("all")
    mergeServiceFiles()
    isZip64 = true
    exclude("org/slf4j/impl/**")
    manifest {
        attributes(
            "Main-Class" to "com.simiacryptus.cognotik.DaemonClient"
        )
    }
}

fun installContextMenuAction(os: String) {
    val appName = "Cognotik"
    val appDisplayName = "Open with Cognotik"
    val scriptPath = layout.buildDirectory.dir("jpackage/scripts").get().asFile
    scriptPath.mkdirs()
    when {
        os.contains("windows") -> {

            val regFile = scriptPath.resolve("add_skyenetapps_context_menu.reg")
            val templateFile = layout.projectDirectory.file("src/packaging/windows/context_menu.reg.template").asFile
            val templateContent = templateFile.readText()
            val regContent = templateContent
                .replace("{{appDisplayName}}", appDisplayName)
                .replace("{{appName}}", appName)
            regFile.writeText(regContent)
            println("Wrote context menu .reg file to: $regFile")

            val batchFile = scriptPath.resolve("install_context_menu.bat")
            batchFile.writeText(
                """
                @echo off
                echo Installing context menu entries...
                regedit /s "%~dp0add_skyenetapps_context_menu.reg"
                echo Context menu entries installed.
                exit /b 0
            """.trimIndent()
            )
            println("Wrote batch file to apply registry entries: $batchFile")
        }

        os.contains("mac") -> {

            val plistFile = scriptPath.resolve("Cognotik.workflow/Contents/info.plist")
            plistFile.parentFile.mkdirs()
            val plistTemplateFile = layout.projectDirectory.file("src/packaging/macos/info.plist.template").asFile
            val plistContent = plistTemplateFile.readText()
                .replace("{{appDisplayName}}", appDisplayName)
            plistFile.writeText(plistContent)

            val script = scriptPath.resolve("Cognotik.workflow/Contents/document.wflow")
            val wflowTemplateFile = layout.projectDirectory.file("src/packaging/macos/document.wflow.template").asFile
            val wflowContent = wflowTemplateFile.readText()
                .replace("{{appName}}", appName)
            script.writeText(wflowContent)

            val stopScriptDir = scriptPath.resolve("StopSkyenetApps.workflow/Contents")
            stopScriptDir.mkdirs()
            val stopPlistFile = stopScriptDir.resolve("info.plist")
            val stopPlistTemplateFile =
                layout.projectDirectory.file("src/packaging/macos/stop_info.plist.template").asFile
            stopPlistFile.writeText(stopPlistTemplateFile.readText())

            val stopScript = stopScriptDir.resolve("document.wflow")
            val stopWflowTemplateFile =
                layout.projectDirectory.file("src/packaging/macos/stop_document.wflow.template").asFile
            val stopWflowContent = stopWflowTemplateFile.readText()
                .replace("{{appName}}", appName)
            stopScript.writeText(stopWflowContent)

            println("Wrote stop server Quick Action to: ${stopScript.parentFile}")
            println("Wrote context menu Quick Action to: ${script.parentFile}")
        }

        os.contains("linux") -> {

            val desktopFile = scriptPath.resolve("cognotik-folder-action.desktop")
            val desktopTemplateFile =
                layout.projectDirectory.file("src/packaging/linux/folder_action.desktop.template").asFile
            val desktopContent = desktopTemplateFile.readText()
                .replace("{{appDisplayName}}", appDisplayName)
            desktopFile.writeText(desktopContent)

            val mainDesktopFile =
                layout.buildDirectory.dir("jpackage/resources").get().asFile.resolve("cognotik.desktop")
            mainDesktopFile.parentFile.mkdirs()
            val mainDesktopTemplateFile =
                layout.projectDirectory.file("src/packaging/linux/main.desktop.template").asFile
            mainDesktopFile.writeText(mainDesktopTemplateFile.readText())

            println("Created main application .desktop file to: $mainDesktopFile")
            println("Created context menu .desktop file to: $desktopFile")
        }
    }
}

abstract class JPackageTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: org.gradle.process.ExecOperations
}

tasks.register("packageDmg", JPackageTask::class) {
    group = "distribution"
    description = "Creates a .dmg package for macOS"
    onlyIf { System.getProperty("os.name").lowercase().contains("mac") }
    dependsOn("shadowJar")
    doLast {
        val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val shadowJarName = shadowJarFile.name
        val inputDir = layout.buildDirectory.dir("jpackage/input").get().asFile
        if (!inputDir.exists()) {
            inputDir.mkdirs()
        }
        copy {
            from(shadowJarFile)
            into(inputDir)
        }
        execOperations.exec {
            commandLine(
                "jpackage",
                "--type", "dmg",
                "--input", inputDir.path,
                "--main-jar", shadowJarName,
                "--main-class", "com.simiacryptus.cognotik.DaemonClient",
                "--dest", layout.buildDirectory.dir("jpackage").get().asFile.path,
                "--name", "Cognotik",
                "--app-version", "${project.version}",
                "--copyright", "Copyright © 2024 SimiaCryptus",
                "--description", "Cognotik Agentic Toolkit",
            )
        }
        installContextMenuAction("mac")
    }
}

tasks.register("packageMsi", JPackageTask::class) {
    group = "distribution"
    description = "Creates a .msi package for Windows"
    onlyIf { System.getProperty("os.name").lowercase().contains("windows") }
    dependsOn("shadowJar")
    doLast {
        val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val shadowJarName = shadowJarFile.name
        val inputDir = layout.buildDirectory.dir("jpackage/input").get().asFile
        if (!inputDir.exists()) {
            inputDir.mkdirs()
        }
        copy {
            from(shadowJarFile)
            into(inputDir)
        }
        val resourceDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
        if (!resourceDir.exists()) {
            resourceDir.mkdirs()
        }
        copy {
            from(layout.projectDirectory.file("src/main/resources/toolbarIcon_128x128.ico"))
            into(resourceDir)
        }

        installContextMenuAction("windows")

        copy {
            from(layout.buildDirectory.dir("jpackage/scripts").get().asFile) {
                include("add_skyenetapps_context_menu.reg")
                include("install_context_menu.bat")
            }
            into(resourceDir)
        }

        val userInstallerScript = File(resourceDir, "Setup_Context_Menu.bat")
        userInstallerScript.writeText(layout.projectDirectory.file("src/packaging/windows/Setup_Context_Menu.bat.template").asFile.readText())

        val shortcutScript = File(resourceDir, "create_installer_shortcut.ps1")
        shortcutScript.writeText(layout.projectDirectory.file("src/packaging/windows/create_installer_shortcut.ps1.template").asFile.readText())

        execOperations.exec {
            commandLine(
                "jpackage",
                "--type",
                "msi",
                "--input",
                inputDir.path,
                "--main-jar",
                shadowJarName,
                "--main-class",
                "com.simiacryptus.cognotik.DaemonClient",
                "--dest",
                layout.buildDirectory.dir("jpackage").get().asFile.path,
                "--name",
                "Cognotik",
                "--app-version",
                project.version.toString().replace("-", "."),
                "--copyright",
                "Copyright © 2024 SimiaCryptus",
                "--description",
                "Cognotik Agentic Toolkit",
                "--win-dir-chooser",
                "--win-menu",
                "--win-shortcut",
                "--icon",
                File(resourceDir, "toolbarIcon_128x128.ico").path,
                "--resource-dir",
                resourceDir.path,
                "--win-shortcut-prompt",
                "--win-help-url",
                "https://github.com/SimiaCryptus/Cognotik",
                "--win-update-url",
                "https://github.com/SimiaCryptus/Cognotik/releases",
                "--file-associations",
                layout.projectDirectory.file("src/packaging/windows/file-associations.properties").asFile.path,
                "--install-dir",
                "Cognotik",
                "--vendor",
                "SimiaCryptus"
            )
        }
    }
}

tasks.register("packageDeb", JPackageTask::class) {
    group = "distribution"
    description = "Creates a .deb package for Linux"
    onlyIf { System.getProperty("os.name").lowercase().contains("linux") }
    dependsOn("shadowJar", "prepareLinuxDesktopFile")
    doLast {

        val resourcesDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs()
        }

        val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val shadowJarName = shadowJarFile.name
        execOperations.exec {
            commandLine(
                "jpackage",
                "--type", "deb",
                "--input", layout.buildDirectory.dir("libs").get().asFile.path,
                "--main-jar", shadowJarName,
                "--main-class", "com.simiacryptus.cognotik.DaemonClient",
                "--dest", layout.buildDirectory.dir("jpackage").get().asFile.path,
                "--name", "Cognotik",
                "--app-version", "${project.version}",
                "--copyright", "Copyright © 2024 SimiaCryptus",
                "--description", "Cognotik Agentic Toolkit",
                "--linux-menu-group", "Utilities",
                "--resource-dir", layout.buildDirectory.dir("jpackage/resources").get().asFile.path,
                "--linux-deb-maintainer", "support@simiacryptus.com",
                "--linux-shortcut",
                "--linux-app-category", "Development;Utility",
                "--icon", layout.projectDirectory.file("src/main/resources/toolbarIcon.svg").asFile.path,
                "--linux-package-name", "cognotik"
            )
        }
        installContextMenuAction("linux")
    }
}
tasks.register("prepareLinuxDesktopFile") {
    group = "build"
    description = "Copies the .desktop file to the jpackage input directory for Linux"
    onlyIf { System.getProperty("os.name").lowercase().contains("linux") }
    doLast {

        val resourcesDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs()
        }

        val contextMenuDesktopFile = File(resourcesDir, "cognotik-folder-action.desktop")
        val contextMenuTemplateFile =
            layout.projectDirectory.file("src/packaging/linux/folder_action.desktop.template").asFile
        val contextMenuContent = contextMenuTemplateFile.readText()
            .replace("{{appDisplayName}}", "Open with Cognotik")
        contextMenuDesktopFile.writeText(contextMenuContent)

        val mainDesktopFile = File(resourcesDir, "cognotik.desktop")
        val mainDesktopTemplateFile = layout.projectDirectory.file("src/packaging/linux/main.desktop.template").asFile
        mainDesktopFile.writeText(mainDesktopTemplateFile.readText())

        val installScript = File(resourcesDir, "postinst")

        val postinstTemplateFile = layout.projectDirectory.file("src/packaging/linux/postinst.template").asFile
        val postinstContent = postinstTemplateFile.readText()
            .replace("{{resourcesDir}}", resourcesDir.absolutePath)
        installScript.writeText(postinstContent)
        installScript.setExecutable(true)

        val uninstallScript = File(resourcesDir, "prerm")
        val prermTemplateFile = layout.projectDirectory.file("src/packaging/linux/prerm.template").asFile
        uninstallScript.writeText(prermTemplateFile.readText())
        uninstallScript.setExecutable(true)

        println("Created desktop files in jpackage resources directory:")
        println("- ${mainDesktopFile.absolutePath}")
        println("- ${contextMenuDesktopFile.absolutePath}")
        println("- ${installScript.absolutePath}")
        println("- ${uninstallScript.absolutePath}")
    }
}

tasks.register("package") {
    description = "Creates a platform-specific package"
    val os = System.getProperty("os.name").lowercase()
    when {
        os.contains("linux") -> dependsOn("prepareLinuxDesktopFile", "shadowJar", "packageDeb")
        os.contains("mac") -> dependsOn("packageDmg")
        os.contains("windows") -> dependsOn("packageMsi")
    }
}

tasks.register("packageLinux") {
    description = "Creates a Linux package using the custom flow"
    dependsOn("clean", "shadowJar", "packageDeb")
}

tasks.named("build") {
    dependsOn(tasks.war)
    dependsOn(tasks.shadowJar)
}

tasks.register("updateVersionFromEnv") {
    val envVersion = System.getenv("COGNOTIK_VERSION")
    if (envVersion != null && envVersion.isNotEmpty()) {
        println("Updating version from environment variable: $envVersion")
        project.version = envVersion
    }
}

tasks.register("verifyRuntimeEnvironment") {
    group = "verification"
    description = "Verifies the runtime environment for packaging"
    doLast {
        val javaHome = System.getProperty("java.home")
        val javaVersion = System.getProperty("java.version")
        println("Java Home: $javaHome")
        println("Java Version: $javaVersion")

        try {
            @Suppress("DEPRECATION")
            exec {
                commandLine("jpackage", "--version")
                standardOutput = System.out
            }
        } catch (e: Exception) {
            logger.warn("jpackage command not found. Make sure you're using JDK 14+ with jpackage.")
        }
    }
}