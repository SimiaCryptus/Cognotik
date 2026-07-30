package com.simiacryptus.cognotik.webui.servlet.action

    import com.simiacryptus.cognotik.webui.servlet.DocProcessorServlet

    /**
     * The single, process-wide handle on the **DocOps endpoint**.
     *
     * Every DocOps invocation - the `/docops` HTTP mount, the `.fsapi/v1/docops` action,
     * the `?resolveParam=target` option list - must be routed through the servlet that is
     * *installed here*, because in some environments that servlet is swapped for a proxy
     * implementation (remote / sandboxed / credential-brokering) and the proxy is the only
     * legal way to run doc-ops. Nothing outside of this registry may construct its own
     * `DocProcessor`: [ServerDocOps] only ever calls the servlet's overridable API
     * ([DocProcessorServlet.newProcessor], [DocProcessorServlet.plan],
     * [DocProcessorServlet.initializeStatus], [DocProcessorServlet.runPlan],
     * [DocProcessorServlet.modelsFor]), so a proxy subclass wins by construction.
     *
     * Install a *provider* (rather than an instance) when the endpoint is created lazily or
     * may be replaced at runtime:
     *
     * ```
     * DocOpsServlets.install { myProxyDocProcessorServlet }
     * ```
     */
    object DocOpsServlets {

      @Volatile
      private var provider: (() -> DocProcessorServlet?)? = null

      /** Publishes a fixed instance (typically the one also mounted at `/docops`). */
      fun install(servlet: DocProcessorServlet) {
        provider = { servlet }
      }

      /** Publishes a late-bound endpoint; called on every resolution, so swaps take effect. */
      fun install(provider: () -> DocProcessorServlet?) {
        this.provider = provider
      }

      fun uninstall() {
        provider = null
      }

      val isInstalled: Boolean get() = current != null

      /** The endpoint to use right now, or null when doc-ops is not available. */
      val current: DocProcessorServlet? get() = provider?.invoke()

      fun require(): DocProcessorServlet = current ?: throw IllegalStateException(
        "No DocOps servlet is installed; call DocOpsServlets.install(...) with the " +
            "endpoint (or its proxy) before invoking doc-ops"
      )
    }