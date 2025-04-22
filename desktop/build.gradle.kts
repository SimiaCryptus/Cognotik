import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  java
  `java-library`
  `maven-publish`
  id("signing")
  id("com.github.johnrengelman.shadow") version "8.1.1"
  kotlin("jvm") version "2.0.20"
  war
  id("org.beryx.runtime") version "1.13.0"
  application
}

application {
  mainClass.set("com.simiacryptus.skyenet.DaemonClient")
}
// Create a task to run the server directly (for development)
tasks.register<JavaExec>("runServer") {
  group = "application"
  description = "Run the AppServer directly (not as daemon)"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.simiacryptus.skyenet.AppServer")
  args = listOf("server")
}
// Create a task to stop the server
tasks.register<JavaExec>("stopServer") {
  group = "application"
  description = "Stop the running server"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.simiacryptus.skyenet.DaemonClient")
  args = listOf("--stop")
}


// Set jpackage type to 'deb' on Linux to avoid 'rpm' errors
runtime {
  options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
  modules.set(
    listOf(
      "java.base", "java.desktop", "java.logging", "java.naming", "java.net.http", "java.sql", "jdk.crypto.ec"
    )
  )
  // Set jpackage type to 'deb' on Linux to avoid 'rpm' errors
  jpackage {
    val os = System.getProperty("os.name").lowercase()
    // Only set one type per OS, and avoid setting both --type app-image and --type deb/rpm
    if (os.contains("linux")) {
      imageOptions.clear()
      imageOptions.addAll(listOf("--type", "deb"))
    }
    if (os.contains("mac")) {
      imageOptions.clear()
      imageOptions.addAll(listOf("--type", "dmg"))
    }
    if (os.contains("windows")) {
      imageOptions.clear()
      imageOptions.addAll(listOf("--type", "msi"))
    }
  }
}

allprojects {
  java {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(17))
    }
  }
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      javaParameters = true
      jvmTarget = "17"
    }
  }
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

val jetty_version = "11.0.24"
val skyenet_version = "1.2.22"
val scala_version = "2.13.9"
val jackson_version = "2.17.2"
val jupiter_version = "5.10.1"
val logback_version = "1.5.13"
dependencies {
  implementation("org.apache.xmlgraphics:batik-transcoder:1.14")
  implementation("org.apache.xmlgraphics:batik-codec:1.14")
  
  implementation("org.openjfx:javafx-swing:17")
  implementation("org.openjfx:javafx-graphics:17")
  implementation("org.openjfx:javafx-base:17")

//  implementation("org.postgresql:postgresql:42.7.2")
  
  implementation(group = "com.simiacryptus", name = "jo-penai", version = "1.1.13")
  
  implementation("org.apache.commons:commons-text:1.11.0")
  
  implementation(group = "com.simiacryptus.skyenet", name = "core", version = skyenet_version)
  implementation(group = "com.simiacryptus.skyenet", name = "groovy", version = skyenet_version)
  implementation(group = "com.simiacryptus.skyenet", name = "kotlin", version = skyenet_version)
  //implementation(group = "com.simiacryptus.skyenet", name = "scala", version = skyenet_version)
  implementation(group = "com.simiacryptus.skyenet", name = "webui", version = skyenet_version)

  implementation(group = "software.amazon.awssdk", name = "aws-sdk-java", version = "2.27.23")
  implementation("org.jsoup:jsoup:1.19.1")
  
  implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = jackson_version)
  implementation(group = "com.fasterxml.jackson.core", name = "jackson-annotations", version = jackson_version)
  implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = jackson_version)
  
  implementation(group = "com.google.guava", name = "guava", version = "32.1.3-jre")
  implementation(group = "org.eclipse.jetty", name = "jetty-server", version = jetty_version)
  implementation(group = "org.eclipse.jetty", name = "jetty-webapp", version = jetty_version)
  implementation(group = "org.eclipse.jetty.websocket", name = "websocket-jetty-server", version = jetty_version)
  implementation(group = "org.apache.httpcomponents.client5", name = "httpclient5-fluent", version = "5.2.3")
  implementation(group = "com.google.code.gson", name = "gson", version = "2.10.1")
  implementation(group = "com.h2database", name = "h2", version = "2.2.224")
  
  implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.8.0-RC")
  implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable", version = "0.3.8")
  implementation(kotlin("stdlib"))
  implementation(kotlin("scripting-jsr223"))
  implementation(kotlin("scripting-jvm"))
  implementation(kotlin("scripting-jvm-host"))
  implementation(kotlin("script-runtime"))
  implementation(kotlin("scripting-compiler-embeddable"))
  implementation(kotlin("compiler-embeddable"))
  
  implementation(group = "org.scala-lang", name = "scala-library", version = scala_version)
  implementation(group = "org.scala-lang", name = "scala-compiler", version = scala_version)
  implementation(group = "org.scala-lang", name = "scala-reflect", version = scala_version)
  
  implementation(group = "commons-io", name = "commons-io", version = "2.15.0")
  implementation(group = "com.vladsch.flexmark", name = "flexmark-all", version = "0.64.8")
  implementation(platform("software.amazon.awssdk:bom:2.27.23"))
  implementation(group = "software.amazon.awssdk", name = "aws-sdk-java", version = "2.21.29")
  implementation(group = "software.amazon.awssdk", name = "sso", version = "2.21.29")
  
  implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.16")
  implementation(group = "ch.qos.logback", name = "logback-classic", version = logback_version)
  implementation(group = "ch.qos.logback", name = "logback-core", version = logback_version)
  
  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = jupiter_version)
  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params", version = jupiter_version)
  testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = jupiter_version)
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
      "Main-Class" to "com.simiacryptus.skyenet.DaemonClient"
    )
  }
}
tasks.withType<ShadowJar> {
  archiveClassifier.set("all")
  mergeServiceFiles()
  isZip64 = true
  manifest {
    attributes(
      "Main-Class" to "com.simiacryptus.skyenet.DaemonClient"
    )
  }
}

