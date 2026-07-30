package com.simiacryptus.cognotik.cli

    import com.simiacryptus.cognotik.docops.UpdateMode
    import com.simiacryptus.cognotik.webui.servlet.action.ServerDocOps as StdServerDocOps

    /**
     * Thin compatibility shim: the DocOps driver now lives in **stdtools**
     * ([com.simiacryptus.cognotik.webui.servlet.action.ServerDocOps]) so every server gets it,
     * and so all invocations go through the installed `DocProcessorServlet` (which may be a
     * proxy). Only the constants used by the CLI's own plumbing are kept here.
     */
    object ServerDocOps {

      const val DEFAULT_MODE = StdServerDocOps.DEFAULT_MODE

      val MODES: List<String> get() = StdServerDocOps.MODES

      fun checkMode(mode: String): UpdateMode = StdServerDocOps.checkMode(mode)
    }