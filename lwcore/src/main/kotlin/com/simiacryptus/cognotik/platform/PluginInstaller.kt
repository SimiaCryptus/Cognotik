package com.simiacryptus.cognotik.platform

import java.io.File

/**
 * Installation/removal of plugin artifacts.
 *
 * Irreversible filesystem side effects live here rather than in
 * [PluginRegistry]/[PluginManagerInterface] (REVIEW.md §3.8).
 */
interface PluginInstaller {

  /**
   * Delete a plugin JAR file from disk.
   * If the plugin is currently loaded, it will be unloaded first; if unloading
   * fails the file is left in place and the failure is propagated.
   *
   * @param jarFile the JAR file to delete
   * @throws PluginNotFoundException if the file does not exist
   */
  fun deletePlugin(jarFile: File)

  /**
   * Copy/verify an artifact into the managed plugin directory without loading it.
   *
   * @return the installed file location
   */
  fun installPlugin(jarFile: File): File =
    throw UnsupportedOperationException("installPlugin is not implemented by ${this.javaClass.name}")
}