// Platform-specific packaging using jpackage: Only use supported types for each OS
// Helper to install context menu actions for folders
fun installContextMenuAction(os: String) {
  val appName = "SkyenetApps"
  val appDisplayName = "Open with SkyenetApps"
  val scriptPath = layout.buildDirectory.dir("jpackage/scripts").get().asFile
  scriptPath.mkdirs()
  when {
    os.contains("windows") -> {
      // Write a .reg file to add context menu for folders
      val regFile = scriptPath.resolve("add_skyenetapps_context_menu.reg")
      regFile.writeText(
        """
            Windows Registry Editor Version 5.00

            [HKEY_CLASSES_ROOT\Directory\shell\${appDisplayName}]
            @="${appDisplayName}"
            "Icon"="\"%ProgramFiles%\\$appName\\$appName.exe\""

            [HKEY_CLASSES_ROOT\Directory\shell\${appDisplayName}\command]
            @="\"%ProgramFiles%\\$appName\\$appName.exe\" \"%1\""
              [HKEY_CLASSES_ROOT\Directory\shell\Stop SkyenetApps Server]
              @="Stop SkyenetApps Server"
              "Icon"="\"%ProgramFiles%\\$appName\\$appName.exe\""
              [HKEY_CLASSES_ROOT\Directory\shell\Stop SkyenetApps Server\command]
              @="\"%ProgramFiles%\\$appName\\$appName.exe\" \"--stop\""
        """.trimIndent()
      )
      println("Wrote context menu .reg file to: $regFile")
    }
    
    os.contains("mac") -> {
      // Write a .plist file for a Finder Quick Action (Service)
      val plistFile = scriptPath.resolve("SkyenetApps.workflow/Contents/info.plist")
      plistFile.parentFile.mkdirs()
      plistFile.writeText(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>CFBundleIdentifier</key>
              <string>com.simiacryptus.skyenetapps.workflow</string>
              <key>CFBundleName</key>
              <string>$appDisplayName</string>
              <key>NSServices</key>
              <array>
                <dict>
                  <key>NSMenuItem</key>
                  <dict>
                    <key>default</key>
                    <string>$appDisplayName</string>
                  </dict>
                  <key>NSMessage</key>
                  <string>runWorkflowAsService</string>
                  <key>NSSendFileTypes</key>
                  <array>
                    <string>public.folder</string>
                  </array>
                </dict>
              </array>
            </dict>
            </plist>
        """.trimIndent()
      )
      // Write a shell script to launch the app with the folder path
      val script = scriptPath.resolve("SkyenetApps.workflow/Contents/document.wflow")
      script.writeText(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>actions</key>
              <array>
                <dict>
                  <key>action</key>
                  <string>com.apple.cocoa.application.run.shellscript</string>
                  <key>parameters</key>
                  <dict>
                    <key>inputMethod</key>
                    <string>arguments</string>
                    <key>script</key>
                    <string>open -a "$appName" "$1"</string>
                  </dict>
                </dict>
              </array>
              <key>workflowTypeIdentifier</key>
              <string>com.apple.Automator.services</string>
            </dict>
            </plist>
        """.trimIndent()
      )
      // Add a separate Quick Action for stopping the server
      val stopScriptDir = scriptPath.resolve("StopSkyenetApps.workflow/Contents")
      stopScriptDir.mkdirs()
      val stopPlistFile = stopScriptDir.resolve("info.plist")
      stopPlistFile.writeText(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>CFBundleIdentifier</key>
              <string>com.simiacryptus.skyenetapps.stop.workflow</string>
              <key>CFBundleName</key>
              <string>Stop SkyenetApps Server</string>
              <key>NSServices</key>
              <array>
                <dict>
                  <key>NSMenuItem</key>
                  <dict>
                    <key>default</key>
                    <string>Stop SkyenetApps Server</string>
                  </dict>
                  <key>NSMessage</key>
                  <string>runWorkflowAsService</string>
                </dict>
              </array>
            </dict>
            </plist>
        """.trimIndent()
      )
      val stopScript = stopScriptDir.resolve("document.wflow")
      stopScript.writeText(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>actions</key>
              <array>
                <dict>
                  <key>action</key>
                  <string>com.apple.cocoa.application.run.shellscript</string>
                  <key>parameters</key>
                  <dict>
                    <key>inputMethod</key>
                    <string>arguments</string>
                    <key>script</key>
                    <string>open -a "$appName" --args --stop</string>
                  </dict>
                </dict>
              </array>
              <key>workflowTypeIdentifier</key>
              <string>com.apple.Automator.services</string>
            </dict>
            </plist>
        """.trimIndent()
      )
      println("Wrote stop server Quick Action to: ${stopScript.parentFile}")
      println("Wrote context menu Quick Action to: ${script.parentFile}")
    }
    
    
    os.contains("linux") -> {
      // Write a .desktop file for Nautilus/Thunar context menu
      val desktopFile = scriptPath.resolve("skyenetapps-folder-action.desktop")
      desktopFile.writeText(
        """
          [Desktop Entry]
          Type=Application
          Name=$appDisplayName
          Comment=Open folders with SkyenetApps
      Icon=/opt/skyenetapps/lib/icon.png
          Exec=/opt/skyenetapps/bin/SkyenetApps "%f"
          MimeType=inode/directory;
          Categories=Development;Utility;TextEditor;
          Terminal=false
          StartupNotify=true
          Actions=OpenFolder;
          [Desktop Action OpenFolder]
          Name=Open Folder with SkyenetApps
          Exec=/opt/skyenetapps/bin/SkyenetApps "%f"
        """.trimIndent()
      )
      // Also create a main application desktop file
      val mainDesktopFile = layout.buildDirectory.dir("jpackage/resources").get().asFile.resolve("skyenetapps.desktop")
      mainDesktopFile.parentFile.mkdirs()
      mainDesktopFile.writeText(
        """
          [Desktop Entry]
          Type=Application
          Name=SkyenetApps
          Comment=AI-powered application suite
          Icon=/opt/skyenetapps/lib/icon.png
          Exec=/opt/skyenetapps/bin/SkyenetApps %f
          Categories=Development;Utility;TextEditor;
          MimeType=inode/directory;text/plain;
          Terminal=false
          StartupNotify=true
        """.trimIndent()
      )
      println("Created main application .desktop file to: $mainDesktopFile")
      println("Created context menu .desktop file to: $desktopFile")
    }
  }
}

