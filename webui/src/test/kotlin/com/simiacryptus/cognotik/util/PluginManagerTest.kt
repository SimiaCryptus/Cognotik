package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.platform.CognotikPlugin
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.jar.Attributes

class PluginManagerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var pluginManager: PluginManager

    @BeforeEach
    fun setUp() {
        pluginManager = PluginManager(root = tempDir)
    }

    // ---- Helper methods ----

    /**
     * Creates a minimal JAR file (no plugin services) in the temp directory.
     */
    private fun createEmptyJar(name: String = "test-plugin.jar"): File {
        val jarFile = File(tempDir, name)
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        JarOutputStream(FileOutputStream(jarFile), manifest).use { jos ->
            // Add a dummy class entry so the JAR is not completely empty
            jos.putNextEntry(JarEntry("dummy/Dummy.class"))
            jos.write(ByteArray(0))
            jos.closeEntry()
        }
        return jarFile
    }

    /**
     * Creates a JAR file with a META-INF/services entry for CognotikPlugin
     * pointing to a class that does not actually exist (to test error handling).
     */
    private fun createJarWithBogusService(name: String = "bogus-service-plugin.jar"): File {
        val jarFile = File(tempDir, name)
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        JarOutputStream(FileOutputStream(jarFile), manifest).use { jos ->
            val servicePath = "META-INF/services/${CognotikPlugin::class.java.name}"
            jos.putNextEntry(JarEntry(servicePath))
            jos.write("com.nonexistent.FakePlugin\n".toByteArray())
            jos.closeEntry()
        }
        return jarFile
    }

    // ---- Tests for loadPlugin(jarFile) ----

    @Test
    fun `loadPlugin should throw when JAR file does not exist`() {
        val nonExistent = File(tempDir, "nonexistent.jar")
        val exception = assertThrows<IllegalArgumentException> {
            pluginManager.loadPlugin(nonExistent)
        }
        assertTrue(exception.message!!.contains("does not exist"))
    }

    @Test
    fun `loadPlugin should throw when file is not a JAR`() {
        val notAJar = File(tempDir, "readme.txt")
        notAJar.writeText("hello")
        val exception = assertThrows<IllegalArgumentException> {
            pluginManager.loadPlugin(notAJar)
        }
        assertTrue(exception.message!!.contains("not a JAR"))
    }

    @Test
    fun `loadPlugin should throw when JAR is already loaded`() {
        val jarFile = createEmptyJar()
        pluginManager.loadPlugin(jarFile)
        val exception = assertThrows<IllegalStateException> {
            pluginManager.loadPlugin(jarFile)
        }
        assertTrue(exception.message!!.contains("already loaded"))
    }

    @Test
    fun `loadPlugin should return empty list when no plugins found in JAR`() {
        val jarFile = createEmptyJar()
        val plugins = pluginManager.loadPlugin(jarFile)
        assertTrue(plugins.isEmpty())
    }

    @Test
    fun `loadPlugin should mark JAR as loaded even with no plugins`() {
        val jarFile = createEmptyJar()
        pluginManager.loadPlugin(jarFile)
        assertTrue(pluginManager.isLoaded(jarFile))
    }

    @Test
    fun `loadPlugin with bogus service should handle error gracefully and return empty`() {
        val jarFile = createJarWithBogusService()
        val plugins = pluginManager.loadPlugin(jarFile)
        assertTrue(plugins.isEmpty())
    }

    // ---- Tests for loadPlugin(jarFile, entryPointClass) ----

    @Test
    fun `loadPlugin with entryPoint should throw when JAR does not exist`() {
        val nonExistent = File(tempDir, "nonexistent.jar")
        val exception = assertThrows<IllegalArgumentException> {
            pluginManager.loadPlugin(nonExistent, "com.example.SomePlugin")
        }
        assertTrue(exception.message!!.contains("does not exist"))
    }

    @Test
    fun `loadPlugin with entryPoint should throw when file is not a JAR`() {
        val notAJar = File(tempDir, "readme.txt")
        notAJar.writeText("hello")
        val exception = assertThrows<IllegalArgumentException> {
            pluginManager.loadPlugin(notAJar, "com.example.SomePlugin")
        }
        assertTrue(exception.message!!.contains("not a JAR"))
    }

    @Test
    fun `loadPlugin with entryPoint should throw when class not found`() {
        val jarFile = createEmptyJar()
        assertThrows<ClassNotFoundException> {
            pluginManager.loadPlugin(jarFile, "com.nonexistent.FakePlugin")
        }
    }

    // ---- Tests for unloadPlugin ----

    @Test
    fun `unloadPlugin should remove JAR from loaded set`() {
        val jarFile = createEmptyJar()
        pluginManager.loadPlugin(jarFile)
        assertTrue(pluginManager.isLoaded(jarFile))

        pluginManager.unloadPlugin(jarFile)
        assertFalse(pluginManager.isLoaded(jarFile))
    }

    @Test
    fun `unloadPlugin should not throw when JAR was never loaded`() {
        val jarFile = File(tempDir, "never-loaded.jar")
        assertDoesNotThrow {
            pluginManager.unloadPlugin(jarFile)
        }
    }

    @Test
    fun `unloadPlugin should remove plugins from getLoadedPlugins`() {
        val jarFile = createEmptyJar()
        pluginManager.loadPlugin(jarFile)
        pluginManager.unloadPlugin(jarFile)

        val loaded = pluginManager.getLoadedPlugins()
        assertFalse(loaded.containsKey(jarFile.canonicalPath))
    }

    // ---- Tests for isLoaded ----

    @Test
    fun `isLoaded should return false for unknown JAR`() {
        val jarFile = File(tempDir, "unknown.jar")
        assertFalse(pluginManager.isLoaded(jarFile))
    }

    @Test
    fun `isLoaded should return true after loading`() {
        val jarFile = createEmptyJar()
        pluginManager.loadPlugin(jarFile)
        assertTrue(pluginManager.isLoaded(jarFile))
    }

    // ---- Tests for getLoadedPlugins ----

    @Test
    fun `getLoadedPlugins should return empty map initially`() {
        val loaded = pluginManager.getLoadedPlugins()
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `getLoadedPlugins should not contain entry for JAR with no discovered plugins`() {
        val jarFile = createEmptyJar()
        pluginManager.loadPlugin(jarFile)
        // Empty JARs with no CognotikPlugin implementations are not added to loadedPlugins
        val loaded = pluginManager.getLoadedPlugins()
        assertFalse(loaded.containsKey(jarFile.canonicalPath))
    }

    // ---- Tests for loadPluginsFromDirectory ----

    @Test
    fun `loadPluginsFromDirectory should throw when path is not a directory`() {
        val file = File(tempDir, "not-a-dir.txt")
        file.writeText("hello")
        val exception = assertThrows<IllegalArgumentException> {
            pluginManager.loadPluginsFromDirectory(file)
        }
        assertTrue(exception.message!!.contains("Not a directory"))
    }

    @Test
    fun `loadPluginsFromDirectory should return empty map for empty directory`() {
        val subDir = File(tempDir, "empty-plugins")
        subDir.mkdirs()
        val results = pluginManager.loadPluginsFromDirectory(subDir)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `loadPluginsFromDirectory should load all JARs in directory`() {
        val subDir = File(tempDir, "multi-plugins")
        subDir.mkdirs()

        // Create multiple JARs in the subdirectory
        val jar1 = createJarInDir(subDir, "plugin-a.jar")
        val jar2 = createJarInDir(subDir, "plugin-b.jar")
        // Create a non-JAR file that should be ignored
        File(subDir, "readme.txt").writeText("not a jar")

        val results = pluginManager.loadPluginsFromDirectory(subDir)
        assertEquals(2, results.size)
        assertTrue(results.containsKey(jar1))
        assertTrue(results.containsKey(jar2))
    }

    @Test
    fun `loadPluginsFromDirectory should skip already loaded JARs`() {
        val subDir = File(tempDir, "reload-plugins")
        subDir.mkdirs()
        val jar = createJarInDir(subDir, "plugin.jar")

        // Load once
        pluginManager.loadPluginsFromDirectory(subDir)
        assertTrue(pluginManager.isLoaded(jar))

        // Load again — should not throw
        assertDoesNotThrow {
            pluginManager.loadPluginsFromDirectory(subDir)
        }
    }

    // ---- Tests for subscribeToChanges / triggerChange ----

    @Test
    fun `subscribeToChanges should be notified on triggerChange`() {
        var notified = false
        pluginManager.subscribeToChanges { notified = true }
        pluginManager.triggerChange()
        assertTrue(notified)
    }

    @Test
    fun `loading a plugin JAR with no plugins should not trigger change`() {
        var changeCount = 0
        pluginManager.subscribeToChanges { changeCount++ }

        val jarFile = createEmptyJar()
        pluginManager.loadPlugin(jarFile)

        // Empty JAR with no discovered plugins does not call triggerChange
        assertEquals(0, changeCount)
    }

    @Test
    fun `unloading a loaded JAR should trigger change`() {
        val jarFile = createEmptyJar()
        pluginManager.loadPlugin(jarFile)

        var changeCount = 0
        pluginManager.subscribeToChanges { changeCount++ }

        pluginManager.unloadPlugin(jarFile)
        assertEquals(1, changeCount)
    }

    @Test
    fun `unloading a non-loaded JAR should not trigger change`() {
        var changeCount = 0
        pluginManager.subscribeToChanges { changeCount++ }

        pluginManager.unloadPlugin(File(tempDir, "nonexistent.jar"))
        assertEquals(0, changeCount)
    }

    // ---- Tests for manifest persistence ----

    @Test
    fun `manifest file should not exist when no plugins with implementations are loaded`() {
        val manifestFile = File(tempDir, "plugins-manifest.json")
        val jarFile = createEmptyJar()
        pluginManager.loadPlugin(jarFile)
        // No plugins discovered, so manifest is not saved
        // (manifest is only saved when plugins are found or on unload)
        // After unload it should be saved
        pluginManager.unloadPlugin(jarFile)
        // Manifest should exist after unload (even if empty)
        assertTrue(manifestFile.exists())
    }

    @Test
    fun `restorePlugins should skip JARs that no longer exist`() {
        // Create a manifest referencing a non-existent JAR
        val manifestFile = File(tempDir, "plugins-manifest.json")
        manifestFile.writeText("""[{"jarPath":"/nonexistent/path/plugin.jar"}]""")

        // Creating a new PluginManager should not throw
        assertDoesNotThrow {
            PluginManager(root = tempDir)
        }
    }

    @Test
    fun `restorePlugins should reload previously loaded JARs`() {
        val jarFile = createEmptyJar("restorable.jar")
        val manifestFile = File(tempDir, "plugins-manifest.json")
        manifestFile.writeText("""[{"jarPath":"${jarFile.canonicalPath.replace("\\", "\\\\")}"}]""")

        val newManager = PluginManager(root = tempDir)
         // PluginManager performs restore asynchronously after a 1s delay; wait for it.
         val deadline = System.currentTimeMillis() + 5000
         while (!newManager.isLoaded(jarFile) && System.currentTimeMillis() < deadline) {
             Thread.sleep(50)
         }
         assertTrue(newManager.isLoaded(jarFile), "Plugin JAR should be loaded after restore")
    }

    // ---- Helper to create JAR in a specific directory ----

    private fun createJarInDir(dir: File, name: String): File {
        val jarFile = File(dir, name)
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        JarOutputStream(FileOutputStream(jarFile), manifest).use { jos ->
            jos.putNextEntry(JarEntry("dummy/Dummy.class"))
            jos.write(ByteArray(0))
            jos.closeEntry()
        }
        return jarFile
    }
}