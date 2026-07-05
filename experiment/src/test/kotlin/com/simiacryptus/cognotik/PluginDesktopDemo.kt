package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.desktop.CognotikApps
import java.awt.Desktop

class PluginDesktopDemo(localName: String, publicName: String, port: Int) : CognotikApps(localName, publicName, port) {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      PluginDesktopDemo(
        localName = "localhost",
        publicName = "localhost",
        port = findAvailablePort(12892)
      ).apply {
        Thread {init(port, args)}.start()
        Desktop.getDesktop().browse(java.net.URI("http://${publicName}:${port}/"))
      }
    }
  }

  override fun init(actualPort: Int, args: Array<out String>) {
    ExperimentalStuff().init()
    super.init(actualPort, args)
  }
}