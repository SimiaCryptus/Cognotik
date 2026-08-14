package com.simiacryptus.cognotik.cli

    import com.simiacryptus.cognotik.CoreProviders
    import com.simiacryptus.cognotik.models.APIProvider
    import com.simiacryptus.cognotik.platform.ApplicationServices
    import com.simiacryptus.cognotik.platform.ApiData
    import com.simiacryptus.cognotik.platform.model.User
    import com.simiacryptus.cognotik.platform.UserSettingsInterface
    import com.simiacryptus.cognotik.util.SecureString
    import java.io.File
    import java.io.PrintStream
    import kotlin.system.exitProcess

    /**
     * Interactive **API key configuration CLI**.
     *
     * ```
     * cognotik keys                       # interactive: list providers, pick a number, paste key
     * cognotik keys --list                # show what is already configured (keys masked)
     * cognotik keys --provider OpenAI --key sk-...     # non-interactive
     * cognotik keys --remove OpenAI
     * ```
     *
     * Design notes:
     *
     *  1. **No secrets on screen.** When a real console is attached the key is read with
     *     [java.io.Console.readPassword] so it is never echoed. Stored keys are only ever
     *     displayed masked.
     *  2. **No secrets in the shell history by default.** `--key` exists for scripting, but the
     *     interactive flow is the documented path.
     *  3. **One writer.** Everything goes through [UserSettingsInterface.updateUserSettings], so
     *     the same file/DB the rest of the platform reads is updated.
     */
    object ApiKeysCli {

      @JvmStatic
      fun main(args: Array<String>) {
        val exitCode = try {
          run(args)
        } catch (e: IllegalArgumentException) {
          System.err.println("keys: ${e.message}")
          printUsage(System.err)
          2
        } catch (e: Throwable) {
          System.err.println("keys: ${e.message ?: e.toString()}")
          if (System.getenv("COGNOTIK_DEBUG") != null) e.printStackTrace()
          1
        }
        // One-shot CLI: never linger.
        exitProcess(exitCode)
      }

      fun run(args: Array<String>): Int {
        var root = File(".").canonicalFile
        var email: String? = null
        var providerName: String? = null
        var key: String? = null
        var baseUrl: String? = null
        var removeName: String? = null
        var listOnly = false
        var verify = false
        var i = 0

        fun value(name: String): String {
          if (i + 1 >= args.size) throw IllegalArgumentException("missing value for $name")
          return args[++i]
        }

        while (i < args.size) {
          val arg = args[i]
          when (arg) {
            "-h", "--help" -> {
              printUsage(System.out)
              return 0
            }

            "--root" -> root = File(value(arg)).canonicalFile
            "--email" -> email = value(arg)
            "--provider" -> providerName = value(arg)
            "--key" -> key = value(arg)
            "--base" -> baseUrl = value(arg)
            "--remove" -> removeName = value(arg)
            "--list" -> listOnly = true
            "--verify" -> verify = true
            else -> throw IllegalArgumentException("unknown option: $arg")
          }
          i++
        }

        installServices()
        initProviders()
        val user = defaultUser(email)

        return when {
          listOnly -> {
            printProviders(providers(), configured(user))
            0
          }

          removeName != null -> remove(user, resolveProvider(removeName))
          providerName != null -> {
            val provider = resolveProvider(providerName)
            val secret = key?.takeIf { it.isNotBlank() } ?: promptKey(provider) ?: return 1
            save(user, provider, secret, baseUrl, verify)
            0
          }

          else -> configure(root = root, user = user, verify = verify, installServices = false)
        }
      }

      /**
       * Interactive loop: list -> select number -> enter key -> done.
       * Safe to call from another CLI that has already installed platform services
       * (pass `installServices = false`).
       */
      fun configure(
        root: File,
        user: User,
        verify: Boolean = false,
        installServices: Boolean = true,
      ): Int {
        if (installServices) installServices()
        initProviders()
        val providers = providers()
        if (providers.isEmpty()) {
          System.err.println("No API providers are registered - nothing to configure.")
          return 1
        }
        println("Configuring API keys for ${user.email} (storage root: ${root.absolutePath})")
        while (true) {
          println()
          printProviders(providers, configured(user))
          print("Select a provider number (Enter or 'q' to finish): ")
          System.out.flush()
          val line = readLine()?.trim()
          if (line == null || line.isEmpty() || line.equals("q", true) || line.equals("quit", true) || line.equals("done", true)) {
            println("Done.")
            return 0
          }
          val index = line.toIntOrNull()
          if (index == null || index < 1 || index > providers.size) {
            println("  ! '$line' is not a number between 1 and ${providers.size}")
            continue
          }
          val provider = providers[index - 1]
          val secret = promptKey(provider) ?: continue
          save(user, provider, secret, promptBase(provider), verify)
        }
      }

      /*
       * ------------------------------------------------------------------
       * Prompts
       * ------------------------------------------------------------------
       */

      private fun promptKey(provider: APIProvider): String? {
        val prompt = "Enter API key for ${provider.name} (blank to cancel): "
        val console = System.console()
        val raw = if (console != null) {
          console.readPassword(prompt)?.let { String(it) }
        } else {
          print(prompt)
          System.out.flush()
          readLine()
        }
        val key = raw?.trim()
        if (key.isNullOrEmpty()) {
          println("  cancelled - no key stored for ${provider.name}")
          return null
        }
        return key
      }

      private fun promptBase(provider: APIProvider): String? {
        val default = provider.base.ifBlank { "" }
        print("Base URL [${default.ifBlank { "<none>" }}] (Enter to keep default): ")
        System.out.flush()
        val line = readLine()?.trim()
        return line?.takeIf { it.isNotEmpty() }
      }

      /*
       * ------------------------------------------------------------------
       * Persistence
       * ------------------------------------------------------------------
       */

      private fun save(user: User, provider: APIProvider, key: String, baseUrl: String?, verify: Boolean) {
        val secure = SecureString(key)
        val base = baseUrl?.takeIf { it.isNotBlank() } ?: provider.base.ifBlank { null }
        if (verify) verify(provider, secure, base ?: "")
        val manager = ApplicationServices.fileApplicationServices().userSettingsManager
        val settings = manager.getUserSettings(user)
        val apis = settings.apis.filterNot { it.provider == provider }.toMutableList()
        apis.add(
          ApiData(
            name = provider.name,
            key = secure,
            baseUrl = baseUrl?.takeIf { it.isNotBlank() },
            provider = provider,
          ).validate()
        )
        manager.updateUserSettings(user, settings.copy(apis = apis))
        println("  saved ${provider.name} -> ${masked(key)}" + (base?.let { " @ $it" } ?: ""))
      }

      private fun remove(user: User, provider: APIProvider): Int {
        val manager = ApplicationServices.fileApplicationServices().userSettingsManager
        val settings = manager.getUserSettings(user)
        val remaining = settings.apis.filterNot { it.provider == provider }.toMutableList()
        if (remaining.size == settings.apis.size) {
          println("No key was configured for ${provider.name}.")
          return 0
        }
        manager.updateUserSettings(user, settings.copy(apis = remaining))
        println("Removed key for ${provider.name}.")
        return 0
      }

      private fun verify(provider: APIProvider, key: SecureString, base: String) {
        try {
          val models = provider.getChatModels(key, base)
          val count = (models as? Collection<*>)?.size
          println("  verified: ${count ?: "?"} model(s) visible")
        } catch (e: Exception) {
          println("  ! verification failed: ${e.message} (key stored anyway)")
        }
      }

      /*
       * ------------------------------------------------------------------
       * Rendering
       * ------------------------------------------------------------------
       */

      private fun printProviders(providers: List<APIProvider>, configured: Map<APIProvider, ApiData>) {
        val nameWidth = maxOf(8, providers.maxOfOrNull { it.name.length } ?: 8)
        println("Available providers:")
        println("   # " + "PROVIDER".padEnd(nameWidth) + "  " + "KEY".padEnd(14) + "  BASE URL")
        providers.forEachIndexed { index, provider ->
          val existing = configured[provider]
          val keyCell = if (existing?.key != null) "configured" else "-"
          val baseCell = existing?.baseUrl?.takeIf { it.isNotBlank() } ?: provider.base.ifBlank { "<none>" }
          println(
            "  ${(index + 1).toString().padStart(2)} " +
                provider.name.padEnd(nameWidth) + "  " + keyCell.padEnd(14) + "  " + baseCell
          )
        }
      }

      private fun masked(key: String): String = when {
        key.length <= 8 -> "*".repeat(key.length)
        else -> key.take(4) + "*".repeat(key.length - 8).take(8) + key.takeLast(4)
      }

      /*
       * ------------------------------------------------------------------
       * Platform plumbing
       * ------------------------------------------------------------------
       */

      private fun installServices() {
        try {
          CliSupport.installFileServices()
        } catch (e: Exception) {
          // Already configured (and possibly locked) by a host process; use whatever is installed.
          System.err.println("warning: using pre-installed application services: ${e.message}")
        }
      }

      private fun initProviders() {
        try {
          CoreProviders.init()
        } catch (e: Exception) {
          System.err.println("warning: provider registration failed: ${e.message}")
        }
      }

      private fun providers(): List<APIProvider> = APIProvider.values()
        .filter { it != APIProvider.NULL }
        .distinctBy { it.name }
        .sortedBy { it.name.lowercase() }

      private fun configured(user: User): Map<APIProvider, ApiData> =
        ApplicationServices.fileApplicationServices().userSettingsManager
          .getUserSettings(user).apis
          .mapNotNull { data -> data.provider?.let { it to data } }
          .toMap()

      private fun resolveProvider(name: String): APIProvider =
        providers().firstOrNull { it.name.equals(name, ignoreCase = true) }
          ?: throw IllegalArgumentException(
            "unknown provider '$name'. Known providers: " + providers().joinToString(", ") { it.name })

      private fun defaultUser(email: String?): User = User(
        email = email
          ?: System.getenv("EMAIL")
          ?: System.getProperty("user.email")
          ?: "user@localhost"
      )

      private fun printUsage(out: PrintStream) {
        out.println(
          """
                API key configuration - store provider credentials for the local user.

                Usage:
                  cognotik keys [options]

                Interactive flow (default):
                  1. A numbered list of providers is printed.
                  2. Type the number of the provider you want to configure.
                  3. Paste the API key (hidden when a console is attached).
                  4. Optionally override the base URL, then repeat or press Enter to finish.

                Options:
                  --list                 Print configured providers and exit
                  --provider NAME        Non-interactive: configure this provider
                  --key VALUE            Key to store (prompts if omitted)
                  --base URL             Base URL override for the provider
                  --remove NAME          Delete the stored key for a provider
                  --verify               Contact the provider to list models after saving
                  --root DIR             Settings storage root (default: .)
                  --user EMAIL           User whose settings are edited (default: ${'$'}EMAIL)
                  -h, --help             Show this message

                Exit codes:
                  0 success, 1 error, 2 bad usage

                Examples:
                  cognotik keys
                  cognotik keys --list
                  cognotik keys --provider OpenAI --verify
                  cognotik keys --remove Anthropic
              """.trimIndent()
        )
      }
    }