// Use ExecOperations for exec, to avoid deprecation
abstract class JPackageTask : DefaultTask() {
  @get:Inject
  abstract val execOperations: org.gradle.process.ExecOperations
}

tasks.register("packageDmg", JPackageTask::class) {
  group = "distribution"
  description = "Creates a .dmg package for macOS"
  onlyIf { System.getProperty("os.name").lowercase().contains("mac") }
  doLast {
    execOperations.exec {
      commandLine(
        "jpackage",
        "--type", "dmg",
        "--input", layout.buildDirectory.dir("libs").get().asFile.path,
        "--main-jar", "${project.name}-${project.version}-all.jar",
        "--main-class", "com.simiacryptus.skyenet.DaemonClient",
        "--dest", layout.buildDirectory.dir("jpackage").get().asFile.path,
        "--name", "SkyenetApps",
        "--app-version", "${project.version}",
        "--copyright", "Copyright © 2024 SimiaCryptus",
        "--description", "Cognotik Applications Suite"
      )
    }
    installContextMenuAction("mac")
  }
}

tasks.register("packageDeb", JPackageTask::class) {
  group = "distribution"
  description = "Creates a .deb package for Linux"
  onlyIf { System.getProperty("os.name").lowercase().contains("linux") }
  dependsOn("shadowJar", "prepareLinuxDesktopFile")
  doLast {
    // Ensure resources directory exists
    val resourcesDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
    if (!resourcesDir.exists()) {
      resourcesDir.mkdirs()
    }
    // Get the actual shadow jar file name
    val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
    val shadowJarName = shadowJarFile.name
    // Create a post-install script to install the desktop files
    val postinstFile = File(resourcesDir, "postinst")
    postinstFile.writeText(
      """
      #!/bin/sh
      set -e
      # Create desktop files directory if it doesn't exist
      mkdir -p /usr/share/applications
      
      # Copy desktop files from resources to applications directory
      cp "${resourcesDir.absolutePath}/skyenetapps.desktop" /usr/share/applications/skyenetapps.desktop
      cp "${resourcesDir.absolutePath}/skyenetapps-folder-action.desktop" /usr/share/applications/skyenetapps-folder-action.desktop
      
      # Update desktop database
      update-desktop-database /usr/share/applications || true
      exit 0
      """.trimIndent()
    )
    postinstFile.setExecutable(true)

    // Create a pre-remove script to stop the app and clean up desktop files
    val prermFile = File(resourcesDir, "prerm")
    prermFile.writeText(
      """
      #!/bin/sh
      set -e
      # Stop the running SkyenetApps server if any
      if [ -x "/opt/skyenetapps/bin/SkyenetApps" ]; then
        "/opt/skyenetapps/bin/SkyenetApps" --stop || true
      fi
      # Remove desktop files
      rm -f /usr/share/applications/skyenetapps.desktop
      rm -f /usr/share/applications/skyenetapps-folder-action.desktop
      # Update desktop database
      update-desktop-database /usr/share/applications || true
      exit 0
      """.trimIndent()
    )
    prermFile.setExecutable(true)
    
    
    execOperations.exec {
      commandLine(
        "jpackage",
        "--type", "deb",
        "--input", layout.buildDirectory.dir("libs").get().asFile.path,
        "--main-jar", shadowJarName,
        "--main-class", "com.simiacryptus.skyenet.DaemonClient",
        "--dest", layout.buildDirectory.dir("jpackage").get().asFile.path,
        "--name", "SkyenetApps",
        "--app-version", "${project.version}",
        "--copyright", "Copyright © 2024 SimiaCryptus",
        "--description", "Cognotik Applications Suite",
        "--linux-menu-group", "Utilities",
        "--resource-dir", layout.buildDirectory.dir("jpackage/resources").get().asFile.path,
        "--linux-deb-maintainer", "support@simiacryptus.com",
        "--linux-shortcut",
        "--linux-app-category", "Development;Utility",
        "--icon", layout.projectDirectory.file("src/main/resources/icon.png").asFile.path,
        "--linux-package-name", "skyenetapps"
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
    // Create resources directory if it doesn't exist
    val resourcesDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
    if (!resourcesDir.exists()) {
      resourcesDir.mkdirs()
    }
    
      
    // Create the context menu desktop file
    val contextMenuDesktopFile = File(resourcesDir, "skyenetapps-folder-action.desktop")
    contextMenuDesktopFile.writeText(
      """
      [Desktop Entry]
      Type=Application
      Name=Open with SkyenetApps
      Comment=Open folders with SkyenetApps
      Icon=/opt/skyenetapps/lib/icon.png
      Exec=/opt/skyenetapps/bin/SkyenetApps %f
      MimeType=inode/directory;text/plain;
      Categories=Development;Utility;TextEditor;
      Terminal=false
      StartupNotify=true
      Actions=OpenFolder;
      [Desktop Action OpenFolder]
      Name=Open Folder with SkyenetApps
      Exec=/opt/skyenetapps/bin/SkyenetApps %f
      """.trimIndent()
    )
    
    // Create the main application desktop file
    val mainDesktopFile = File(resourcesDir, "skyenetapps.desktop")
    mainDesktopFile.writeText(
      """
      [Desktop Entry]
      Type=Application
      Name=SkyenetApps
      Comment=AI-powered application suite
      Icon=/opt/skyenetapps/lib/icon.png
      Exec=/opt/skyenetapps/bin/SkyenetApps %f
      Categories=Development;Utility;TextEditor;
      MimeType=inode/directory;text/plain;
      Terminal=false
      StartupNotify=true
      Actions=StopServer;
      [Desktop Action StopServer]
      Name=Stop SkyenetApps Server
      Exec=/opt/skyenetapps/bin/SkyenetApps --stop
      Icon=/opt/skyenetapps/lib/icon.png
      """.trimIndent()
    )
      
    // Create a shell script to copy the desktop files to the correct location
    val installScript = File(resourcesDir, "postinst")
    installScript.writeText(
      """
      #!/bin/sh
      set -e
      # Create desktop files directory if it doesn't exist
      mkdir -p /usr/share/applications
      
      # Copy desktop files from resources
      cp "${resourcesDir.absolutePath}/skyenetapps.desktop" /usr/share/applications/skyenetapps.desktop
      cp "${resourcesDir.absolutePath}/skyenetapps-folder-action.desktop" /usr/share/applications/skyenetapps-folder-action.desktop
      
      # Update desktop database
      update-desktop-database /usr/share/applications || true
      exit 0
      """.trimIndent()
    )
    installScript.setExecutable(true)

    // Create a shell script to remove the desktop files and stop the app
    val uninstallScript = File(resourcesDir, "prerm")
    uninstallScript.writeText(
      """
      #!/bin/sh
      set -e
      # Stop the running SkyenetApps server if any
      if [ -x "/opt/skyenetapps/bin/SkyenetApps" ]; then
        "/opt/skyenetapps/bin/SkyenetApps" --stop || true
      fi
      # Remove desktop files
      rm -f /usr/share/applications/skyenetapps.desktop
      rm -f /usr/share/applications/skyenetapps-folder-action.desktop
      # Update desktop database
      update-desktop-database /usr/share/applications || true
      exit 0
      """.trimIndent()
    )
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

// Add a task to run the Linux packaging flow
tasks.register("packageLinux") {
  description = "Creates a Linux package using the custom flow"
  dependsOn("clean", "shadowJar", "packageDeb")
}

// Only depend on "package" if not running the beryx jpackage task, to avoid double packaging
tasks.named("build") {
  dependsOn(tasks.war)
  dependsOn(tasks.shadowJar)
  // Remove dependsOn("package") to avoid running both beryx jpackage and custom package tasks
  // dependsOn(tasks.named("package"))
}
// Add a task to update version from environment variable if present
tasks.register("updateVersionFromEnv") {
  val envVersion = System.getenv("SKYENET_VERSION")
  if (envVersion != null && envVersion.isNotEmpty()) {
    println("Updating version from environment variable: $envVersion")
    project.version = envVersion
  }
}
// Make sure the version is updated before packaging
tasks.named("jpackage") {
  dependsOn("updateVersionFromEnv")
}

// Workaround: Disable the beryx jpackage task if running on Linux and not building RPMs
tasks.named("jpackage") {
  doFirst {
    val os = System.getProperty("os.name").lowercase()
    if (os.contains("linux")) {
      // Check if the app-image exists, otherwise skip to avoid RPM error
      val appImageDir = layout.buildDirectory.dir("jpackage/SkyenetApps").get().asFile
      if (!appImageDir.exists()) {
        logger.warn("Skipping jpackage task: App image directory does not exist: $appImageDir")
        throw org.gradle.api.tasks.StopExecutionException("App image directory does not exist: $appImageDir")
      }
    }
  }
}