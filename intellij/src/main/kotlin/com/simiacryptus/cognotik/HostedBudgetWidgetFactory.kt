package com.simiacryptus.cognotik
import com.intellij.ide.BrowserUtil

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.HostedAutoSetup
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Status bar widget showing the remaining Cognotik Hosted budget.
 * Shows nothing unless a HostedProxy provider is configured.
 */
class HostedBudgetWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = FACTORY_ID
    override fun getDisplayName(): String = "Cognotik Hosted Budget"
    override fun isAvailable(project: Project): Boolean = true
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = HostedBudgetWidget(project)

    class HostedBudgetWidget(
        @Suppress("unused") private val project: Project? = null
    ) : StatusBarWidget, StatusBarWidget.TextPresentation {

        private var statusBar: StatusBar? = null

        @Volatile
        private var balance: Double? = null

        @Volatile
        private var error: String? = null

        @Volatile
        private var lastUpdated: Long = 0L

        private val loading = AtomicBoolean(false)

        private val timer = Timer(REFRESH_INTERVAL_MS) { refresh() }.apply { isRepeats = true }

        private val settingsListener: () -> Unit = { SwingUtilities.invokeLater { refresh() } }

        override fun ID(): String = WIDGET_ID

        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

        override fun install(statusBar: StatusBar) {
            this.statusBar = statusBar
            AppSettingsState.onSettingsLoadedListeners.add(settingsListener)
            timer.start()
            refresh()
        }

        override fun dispose() {
            timer.stop()
            AppSettingsState.onSettingsLoadedListeners.remove(settingsListener)
            statusBar = null
        }

        private fun updateWidget() {
            SwingUtilities.invokeLater { statusBar?.updateWidget(ID()) }
        }

        /** Fetches the balance in the background (no-op when not hosted or already loading). */
        fun refresh() {
            if (!HostedAutoSetup.isHosted()) {
                balance = null
                error = null
                updateWidget()
                return
            }
            if (!loading.compareAndSet(false, true)) return
            updateWidget()
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    balance = HostedAutoSetup.fetchBalance()
                    error = null
                    lastUpdated = System.currentTimeMillis()
                } catch (e: Exception) {
                    log.warn("Could not fetch hosted balance", e)
                    error = e.message ?: e.javaClass.simpleName
                } finally {
                    loading.set(false)
                    updateWidget()
                }
            }
        }

        override fun getText(): String {
            if (!HostedAutoSetup.isHosted()) return ""
            val b = balance
            return when {
                b != null -> "💰 " + String.format(Locale.US, "$%.2f", b)
                error != null -> "💰 ?"
                else -> "💰 …"
            }
        }

        override fun getTooltipText(): String {
            if (!HostedAutoSetup.isHosted()) return "Cognotik Hosted is not configured"
            val b = balance
            val updated = if (lastUpdated > 0)
                "<br/>Updated: ${SimpleDateFormat("HH:mm:ss").format(Date(lastUpdated))}" else ""
            val err = error?.let { "<br/>Last error: $it" } ?: ""
            val value = if (b != null) String.format(Locale.US, "$%.2f", b) else "unknown"
             return "<html>Cognotik Hosted remaining budget: <b>$value</b>$updated$err<br/>Click for options</html>"
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

         override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { e -> showMenu(e) }

         private fun showMenu(e: MouseEvent) {
             val step = object : BaseListPopupStep<MenuItem>("Cognotik Hosted", MenuItem.values().toList()) {
                 override fun getTextFor(value: MenuItem): String = value.label
                 override fun onChosen(selectedValue: MenuItem, finalChoice: Boolean): PopupStep<*>? {
                     return doFinalStep {
                         val url = selectedValue.url
                         if (url == null) refresh() else BrowserUtil.browse(url)
                     }
                 }
             }
             val popup = JBPopupFactory.getInstance().createListPopup(step)
             val component = e.component
             if (component != null) {
                 val height = popup.content.preferredSize.height
                 popup.show(RelativePoint(component, Point(0, -height)))
             } else {
                 popup.show(RelativePoint(e))
             }
         }

         private enum class MenuItem(val label: String, val url: String?) {
             REFRESH("Refresh", null),
             USAGE("Usage", "https://hosted.cognotik.com/usage/"),
             CREDITS("Credits", "https://hosted.cognotik.com/credits/"),
             ACTIVITY("Activity", "https://hosted.cognotik.com/usageGraphQL.html"),
         }
    }

    companion object {
        private val log = LoggerFactory.getLogger(HostedBudgetWidgetFactory::class.java)
        const val FACTORY_ID = "Cognotik.HostedBudgetWidgetFactory"
        const val WIDGET_ID = "Cognotik.HostedBudgetWidget"
        private const val REFRESH_INTERVAL_MS = 5 * 60 * 1000
    }
}