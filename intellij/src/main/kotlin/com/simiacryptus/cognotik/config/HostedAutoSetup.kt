package com.simiacryptus.cognotik.config

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.ServiceKey
import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.client.UsageClient
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.util.encrypt
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.FlowLayout
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * First-run setup: when no API providers are configured, offer to use Cognotik Hosted.
 * On acceptance a device login is performed and the resulting token is stored as a
 * regular HostedProxy API entry in the user settings (no service wrapping/overriding).
 */
object HostedAutoSetup {
  private val log = LoggerFactory.getLogger(HostedAutoSetup::class.java)

  private val userSettings: UserSettings
    get() = ServiceKey.USER_SETTINGS.get().getUserSettings(CognotikConfig.localUser)

  /** True when at least one provider with a non-blank key is configured. */
  fun hasConfiguredProvider(settings: UserSettings = userSettings): Boolean = try {
    settings.apis.any { it.provider != null && !it.key?.decrypt.isNullOrBlank() }
  } catch (e: Exception) {
    log.warn("Could not inspect API configuration", e)
    true // don't nag if we can't tell
  }

  fun needsSetup(settings: UserSettings = userSettings) = !hasConfiguredProvider(settings)

  /** The configured Cognotik Hosted session token, or null when not in hosted mode. */
  fun hostedToken(settings: UserSettings? = null): String? = try {
    (settings ?: userSettings).apis
      .firstOrNull { it.provider?.name == CoreProviders.HostedProxy.name }
      ?.key?.decrypt?.takeIf { it.isNotBlank() }
  } catch (e: Exception) {
    log.debug("Could not read hosted token", e)
    null
  }

  /** True when a HostedProxy provider with a non-blank key is configured. */
  fun isHosted(settings: UserSettings? = null): Boolean = hostedToken(settings) != null
  private val hostedUsageEnabled = AtomicBoolean(false)

  /** Routes usage/balance queries to the hosted backend (mirrors the CLI's hosted setup). */
  fun enableHostedUsage() {
    if (!hostedUsageEnabled.compareAndSet(false, true)) return
    ServiceKey.USAGE_DB.factory = { UsageClient() }
    log.info("Usage tracking routed to Cognotik Hosted")
  }

  /**
   * Remaining hosted budget for the local user. Performs a network call; never call on the EDT.
   * @throws IllegalStateException when not in hosted mode.
   */
  fun fetchBalance(): Double {
    check(isHosted()) { "Cognotik Hosted is not configured" }
    enableHostedUsage()
    return ServiceRouter.getUserBalance(CognotikConfig.localUser)
  }


