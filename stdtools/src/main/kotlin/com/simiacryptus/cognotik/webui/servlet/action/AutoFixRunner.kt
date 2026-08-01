package com.simiacryptus.cognotik.webui.servlet.action
    import com.simiacryptus.cognotik.platform.model.User
    import com.simiacryptus.cognotik.platform.model.defaultUser

    import java.io.File

    /** A fully resolved AutoFix invocation (already jailed to [root] by the action). */
    data class AutoFixRequest(
      val root: File,
      /** Working directory relative to [root]; blank = the root itself. */
      val dir: String = "",
      val commands: List<String>,
      val autoFix: Boolean = true,
      val timeoutMinutes: Long = 30,
      /** Owner of the credentials *and* of the model selection (its stored settings). */
      val user: User = defaultUser,
      /**
       * Resolved from [user]'s persisted settings by the action — there is no static or
       * environment-configured fallback. null = nothing has been selected yet.
       */
      val smartModel: String? = null,
      val fastModel: String? = null,
      /** true = no monitor server, so patches must be applied without an approval click. */
      val serverless: Boolean = true,
    )

    /**
     * Pluggable "run a command and fix whatever it reports" implementation. The concrete
     * agent lives outside stdtools (the CLI supplies `AutoFixCli`), so the action itself
     * stays dependency-free; when no runner is installed the `autofix` operation is simply
     * not registered.
     */
    fun interface AutoFixRunner {
      /** Runs the loop and returns a process-style exit code. Stdout is captured by [FsTasks]. */
      fun run(request: AutoFixRequest): Int
    }