  /** Popup asking the user whether to use Cognotik Hosted. */
  fun createSetupPopup(project: Project?, onConfigured: () -> Unit = {}): JBPopup {
    val panel = JPanel(BorderLayout(10, 10)).apply {
      border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
    }
    panel.add(
      JLabel(
        """
                <html><b>Welcome to Cognotik</b><br/><br/>
                No AI providers are configured yet.<br/>
                Would you like to use <b>Cognotik Hosted</b>? You will be asked to sign in<br/>
                in your browser and the hosted provider will be configured automatically.<br/><br/>
                Alternatively you can enter your own API keys in the settings.</html>
                """.trimIndent()
      ), BorderLayout.CENTER
    )
    val hostedButton = JButton("Use Cognotik Hosted")
    val manualButton = JButton("Configure Manually…")
    val laterButton = JButton("Not Now")
    val buttons = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
      add(hostedButton)
      add(manualButton)
      add(laterButton)
    }
    panel.add(buttons, BorderLayout.SOUTH)

    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, hostedButton)
      .setTitle("Cognotik Setup")
      .setRequestFocus(true)
      .setMovable(true)
      .setCancelOnClickOutside(true)
      .createPopup()

    hostedButton.addActionListener {
      popup.cancel()
      startHostedSetup(project, onConfigured)
    }
    manualButton.addActionListener {
      popup.cancel()
      openSettings(project, onConfigured)
    }
    laterButton.addActionListener { popup.cancel() }
    return popup
  }

  private fun openSettings(project: Project?, onConfigured: () -> Unit) {
    try {
      ShowSettingsUtil.getInstance().editConfigurable(project, StaticAppSettingsConfigurable())
      onConfigured()
    } catch (e: Exception) {
      log.warn("Could not open settings", e)
    }
  }

  /** Asks for confirmation (outside a popup context) and runs the hosted login. */
  fun promptAndSetup(project: Project?, onConfigured: () -> Unit = {}) {
    val result = Messages.showYesNoDialog(
      project,
      "No AI providers are configured. Would you like to sign in to Cognotik Hosted?",
      "Cognotik Setup",
      "Use Cognotik Hosted",
      "Not Now",
      Messages.getQuestionIcon()
    )
    if (result == Messages.YES) startHostedSetup(project, onConfigured)
  }

  /**
   * Runs the device login as a cancellable background task (non-modal, so the IDE stays
   * responsive while the user approves the login in the browser), then configures HostedProxy.
   */
  fun startHostedSetup(project: Project?, onConfigured: () -> Unit = {}) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Signing in to Cognotik Hosted", true) {
      private var result: HostedLogin.LoginResult? = null

      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        indicator.text = "Starting login…"
        result = HostedLogin.login(object : HostedLogin.Listener {
          override fun onVerification(verificationUrl: String, displayCode: String?) {
            indicator.text = if (displayCode != null)
              "Approve the login in your browser (confirmation code: $displayCode)"
            else
              "Approve the login in your browser"
            indicator.text2 = verificationUrl
            // Open the browser off the EDT; this does not depend on modality state
            ApplicationManager.getApplication().executeOnPooledThread {
              try {
                Desktop.getDesktop().browse(URI(verificationUrl))
              } catch (e: Exception) {
                log.warn("Could not open browser for $verificationUrl", e)
              }
            }
            notifyVerification(project, verificationUrl, displayCode)
          }

          override fun onStatus(message: String) {
            indicator.text2 = message
          }

          override fun checkCanceled() {
            indicator.checkCanceled()
          }
        })
      }

      override fun onSuccess() {
        val login = result ?: return
        try {
          configureHostedProxy(login.token)
          onConfigured()
          Messages.showInfoMessage(
            project,
            "Signed in to Cognotik Hosted" +
                (if (login.userId.isNotBlank()) " as ${login.userId}" else "") +
                ".\nClick the Cognotik status bar widget to choose your models.",
            "Cognotik Setup"
          )
        } catch (e: Exception) {
          log.error("Failed to store hosted configuration", e)
          Messages.showErrorDialog(project, "Failed to save configuration: ${e.message}", "Cognotik Setup")
        }
      }

      override fun onCancel() {
        log.info("Hosted login cancelled by user")
      }

      override fun onThrowable(error: Throwable) {
        if (error is ProcessCanceledException) return
        log.warn("Hosted login failed", error)
        Messages.showErrorDialog(project, "Login failed: ${error.message}", "Cognotik Setup")
      }
    })
  }

  /** Shows a non-modal balloon with the verification URL/code (background progress is less visible). */
  private fun notifyVerification(project: Project?, verificationUrl: String, displayCode: String?) {
    try {
      val content = buildString {
        append("Approve the login in your browser")
        if (displayCode != null) append(" (confirmation code: <b>$displayCode</b>)")
        append(".<br/>If the browser did not open, visit:<br/>$verificationUrl")
      }
      ApplicationManager.getApplication().invokeLater({
        try {
          NotificationGroupManager.getInstance()
            .getNotificationGroup("Cognotik")
            ?.createNotification("Cognotik Hosted sign-in", content, NotificationType.INFORMATION)
            ?.notify(project)
        } catch (e: Exception) {
          log.debug("Could not show verification notification", e)
        }
      }, com.intellij.openapi.application.ModalityState.any())
    } catch (e: Exception) {
      log.debug("Could not schedule verification notification", e)
    }
  }


  /** Adds (or replaces) the HostedProxy API entry in the persisted user settings. */
  fun configureHostedProxy(token: String) {
    val service = ServiceKey.USER_SETTINGS.get()
    val settings = service.getUserSettings(CognotikConfig.localUser)
    val hosted = CoreProviders.HostedProxy
    settings.apis.removeAll { it.provider?.name == hosted.name }
    settings.apis.add(
      ApiData(
        name = hosted.name,
        key = token.encrypt,
        baseUrl = hosted.base,
        provider = hosted,
      )
    )
    service.updateUserSettings(CognotikConfig.localUser, settings)
    log.info("Configured HostedProxy API provider")
    enableHostedUsage()
    AppSettingsState.notifySettingsLoaded()
  }
}